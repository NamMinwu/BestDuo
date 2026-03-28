package com.bestduo.batch.phase0;

import com.bestduo.client.RiotApiClient;
import com.bestduo.domain.entity.MatchRaw;
import com.bestduo.domain.entity.SummonerPool;
import com.bestduo.domain.repository.MatchRawRepository;
import com.bestduo.domain.repository.SummonerPoolRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Phase 0 데이터 수집 서비스
 *
 * 수집 범위: KR Challenger / GrandMaster / Master
 * (Phase 0 목표: 데이터 밀도 검증용 최소 수집)
 *
 * 흐름:
 *   1. 래더 API → PUUID 수집
 *   2. 각 PUUID → 최근 20 솔로랭크 매치 ID
 *   3. 중복 제거 후 매치 상세 수집 → match_raw 저장
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class Phase0CollectionService {

    private final RiotApiClient riotApiClient;
    private final MatchRawRepository matchRawRepository;
    private final SummonerPoolRepository summonerPoolRepository;
    private final ObjectMapper objectMapper;

    /**
     * Challenger / GM / Master 래더에서 PUUID 수집
     *
     * @return 중복 제거된 PUUID 목록
     */
    public List<String> collectTopLadderPuuids() {
        Set<String> puuids = new LinkedHashSet<>();

        log.info("챌린저 래더 수집 시작...");
        puuids.addAll(extractPuuids(riotApiClient.fetchChallengerLadder(), "Challenger"));

        log.info("그랜드마스터 래더 수집 시작...");
        puuids.addAll(extractPuuids(riotApiClient.fetchGrandmasterLadder(), "GrandMaster"));

        log.info("마스터 래더 수집 시작...");
        puuids.addAll(extractPuuids(riotApiClient.fetchMasterLadder(), "Master"));

        log.info("총 소환사 수집 완료: {}명", puuids.size());
        saveSummonersToPool(puuids, "MASTER");
        return new ArrayList<>(puuids);
    }

    /**
     * 소환사 목록으로부터 매치 수집
     *
     * @param puuids 대상 PUUID 목록
     * @param targetMatchCount 목표 매치 수
     * @return 실제 저장된 신규 매치 수
     */
    public int collectMatches(List<String> puuids, int targetMatchCount) {
        Set<String> allMatchIds = new LinkedHashSet<>();

        for (String puuid : puuids) {
            if (allMatchIds.size() >= targetMatchCount * 2) break; // 여유있게 수집

            try {
                String matchIdsJson = riotApiClient.fetchMatchIds(puuid, 20);
                List<String> ids = objectMapper.readValue(matchIdsJson,
                        objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
                allMatchIds.addAll(ids);
            } catch (Exception e) {
                log.warn("PUUID {} 매치 ID 수집 실패: {}", puuid, e.getMessage());
            }
        }

        log.info("중복 제거 후 매치 ID 수: {}", allMatchIds.size());

        int saved = 0;
        int skipped = 0;
        for (String matchId : allMatchIds) {
            if (saved >= targetMatchCount) break;

            // 이미 수집된 매치는 스킵 (idempotent)
            if (matchRawRepository.existsByMatchId(matchId)) {
                skipped++;
                continue;
            }

            try {
                String detail = riotApiClient.fetchMatchDetail(matchId);
                if (detail == null) continue;

                String patch = extractPatchFromMatchDetail(detail);
                saveMatchRaw(matchId, patch, detail);
                saved++;

                if (saved % 100 == 0) {
                    log.info("매치 수집 진행: {}/{}", saved, targetMatchCount);
                }
            } catch (Exception e) {
                log.warn("매치 {} 수집 실패: {}", matchId, e.getMessage());
            }
        }

        log.info("매치 수집 완료: 신규 {} 건, 중복 스킵 {} 건", saved, skipped);
        return saved;
    }

    private List<String> extractPuuids(String ladderJson, String tierName) {
        List<String> puuids = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(ladderJson);
            JsonNode entries = root.path("entries");
            for (JsonNode entry : entries) {
                String puuid = entry.path("puuid").asText("");
                if (!puuid.isEmpty()) puuids.add(puuid);
            }
            log.info("{} PUUID 수집: {}명", tierName, puuids.size());
        } catch (Exception e) {
            log.error("{} 래더 파싱 실패: {}", tierName, e.getMessage());
        }
        return puuids;
    }

    @Transactional
    private void saveSummonersToPool(Set<String> puuids, String tier) {
        for (String puuid : puuids) {
            if (!summonerPoolRepository.existsById(puuid)) {
                summonerPoolRepository.save(SummonerPool.builder()
                        .puuid(puuid)
                        .tier(tier)
                        .verified(true)
                        .lastSeenAt(LocalDateTime.now())
                        .build());
            }
        }
    }

    @Transactional
    private void saveMatchRaw(String matchId, String patch, String rawJson) {
        matchRawRepository.save(MatchRaw.builder()
                .matchId(matchId)
                .patch(patch)
                .rawJson(rawJson)
                .processed(false)
                .collectedAt(LocalDateTime.now())
                .build());
    }

    private String extractPatchFromMatchDetail(String matchJson) {
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
