package com.bestduo.batch.phase0;

import com.bestduo.calculator.WilsonScoreCalculator;
import com.bestduo.domain.entity.MatchRaw;
import com.bestduo.domain.model.BottomPair;
import com.bestduo.domain.repository.MatchRawRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Phase 0 분석 리포트 서비스
 *
 * 수집된 match_raw 데이터에서:
 *   1. 바텀 페어 추출
 *   2. 페어별 게임 수 분포 통계
 *   3. Wilson score 하한 계산
 *   4. min_sample_threshold 추천값 도출
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class Phase0ReportService {

    private final MatchRawRepository matchRawRepository;
    private final DuoPairExtractor duoPairExtractor;
    private final WilsonScoreCalculator wilsonCalculator;

    /**
     * Phase 0 전체 분석 리포트 생성 및 출력
     */
    public void generateReport(String patch) {
        List<MatchRaw> matches = matchRawRepository.findAll();
        if (patch != null && !patch.isEmpty()) {
            matches = matches.stream()
                    .filter(m -> m.getPatch().equals(patch))
                    .collect(Collectors.toList());
        }

        log.info("\n========================================");
        log.info("Phase 0 데이터 밀도 분석 리포트");
        log.info("분석 대상 매치: {}건", matches.size());
        log.info("========================================");

        // 전체 바텀 페어 추출
        Map<String, PairStats> pairStatsMap = new HashMap<>();

        for (MatchRaw match : matches) {
            if (match.getRawJson() == null) continue;

            List<BottomPair> pairs = duoPairExtractor.extract(match.getRawJson());
            for (BottomPair pair : pairs) {
                String key = pair.getAdcChampionId() + "_" + pair.getSupportChampionId();
                pairStatsMap.computeIfAbsent(key, k -> new PairStats(
                        pair.getAdcChampionId(), pair.getSupportChampionId()
                )).add(pair.isWin());
            }
        }

        if (pairStatsMap.isEmpty()) {
            log.warn("추출된 바텀 페어 없음. teamPosition 데이터 확인 필요.");
            return;
        }

        List<PairStats> allPairs = new ArrayList<>(pairStatsMap.values());
        allPairs.sort(Comparator.comparingInt(PairStats::getGames).reversed());

        // 기본 통계
        int totalPairs = allPairs.size();
        int totalGames = allPairs.stream().mapToInt(PairStats::getGames).sum();
        double avgGames = (double) totalGames / totalPairs;

        List<Integer> gameCounts = allPairs.stream()
                .map(PairStats::getGames)
                .sorted()
                .collect(Collectors.toList());

        int p25 = percentile(gameCounts, 25);
        int p50 = percentile(gameCounts, 50);
        int p75 = percentile(gameCounts, 75);
        int p90 = percentile(gameCounts, 90);

        log.info("\n[기본 통계]");
        log.info("  고유 페어 수:        {}", totalPairs);
        log.info("  총 페어 게임 수:     {}", totalGames);
        log.info("  페어당 평균 게임:    {:.1f}", avgGames);
        log.info("  P25 (하위 25%):     {}게임", p25);
        log.info("  P50 (중앙값):       {}게임", p50);
        log.info("  P75:               {}게임", p75);
        log.info("  P90:               {}게임", p90);

        // 게임 수 분포 히스토그램
        log.info("\n[게임 수 분포 히스토그램]");
        int[] buckets = {1, 5, 10, 20, 30, 50, 100, Integer.MAX_VALUE};
        String[] labels = {"1", "2~5", "6~10", "11~20", "21~30", "31~50", "51~100", "100+"};
        long[] counts = new long[labels.length];

        for (PairStats s : allPairs) {
            for (int i = 0; i < buckets.length; i++) {
                if (s.getGames() <= buckets[i]) {
                    counts[i]++;
                    break;
                }
            }
        }
        for (int i = 0; i < labels.length; i++) {
            double pct = counts[i] * 100.0 / totalPairs;
            String bar = "█".repeat((int) (pct / 2));
            log.info("  {:>5}게임: {:>4}개 ({:>5.1f}%) {}", labels[i], counts[i], pct, bar);
        }

        // 임계값별 Wilson score 분석
        log.info("\n[min_sample_threshold 후보별 영향]");
        int[] thresholds = {10, 20, 30, 50};
        for (int threshold : thresholds) {
            long sufficient = allPairs.stream().filter(s -> s.getGames() >= threshold).count();
            double pct = sufficient * 100.0 / totalPairs;
            double avgWilson = allPairs.stream()
                    .filter(s -> s.getGames() >= threshold)
                    .mapToDouble(s -> wilsonCalculator.calculateLower(s.getWins(), s.getGames()))
                    .average()
                    .orElse(0);
            log.info("  threshold={:>3}게임: 충족 페어 {:>4}개 ({:>5.1f}%), 평균 Wilson lower: {:.4f}",
                    threshold, sufficient, pct, avgWilson);
        }

        // Top 20 페어
        log.info("\n[게임 수 Top 20 페어]");
        log.info("  {:>6} {:>6}  {:>5}  {:>5}  {:>7}  {:>8}",
                "ADC", "SUPP", "게임", "승률", "Wilson↓", "충분?");
        allPairs.stream().limit(20).forEach(s -> {
            double winRate = s.getGames() > 0 ? (double) s.getWins() / s.getGames() : 0;
            double wilsonLower = wilsonCalculator.calculateLower(s.getWins(), s.getGames());
            log.info("  {:>6} {:>6}  {:>5}  {:.3f}  {:.4f}   {}",
                    s.getAdcChampionId(), s.getSupportChampionId(),
                    s.getGames(), winRate, wilsonLower,
                    s.getGames() >= 30 ? "✓" : "✗");
        });

        // 추천값
        int recommended = recommendThreshold(allPairs, totalPairs);
        log.info("\n========================================");
        log.info("권장 min_sample_threshold: {}게임", recommended);
        log.info("→ application.yml: ranking.min-sample-games: {}", recommended);
        log.info("========================================\n");
    }

    private int recommendThreshold(List<PairStats> allPairs, int totalPairs) {
        // 전체 페어의 50% 이상이 충족하는 최대 임계값 선택
        for (int t : new int[]{50, 30, 20, 10}) {
            long sufficient = allPairs.stream().filter(s -> s.getGames() >= t).count();
            if (sufficient >= totalPairs * 0.5) return t;
        }
        return 10;
    }

    private int percentile(List<Integer> sorted, int pct) {
        if (sorted.isEmpty()) return 0;
        int index = (int) Math.ceil(pct / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
    }

    // 내부 집계용 클래스
    static class PairStats {
        private final int adcChampionId;
        private final int supportChampionId;
        private int games = 0;
        private int wins = 0;

        PairStats(int adcChampionId, int supportChampionId) {
            this.adcChampionId = adcChampionId;
            this.supportChampionId = supportChampionId;
        }

        void add(boolean win) {
            games++;
            if (win) wins++;
        }

        int getGames() { return games; }
        int getWins() { return wins; }
        int getAdcChampionId() { return adcChampionId; }
        int getSupportChampionId() { return supportChampionId; }
    }
}
