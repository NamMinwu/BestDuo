package com.bestduo.batch.phase0;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Phase 0 실행 진입점
 *
 * 실행 방법:
 *   ./gradlew bootRun --args='--spring.profiles.active=phase0'
 *
 * 또는 환경변수:
 *   SPRING_PROFILES_ACTIVE=phase0 ./gradlew bootRun
 *
 * 필수 환경변수:
 *   RIOT_API_KEYS=key1,key2,key3
 */
@Slf4j
@Component
@Profile("phase0")
@RequiredArgsConstructor
public class Phase0Runner implements CommandLineRunner {

    private final Phase0CollectionService collectionService;
    private final Phase0ReportService reportService;

    @Value("${phase0.target-matches:5000}")
    private int targetMatches;

    @Value("${phase0.report-only:false}")
    private boolean reportOnly;

    @Value("${phase0.patch:}")
    private String filterPatch;

    @Override
    public void run(String... args) {
        log.info("=== Phase 0 데이터 밀도 검증 시작 ===");
        log.info("목표 매치 수: {}", targetMatches);

        if (!reportOnly) {
            // 1단계: 소환사 수집
            List<String> puuids = collectionService.collectTopLadderPuuids();
            log.info("수집된 소환사: {}명", puuids.size());

            // 2단계: 매치 수집
            int saved = collectionService.collectMatches(puuids, targetMatches);
            log.info("수집 완료: {}건", saved);
        } else {
            log.info("리포트 전용 모드 (기존 수집 데이터 분석)");
        }

        // 3단계: 분석 리포트 출력
        reportService.generateReport(filterPatch.isEmpty() ? null : filterPatch);

        log.info("=== Phase 0 완료 ===");
    }
}
