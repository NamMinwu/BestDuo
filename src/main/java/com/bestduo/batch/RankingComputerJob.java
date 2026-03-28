package com.bestduo.batch;

import com.bestduo.calculator.WilsonScoreCalculator;
import com.bestduo.domain.entity.DuoPairStats;
import com.bestduo.domain.repository.DuoPairStatsRepository;
import com.bestduo.domain.repository.DuoRankingRepository;
import com.bestduo.domain.repository.PatchMetaRepository;
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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 듀오 랭킹 계산 배치 잡
 *
 * 흐름:
 *   1. computeRankingStep — duo_pair_stats → score 계산 → duo_ranking_staging 적재
 *   2. swapRankingStep    — duo_ranking_staging → duo_ranking atomic swap (읽기 중단 없음)
 *
 * 점수 공식:
 *   score = wilson_lower × 0.7 + normalized_pick_rate × 0.3
 *   normalized_pick_rate = pick_rate / max(pick_rate) [같은 patch, tier 기준]
 *
 * tier_grade (SMALLINT):
 *   0=S, 1=A, 2=B, 3=C, 4=D
 *
 * Atomic swap:
 *   같은 @Transactional 안에서 deleteByPatchAndTier → insertFromStaging
 *   → 라이브 읽기 트래픽이 두 테이블 사이 빈 상태를 보지 않음
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class RankingComputerJob {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final DuoPairStatsRepository duoPairStatsRepository;
    private final DuoRankingRepository duoRankingRepository;
    private final PatchMetaRepository patchMetaRepository;
    private final WilsonScoreCalculator wilsonCalculator;

    @Value("${ranking.score-weights.wilson:0.7}")
    private double wilsonWeight;

    @Value("${ranking.score-weights.pick-rate:0.3}")
    private double pickRateWeight;

    @Value("${ranking.min-sample-games:30}")
    private int minSampleGames;

    @Value("${ranking.tier-thresholds.s:0.56}")
    private double thresholdS;

    @Value("${ranking.tier-thresholds.a:0.52}")
    private double thresholdA;

    @Value("${ranking.tier-thresholds.b:0.49}")
    private double thresholdB;

    @Value("${ranking.tier-thresholds.c:0.46}")
    private double thresholdC;

    private static final List<String> TARGET_TIERS =
            List.of("ALL", "EMERALD", "DIAMOND", "MASTER", "GRANDMASTER", "CHALLENGER");

    // ─── Job ──────────────────────────────────────────────────────────────────

    @Bean
    public Job rankingComputerBatchJob() {
        return new JobBuilder("rankingComputerJob", jobRepository)
                .start(computeRankingStep())
                .next(swapRankingStep())
                .build();
    }

    // ─── Step 1: 랭킹 계산 → staging 적재 ──────────────────────────────────

    @Bean
    public Step computeRankingStep() {
        return new StepBuilder("computeRankingStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    log.info("[Step 1] 랭킹 계산 시작");

                    List<String> activePatches = patchMetaRepository.findByActiveTrueOrderByPatchDesc()
                            .stream().map(p -> p.getPatch()).toList();

                    log.info("[Step 1] 활성 패치: {}", activePatches);

                    for (String patch : activePatches) {
                        for (String tier : TARGET_TIERS) {
                            List<DuoPairStats> stats = duoPairStatsRepository.findByPatchAndTier(patch, tier);
                            if (stats.isEmpty()) continue;

                            computeAndStageRanking(patch, tier, stats);
                        }
                    }

                    log.info("[Step 1] staging 적재 완료");
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    // ─── Step 2: Atomic swap ─────────────────────────────────────────────────

    @Bean
    public Step swapRankingStep() {
        return new StepBuilder("swapRankingStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    log.info("[Step 2] duo_ranking atomic swap 시작");

                    List<String> activePatches = patchMetaRepository.findByActiveTrueOrderByPatchDesc()
                            .stream().map(p -> p.getPatch()).toList();

                    for (String patch : activePatches) {
                        for (String tier : TARGET_TIERS) {
                            atomicSwap(patch, tier);
                        }
                    }

                    log.info("[Step 2] atomic swap 완료");
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    // ─── 내부 헬퍼 ──────────────────────────────────────────────────────────

    @Transactional
    void computeAndStageRanking(String patch, String tier, List<DuoPairStats> stats) {
        // staging 초기화
        duoRankingRepository.clearStaging(patch, tier);

        // 최대 pick_rate 조회 (정규화 분모)
        double maxPickRate = stats.stream()
                .mapToDouble(DuoPairStats::getPickRate)
                .max()
                .orElse(1.0);
        if (maxPickRate == 0) maxPickRate = 1.0;

        // 점수 계산
        List<ScoredPair> scored = new ArrayList<>();
        for (DuoPairStats s : stats) {
            double wilsonLower = s.getGames() > 0
                    ? wilsonCalculator.calculateLower(s.getWins(), s.getGames()) : 0;
            double normalizedPickRate = s.getPickRate() / maxPickRate;
            double score = wilsonLower * wilsonWeight + normalizedPickRate * pickRateWeight;
            scored.add(new ScoredPair(s, score));
        }

        // score 내림차순 정렬
        scored.sort(Comparator.comparingDouble(ScoredPair::score).reversed());

        // staging INSERT
        for (int i = 0; i < scored.size(); i++) {
            ScoredPair sp = scored.get(i);
            DuoPairStats s = sp.stats();
            int rankPosition = i + 1;
            int tierGrade = computeTierGrade(sp.score());

            duoRankingRepository.insertIntoStaging(
                    patch, tier, rankPosition,
                    s.getAdcChampionId(), s.getSupportChampionId(),
                    sp.score(), tierGrade,
                    s.getWinRate(), s.getPickRate(),
                    s.getGames(), s.getCiLower(),
                    s.isSufficientSample());
        }

        log.debug("[Staging] patch={} tier={} 랭킹 {}개 적재", patch, tier, scored.size());
    }

    @Transactional
    void atomicSwap(String patch, String tier) {
        duoRankingRepository.deleteByPatchAndTier(patch, tier);
        duoRankingRepository.insertFromStaging(patch, tier);
        log.debug("[Swap] patch={} tier={} 교체 완료", patch, tier);
    }

    int computeTierGrade(double score) {
        if (score >= thresholdS) return 0;  // S
        if (score >= thresholdA) return 1;  // A
        if (score >= thresholdB) return 2;  // B
        if (score >= thresholdC) return 3;  // C
        return 4;                           // D
    }

    private record ScoredPair(DuoPairStats stats, double score) {}
}
