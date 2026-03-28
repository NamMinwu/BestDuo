package com.bestduo.batch;

import com.bestduo.calculator.WilsonScoreCalculator;
import com.bestduo.domain.entity.DuoPairStats;
import com.bestduo.domain.repository.DuoPairStatsRepository;
import com.bestduo.domain.repository.DuoRankingRepository;
import com.bestduo.domain.repository.PatchMetaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RankingComputerJobTest {

    @Mock private DuoPairStatsRepository duoPairStatsRepository;
    @Mock private DuoRankingRepository duoRankingRepository;
    @Mock private PatchMetaRepository patchMetaRepository;

    private RankingComputerJob job;

    // 실제 WilsonScoreCalculator 사용 (핵심 알고리즘 mock 금지)
    private final WilsonScoreCalculator wilsonCalculator = new WilsonScoreCalculator();

    @BeforeEach
    void setUp() {
        job = new RankingComputerJob(
                null, null,
                duoPairStatsRepository, duoRankingRepository,
                patchMetaRepository, wilsonCalculator
        );
        setField(job, "wilsonWeight", 0.7);
        setField(job, "pickRateWeight", 0.3);
        setField(job, "minSampleGames", 30);
        setField(job, "thresholdS", 0.56);
        setField(job, "thresholdA", 0.52);
        setField(job, "thresholdB", 0.49);
        setField(job, "thresholdC", 0.46);
    }

    // ─── computeTierGrade 경계값 테스트 ──────────────────────────────────────

    @Test
    void score_0_56이상이면_S등급() {
        assertThat(job.computeTierGrade(0.56)).isEqualTo(0);
        assertThat(job.computeTierGrade(0.70)).isEqualTo(0);
        assertThat(job.computeTierGrade(1.00)).isEqualTo(0);
    }

    @Test
    void score_0_52이상_0_56미만이면_A등급() {
        assertThat(job.computeTierGrade(0.52)).isEqualTo(1);
        assertThat(job.computeTierGrade(0.54)).isEqualTo(1);
        assertThat(job.computeTierGrade(0.559)).isEqualTo(1);
    }

    @Test
    void score_0_49이상_0_52미만이면_B등급() {
        assertThat(job.computeTierGrade(0.49)).isEqualTo(2);
        assertThat(job.computeTierGrade(0.505)).isEqualTo(2);
        assertThat(job.computeTierGrade(0.519)).isEqualTo(2);
    }

    @Test
    void score_0_46이상_0_49미만이면_C등급() {
        assertThat(job.computeTierGrade(0.46)).isEqualTo(3);
        assertThat(job.computeTierGrade(0.475)).isEqualTo(3);
        assertThat(job.computeTierGrade(0.489)).isEqualTo(3);
    }

    @Test
    void score_0_46미만이면_D등급() {
        assertThat(job.computeTierGrade(0.459)).isEqualTo(4);
        assertThat(job.computeTierGrade(0.20)).isEqualTo(4);
        assertThat(job.computeTierGrade(0.00)).isEqualTo(4);
    }

    // ─── is_sufficient_sample 테스트 ─────────────────────────────────────────

    @Test
    void games가_minSample_이상이면_sufficientSample_true() {
        DuoPairStats stats = pairStats(235, 412, 30, 16, 0.53, 1.0);
        assertThat(stats.isSufficientSample()).isTrue();
    }

    @Test
    void games가_minSample_미만이면_sufficientSample_false() {
        DuoPairStats stats = pairStats(235, 412, 29, 15, 0.52, 1.0);
        assertThat(stats.isSufficientSample()).isFalse();
    }

    @Test
    void games_정확히_minSample이면_sufficientSample_true() {
        DuoPairStats stats = pairStats(235, 412, 30, 15, 0.50, 1.0);
        assertThat(stats.isSufficientSample()).isTrue();
    }

    // ─── 랭킹 순서 (rank_position) 테스트 ───────────────────────────────────

    @Test
    void 높은_score_페어가_rank1에_배치() {
        // high: 1000게임 700승, pick_rate=1.0 (max)
        // low:  100게임 40승,  pick_rate=0.2
        DuoPairStats high = pairStats(235, 412, 1000, 700, 0.70, 1.0);
        DuoPairStats low  = pairStats(51, 99,   100, 40,  0.40, 0.2);

        doNothing().when(duoRankingRepository).clearStaging(any(), any());

        job.computeAndStageRanking("15.6", "ALL", List.of(low, high)); // 순서 반대로 입력

        var rankCaptor = ArgumentCaptor.forClass(Integer.class);
        var adcCaptor  = ArgumentCaptor.forClass(Integer.class);
        verify(duoRankingRepository, times(2)).insertIntoStaging(
                eq("15.6"), eq("ALL"),
                rankCaptor.capture(), adcCaptor.capture(),
                anyInt(), anyDouble(), anyInt(),
                anyDouble(), anyDouble(), anyInt(), anyDouble(), anyBoolean());

        int idxRank1 = rankCaptor.getAllValues().indexOf(1);
        assertThat(adcCaptor.getAllValues().get(idxRank1)).isEqualTo(235); // high 페어
    }

    @Test
    void 동일_patch_tier에서_랭킹_연속_부여() {
        DuoPairStats a = pairStats(1, 2, 500, 280, 0.56, 1.0);
        DuoPairStats b = pairStats(3, 4, 300, 150, 0.50, 0.5);
        DuoPairStats c = pairStats(5, 6, 100, 45,  0.45, 0.2);

        doNothing().when(duoRankingRepository).clearStaging(any(), any());

        job.computeAndStageRanking("15.6", "ALL", List.of(a, b, c));

        var rankCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(duoRankingRepository, times(3)).insertIntoStaging(
                anyString(), anyString(),
                rankCaptor.capture(),
                anyInt(), anyInt(), anyDouble(), anyInt(),
                anyDouble(), anyDouble(), anyInt(), anyDouble(), anyBoolean());

        assertThat(rankCaptor.getAllValues()).containsExactlyInAnyOrder(1, 2, 3);
    }

    // ─── 헬퍼 ─────────────────────────────────────────────────────────────────

    private DuoPairStats pairStats(int adcId, int suppId, int games, int wins,
                                   double winRate, double pickRate) {
        return DuoPairStats.builder()
                .patch("15.6")
                .tier("ALL")
                .adcChampionId(adcId)
                .supportChampionId(suppId)
                .games(games)
                .wins(wins)
                .winRate(winRate)
                .pickRate(pickRate)
                .ciLower(games > 0 ? wilsonCalculator.calculateLower(wins, games) : 0)
                .ciUpper(games > 0 ? wilsonCalculator.calculateUpper(wins, games) : 0)
                .sufficientSample(games >= 30)
                .build();
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
