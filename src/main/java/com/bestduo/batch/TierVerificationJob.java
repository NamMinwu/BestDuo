package com.bestduo.batch;

import com.bestduo.client.RiotApiClient;
import com.bestduo.domain.entity.SummonerPool;
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
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.support.ListItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;

/**
 * BFS로 발견된 소환사의 티어 검증 배치 잡
 *
 * 대상: summoner_pool WHERE is_verified = FALSE
 * 처리:
 *   - LEAGUE-V4 /entries/by-summoner/{id} 조회
 *   - 솔로랭크 에메랄드+ → is_verified=TRUE, tier 업데이트
 *   - 에메랄드 미만 또는 미배치 → is_verified=TRUE, tier='UNRANKED' (수집 제외)
 *
 * 주의:
 *   - summoner_pool.puuid로 summonerId를 알 수 없음
 *   → PUUID 기반 소환사 정보 API 먼저 조회 필요
 *   - 배치 크기(chunk=50)로 DB flush 주기 조절
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class TierVerificationJob {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final RiotApiClient riotApiClient;
    private final SummonerPoolRepository summonerPoolRepository;
    private final ObjectMapper objectMapper;

    @Value("${collector.tier-verification-chunk:50}")
    private int chunkSize;

    // 에메랄드 이상으로 인정할 티어 목록
    private static final List<String> ELIGIBLE_TIERS =
            List.of("EMERALD", "DIAMOND", "MASTER", "GRANDMASTER", "CHALLENGER");

    // ─── Job ──────────────────────────────────────────────────────────────────

    @Bean
    public Job tierVerificationJob() {
        return new JobBuilder("tierVerificationJob", jobRepository)
                .start(verifyTiersStep())
                .build();
    }

    // ─── Step ─────────────────────────────────────────────────────────────────

    @Bean
    public Step verifyTiersStep() {
        List<SummonerPool> unverified = summonerPoolRepository.findByVerifiedFalse();
        log.info("[TierVerificationJob] 미확인 소환사: {}명", unverified.size());

        return new StepBuilder("verifyTiersStep", jobRepository)
                .<SummonerPool, SummonerPool>chunk(chunkSize, transactionManager)
                .reader(new ListItemReader<>(unverified))
                .processor(tierVerificationProcessor())
                .writer(tierVerificationWriter())
                .build();
    }

    // ─── Processor ────────────────────────────────────────────────────────────

    @Bean
    public ItemProcessor<SummonerPool, SummonerPool> tierVerificationProcessor() {
        return summoner -> {
            try {
                // 1단계: PUUID → summoner 정보 조회 (summonerId 필요)
                String summonerJson = riotApiClient.fetchSummonerByPuuid(summoner.getPuuid());
                if (summonerJson == null) {
                    log.warn("[TierVerify] PUUID {} 소환사 정보 없음 → UNRANKED 처리", summoner.getPuuid());
                    summoner.updateTier("UNRANKED");
                    return summoner;
                }

                JsonNode summonerNode = objectMapper.readTree(summonerJson);
                String summonerId = summonerNode.path("id").asText("");

                if (summonerId.isEmpty()) {
                    summoner.updateTier("UNRANKED");
                    return summoner;
                }

                // 2단계: summonerId → 리그 엔트리 조회
                String leagueJson = riotApiClient.fetchSummonerLeagueEntry(summonerId);
                String tier = extractSoloRankTier(leagueJson);

                summoner.updateTier(tier);
                log.debug("[TierVerify] PUUID {} → tier={}", summoner.getPuuid(), tier);
                return summoner;

            } catch (Exception e) {
                log.warn("[TierVerify] PUUID {} 처리 실패: {} → UNRANKED", summoner.getPuuid(), e.getMessage());
                summoner.updateTier("UNRANKED");
                return summoner;
            }
        };
    }

    // ─── Writer ───────────────────────────────────────────────────────────────

    @Bean
    public ItemWriter<SummonerPool> tierVerificationWriter() {
        return items -> {
            summonerPoolRepository.saveAll(items.getItems());
            long eligible = items.getItems().stream()
                    .filter(s -> ELIGIBLE_TIERS.contains(s.getTier()))
                    .count();
            log.debug("[TierVerify] 배치 저장: {}명 (에메랄드+: {}명)", items.size(), eligible);
        };
    }

    // ─── 헬퍼 ────────────────────────────────────────────────────────────────

    /**
     * 리그 엔트리 JSON에서 솔로랭크 티어 추출
     * 솔로랭크가 없으면 "UNRANKED" 반환
     */
    private String extractSoloRankTier(String leagueJson) {
        if (leagueJson == null) return "UNRANKED";
        try {
            JsonNode entries = objectMapper.readTree(leagueJson);
            for (JsonNode entry : entries) {
                if ("RANKED_SOLO_5x5".equals(entry.path("queueType").asText())) {
                    return entry.path("tier").asText("UNRANKED");
                }
            }
        } catch (Exception e) {
            log.warn("[TierVerify] 리그 엔트리 파싱 실패: {}", e.getMessage());
        }
        return "UNRANKED";
    }
}
