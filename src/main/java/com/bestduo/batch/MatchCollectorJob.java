package com.bestduo.batch;

import com.bestduo.client.RiotApiClient;
import com.bestduo.domain.entity.MatchRaw;
import com.bestduo.domain.entity.SummonerPool;
import com.bestduo.domain.repository.MatchRawRepository;
import com.bestduo.domain.repository.SummonerPoolRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 매치 수집 배치 잡
 *
 * 흐름:
 *   1. collectSummonersStep  — Challenger/GM/Master 래더 + Emerald/Diamond 페이지네이션 → summoner_pool
 *   2. collectMatchesStep    — summoner_pool(verified=TRUE) → 매치 ID 수집 → match_raw 저장
 *   3. discoverNewSummonersStep — 수집된 매치 참가자 PUUID → summoner_pool(verified=FALSE) 추가
 *
 * 주의:
 *   - BFS로 발견된 소환사 티어 확인은 TierVerificationJob이 별도 처리
 *   - match_id UNIQUE 제약으로 중복 저장 방지 (idempotent)
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class MatchCollectorJob {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final RiotApiClient riotApiClient;
    private final MatchRawRepository matchRawRepository;
    private final SummonerPoolRepository summonerPoolRepository;
    private final ObjectMapper objectMapper;

    @Value("${collector.matches-per-puuid:20}")
    private int matchesPerPuuid;

    @Value("${collector.target-matches:10000}")
    private int targetMatches;

    @Value("${collector.emerald-diamond-pages:5}")
    private int emeraldDiamondPages;

    // ─── Job ──────────────────────────────────────────────────────────────────

    @Bean
    public Job matchCollectorBatchJob() {
        return new JobBuilder("matchCollectorJob", jobRepository)
                .start(collectSummonersStep())
                .next(collectMatchesStep())
                .next(discoverNewSummonersStep())
                .build();
    }

    // ─── Step 1: 소환사 수집 ────────────────────────────────────────────────

    @Bean
    public Step collectSummonersStep() {
        return new StepBuilder("collectSummonersStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    log.info("[Step 1] 소환사 수집 시작");

                    Set<String> puuids = new LinkedHashSet<>();

                    // 최상위 래더 (직접 API)
                    puuids.addAll(extractPuuidsFromLadder(riotApiClient.fetchChallengerLadder(), "Challenger"));
                    puuids.addAll(extractPuuidsFromLadder(riotApiClient.fetchGrandmasterLadder(), "GrandMaster"));
                    puuids.addAll(extractPuuidsFromLadder(riotApiClient.fetchMasterLadder(), "Master"));

                    // Emerald / Diamond — 페이지네이션
                    for (String tier : List.of("EMERALD", "DIAMOND")) {
                        for (String division : List.of("I", "II", "III", "IV")) {
                            puuids.addAll(collectLeagueEntries(tier, division));
                        }
                    }

                    log.info("[Step 1] 전체 PUUID 수집: {}명", puuids.size());
                    saveSummonersVerified(puuids);

                    chunkContext.getStepContext().getStepExecution()
                            .getJobExecution().getExecutionContext()
                            .putInt("collectedSummonerCount", puuids.size());

                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    // ─── Step 2: 매치 수집 ──────────────────────────────────────────────────

    @Bean
    public Step collectMatchesStep() {
        return new StepBuilder("collectMatchesStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    log.info("[Step 2] 매치 수집 시작 (목표: {}건)", targetMatches);

                    List<SummonerPool> summoners = summonerPoolRepository.findByVerifiedTrue();
                    log.info("[Step 2] 대상 소환사: {}명", summoners.size());

                    Set<String> allMatchIds = new LinkedHashSet<>();
                    for (SummonerPool summoner : summoners) {
                        if (allMatchIds.size() >= targetMatches * 2) break;
                        try {
                            String json = riotApiClient.fetchMatchIds(summoner.getPuuid(), matchesPerPuuid);
                            List<String> ids = objectMapper.readValue(json,
                                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
                            allMatchIds.addAll(ids);
                        } catch (Exception e) {
                            log.warn("[Step 2] PUUID {} 매치 ID 수집 실패: {}", summoner.getPuuid(), e.getMessage());
                        }
                    }

                    log.info("[Step 2] 중복 제거 후 매치 ID: {}건", allMatchIds.size());

                    int saved = 0;
                    int skipped = 0;
                    for (String matchId : allMatchIds) {
                        if (saved >= targetMatches) break;

                        if (matchRawRepository.existsByMatchId(matchId)) {
                            skipped++;
                            continue;
                        }

                        try {
                            String detail = riotApiClient.fetchMatchDetail(matchId);
                            if (detail == null) continue;

                            String patch = extractPatch(detail);
                            saveMatchRaw(matchId, patch, detail);
                            saved++;

                            if (saved % 500 == 0) {
                                log.info("[Step 2] 진행: {}/{}", saved, targetMatches);
                            }
                        } catch (Exception e) {
                            log.warn("[Step 2] 매치 {} 수집 실패: {}", matchId, e.getMessage());
                        }
                    }

                    log.info("[Step 2] 완료: 신규 {}건, 중복 스킵 {}건", saved, skipped);

                    chunkContext.getStepContext().getStepExecution()
                            .getJobExecution().getExecutionContext()
                            .putInt("savedMatchCount", saved);

                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    // ─── Step 3: BFS 소환사 발견 ────────────────────────────────────────────

    @Bean
    public Step discoverNewSummonersStep() {
        return new StepBuilder("discoverNewSummonersStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    log.info("[Step 3] 매치 참가자 PUUID → summoner_pool(미확인) 추가 시작");

                    // processed=FALSE인 매치에서 참가자 PUUID 추출
                    List<MatchRaw> unprocessed = matchRawRepository.findByProcessedFalse().stream()
                            .filter(m -> m.getRawJson() != null)
                            .toList();

                    Set<String> discoveredPuuids = new LinkedHashSet<>();
                    for (MatchRaw match : unprocessed) {
                        try {
                            JsonNode root = objectMapper.readTree(match.getRawJson());
                            JsonNode participants = root.path("info").path("participants");
                            for (JsonNode p : participants) {
                                String puuid = p.path("puuid").asText("");
                                if (!puuid.isEmpty()) discoveredPuuids.add(puuid);
                            }
                        } catch (Exception e) {
                            log.warn("[Step 3] 매치 {} 참가자 파싱 실패: {}", match.getMatchId(), e.getMessage());
                        }
                    }

                    int newCount = saveNewUnverifiedSummoners(discoveredPuuids);
                    log.info("[Step 3] 신규 발견 소환사: {}명 (TierVerificationJob 대상)", newCount);

                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    // ─── 내부 헬퍼 ──────────────────────────────────────────────────────────

    private List<String> extractPuuidsFromLadder(String ladderJson, String tierName) {
        List<String> puuids = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(ladderJson);
            for (JsonNode entry : root.path("entries")) {
                String puuid = entry.path("puuid").asText("");
                if (!puuid.isEmpty()) puuids.add(puuid);
            }
            log.info("{} PUUID: {}명", tierName, puuids.size());
        } catch (Exception e) {
            log.error("{} 래더 파싱 실패: {}", tierName, e.getMessage());
        }
        return puuids;
    }

    private List<String> collectLeagueEntries(String tier, String division) {
        List<String> puuids = new ArrayList<>();
        for (int page = 1; page <= emeraldDiamondPages; page++) {
            try {
                String json = riotApiClient.fetchLeagueEntries(tier, division, page);
                JsonNode entries = objectMapper.readTree(json);
                if (!entries.isArray() || entries.isEmpty()) break;

                for (JsonNode entry : entries) {
                    String puuid = entry.path("puuid").asText("");
                    if (!puuid.isEmpty()) puuids.add(puuid);
                }
            } catch (Exception e) {
                log.warn("{} {} 페이지 {} 수집 실패: {}", tier, division, page, e.getMessage());
                break;
            }
        }
        log.info("{} {} PUUID: {}명", tier, division, puuids.size());
        return puuids;
    }

    @Transactional
    void saveSummonersVerified(Set<String> puuids) {
        for (String puuid : puuids) {
            if (!summonerPoolRepository.existsById(puuid)) {
                summonerPoolRepository.save(SummonerPool.builder()
                        .puuid(puuid)
                        .tier("UNKNOWN")
                        .verified(true)   // 래더 API에서 직접 수집 = 에메랄드+ 보장
                        .lastSeenAt(LocalDateTime.now())
                        .build());
            } else {
                summonerPoolRepository.findById(puuid).ifPresent(s -> s.updateLastSeen());
            }
        }
    }

    @Transactional
    int saveNewUnverifiedSummoners(Set<String> puuids) {
        int count = 0;
        for (String puuid : puuids) {
            if (!summonerPoolRepository.existsById(puuid)) {
                summonerPoolRepository.save(SummonerPool.builder()
                        .puuid(puuid)
                        .tier(null)
                        .verified(false)   // TierVerificationJob이 확인 예정
                        .lastSeenAt(LocalDateTime.now())
                        .build());
                count++;
            }
        }
        return count;
    }

    @Transactional
    void saveMatchRaw(String matchId, String patch, String rawJson) {
        matchRawRepository.save(MatchRaw.builder()
                .matchId(matchId)
                .patch(patch)
                .rawJson(rawJson)
                .processed(false)
                .collectedAt(LocalDateTime.now())
                .build());
    }

    private String extractPatch(String matchJson) {
        try {
            JsonNode root = objectMapper.readTree(matchJson);
            String gameVersion = root.path("info").path("gameVersion").asText("unknown");
            String[] parts = gameVersion.split("\\.");
            return parts.length >= 2 ? parts[0] + "." + parts[1] : "unknown";
        } catch (Exception e) {
            return "unknown";
        }
    }
}
