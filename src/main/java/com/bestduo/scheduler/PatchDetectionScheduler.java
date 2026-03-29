package com.bestduo.scheduler;

import com.bestduo.domain.entity.PatchMeta;
import com.bestduo.domain.repository.PatchMetaRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 패치 감지 스케줄러
 *
 * 15분마다 Data Dragon 버전 API를 폴링해서 신규 패치를 감지.
 * 신규 패치 감지 시:
 *   1. patch_meta에 신규 패치 등록
 *   2. BatchPipelineService.runPipeline() 비동기 트리거
 *      (MatchCollector → TierVerification → DuoPairAggregator → RankingComputer)
 *   3. 최신 2개 패치만 is_active=TRUE 유지 (나머지 FALSE)
 *
 * 주의:
 *   패치 배포 직후 KR 게임 데이터가 없을 수 있음
 *   → MatchCollectorJob이 0건 수집해도 에러 아님 (다음 주기에 재수집)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PatchDetectionScheduler {

    private final PatchMetaRepository patchMetaRepository;
    private final ObjectMapper objectMapper;
    private final BatchPipelineService batchPipelineService;

    @Value("${scheduler.patch-detection.ddragon-url:https://ddragon.leagueoflegends.com/api/versions.json}")
    private String ddragonVersionsUrl;

    @Value("${scheduler.patch-detection.active-patch-count:2}")
    private int activePatchCount;

    private final WebClient webClient = WebClient.builder().build();

    /**
     * 15분마다 Data Dragon 폴링
     * cron: application.yml의 scheduler.patch-detection.cron
     */
    @Scheduled(cron = "${scheduler.patch-detection.cron}")
    public void detectNewPatch() {
        log.debug("패치 감지 폴링 시작");

        try {
            String latestPatch = fetchLatestPatchFromDdragon();
            if (latestPatch == null) {
                log.warn("Data Dragon 응답에서 패치 버전을 파싱할 수 없음");
                return;
            }

            if (patchMetaRepository.existsByPatch(latestPatch)) {
                log.debug("패치 {} 이미 등록됨 → 스킵", latestPatch);
                return;
            }

            log.info("신규 패치 감지: {}", latestPatch);
            registerNewPatch(latestPatch);
            deactivateOldPatches();
            batchPipelineService.runPipeline(latestPatch);

        } catch (Exception e) {
            log.error("패치 감지 중 오류: {}", e.getMessage());
        }
    }

    /**
     * Data Dragon에서 최신 패치 버전 조회
     * versions.json = ["15.7.1", "15.6.1", ...] 형태
     * "15.7.1" → "15.7" 변환
     */
    String fetchLatestPatchFromDdragon() {
        try {
            String json = webClient.get()
                    .uri(ddragonVersionsUrl)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (json == null) return null;

            JsonNode versions = objectMapper.readTree(json);
            if (!versions.isArray() || versions.isEmpty()) return null;

            String fullVersion = versions.get(0).asText();
            return normalizePatch(fullVersion);

        } catch (Exception e) {
            log.error("Data Dragon 조회 실패: {}", e.getMessage());
            return null;
        }
    }

    /**
     * "15.7.1" → "15.7"
     */
    String normalizePatch(String version) {
        if (version == null || version.isEmpty()) return null;
        String[] parts = version.split("\\.");
        if (parts.length < 2) return version;
        return parts[0] + "." + parts[1];
    }

    @Transactional
    public void registerNewPatch(String patch) {
        patchMetaRepository.save(PatchMeta.builder()
                .patch(patch)
                .releasedAt(LocalDateTime.now())
                .active(true)
                .build());
        log.info("패치 {} 등록 완료", patch);
    }

    /**
     * 최신 activePatchCount(기본 2)개만 is_active=TRUE 유지
     * 나머지 패치는 is_active=FALSE
     */
    @Transactional
    public void deactivateOldPatches() {
        List<PatchMeta> activePatches = patchMetaRepository.findByActiveTrueOrderByPatchDesc();

        if (activePatches.size() <= activePatchCount) return;

        // 오래된 패치 비활성화
        activePatches.stream()
                .skip(activePatchCount)
                .forEach(p -> {
                    p.deactivate();
                    patchMetaRepository.save(p);
                    log.info("패치 {} 비활성화 (최신 {}개 초과)", p.getPatch(), activePatchCount);
                });
    }
}
