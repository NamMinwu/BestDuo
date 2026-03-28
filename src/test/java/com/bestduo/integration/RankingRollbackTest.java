package com.bestduo.integration;

import com.bestduo.domain.entity.DuoRanking;
import com.bestduo.domain.repository.DuoRankingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DuoRankingRepository — delete/insert 시퀀스 및 조회 통합 테스트
 *
 * 테스트 전략:
 *   H2 in-memory DB에서 실제 JPA repository 메서드를 직접 호출하여 검증.
 *   atomicSwap의 내부 구성요소인 deleteByPatchAndTier + save를 시뮬레이션.
 *
 * 검증 항목:
 *   1. delete → insert 시퀀스 후 duo_ranking 정확히 반영
 *   2. deleteByPatchAndTier가 지정한 (patch, tier)만 삭제
 *   3. 정렬 조회 (rank_position, win_rate, pick_rate)
 *   4. 특정 조합 상세 조회 및 없는 조합 empty 반환
 */
@SpringBootTest
class RankingRollbackTest {

    @Autowired
    private DuoRankingRepository duoRankingRepository;

    private static final String PATCH = "15.6";
    private static final String TIER = "ALL";

    @BeforeEach
    @Transactional
    void setUp() {
        duoRankingRepository.deleteAll();
    }

    // ─── delete → insert 시퀀스 검증 ─────────────────────────────────────────

    @Test
    @Transactional
    void 기존_데이터_삭제후_신규_데이터_삽입() {
        // 기존 데이터 저장
        duoRankingRepository.save(ranking(PATCH, TIER, 1, 222, 99));

        // 신규 데이터로 교체 (delete + insert를 수동으로 시뮬레이션)
        duoRankingRepository.deleteByPatchAndTier(PATCH, TIER);
        duoRankingRepository.save(ranking(PATCH, TIER, 1, 235, 412));
        duoRankingRepository.save(ranking(PATCH, TIER, 2, 51, 99));

        List<DuoRanking> result = duoRankingRepository
                .findByPatchAndTierOrderByRankPositionAsc(PATCH, TIER);

        assertThat(result).hasSize(2);
        // 기존 222번 삭제됨
        assertThat(result).noneMatch(r -> r.getAdcChampionId() == 222);
        // 신규 235, 51번 존재
        assertThat(result.get(0).getAdcChampionId()).isEqualTo(235);
        assertThat(result.get(1).getAdcChampionId()).isEqualTo(51);
    }

    @Test
    @Transactional
    void deleteByPatchAndTier_정확한_범위만_삭제() {
        duoRankingRepository.save(ranking(PATCH, TIER, 1, 235, 412));
        duoRankingRepository.save(ranking("15.5", TIER, 1, 222, 99));
        duoRankingRepository.save(ranking(PATCH, "DIAMOND", 1, 51, 412));

        duoRankingRepository.deleteByPatchAndTier(PATCH, TIER);

        // 15.6 ALL만 삭제
        assertThat(duoRankingRepository.findByPatchAndTierOrderByRankPositionAsc(PATCH, TIER))
                .isEmpty();
        // 다른 patch/tier는 보존
        assertThat(duoRankingRepository.findByPatchAndTierOrderByRankPositionAsc("15.5", TIER))
                .hasSize(1);
        assertThat(duoRankingRepository.findByPatchAndTierOrderByRankPositionAsc(PATCH, "DIAMOND"))
                .hasSize(1);
    }

    // ─── 정렬 조회 검증 ───────────────────────────────────────────────────────

    @Test
    @Transactional
    void 랭킹순_조회_rank_position_오름차순() {
        duoRankingRepository.save(ranking(PATCH, TIER, 3, 3, 3));
        duoRankingRepository.save(ranking(PATCH, TIER, 1, 1, 1));
        duoRankingRepository.save(ranking(PATCH, TIER, 2, 2, 2));

        List<DuoRanking> result = duoRankingRepository
                .findByPatchAndTierOrderByRankPositionAsc(PATCH, TIER);

        assertThat(result).hasSize(3);
        assertThat(result.get(0).getRankPosition()).isEqualTo(1);
        assertThat(result.get(1).getRankPosition()).isEqualTo(2);
        assertThat(result.get(2).getRankPosition()).isEqualTo(3);
    }

    @Test
    @Transactional
    void 승률순_조회_win_rate_내림차순() {
        duoRankingRepository.save(rankingWithWinRate(PATCH, TIER, 1, 235, 412, 0.50));
        duoRankingRepository.save(rankingWithWinRate(PATCH, TIER, 2, 51, 99, 0.60));
        duoRankingRepository.save(rankingWithWinRate(PATCH, TIER, 3, 222, 40, 0.55));

        List<DuoRanking> result = duoRankingRepository
                .findByPatchAndTierOrderByWinRateDesc(PATCH, TIER);

        assertThat(result).hasSize(3);
        assertThat(result.get(0).getWinRate()).isEqualTo(0.60);
        assertThat(result.get(1).getWinRate()).isEqualTo(0.55);
        assertThat(result.get(2).getWinRate()).isEqualTo(0.50);
    }

    @Test
    @Transactional
    void 픽률순_조회_pick_rate_내림차순() {
        duoRankingRepository.save(rankingWithPickRate(PATCH, TIER, 1, 235, 412, 0.04));
        duoRankingRepository.save(rankingWithPickRate(PATCH, TIER, 2, 51, 99, 0.10));
        duoRankingRepository.save(rankingWithPickRate(PATCH, TIER, 3, 222, 40, 0.07));

        List<DuoRanking> result = duoRankingRepository
                .findByPatchAndTierOrderByPickRateDesc(PATCH, TIER);

        assertThat(result).hasSize(3);
        assertThat(result.get(0).getPickRate()).isEqualTo(0.10);
        assertThat(result.get(1).getPickRate()).isEqualTo(0.07);
        assertThat(result.get(2).getPickRate()).isEqualTo(0.04);
    }

    @Test
    @Transactional
    void 특정_조합_상세_조회() {
        duoRankingRepository.save(ranking(PATCH, TIER, 1, 235, 412));
        duoRankingRepository.save(ranking(PATCH, TIER, 2, 51, 99));

        var result = duoRankingRepository
                .findByPatchAndTierAndAdcChampionIdAndSupportChampionId(PATCH, TIER, 235, 412);

        assertThat(result).isPresent();
        assertThat(result.get().getAdcChampionId()).isEqualTo(235);
        assertThat(result.get().getSupportChampionId()).isEqualTo(412);
    }

    @Test
    @Transactional
    void 없는_조합_조회시_empty() {
        var result = duoRankingRepository
                .findByPatchAndTierAndAdcChampionIdAndSupportChampionId(PATCH, TIER, 999, 888);
        assertThat(result).isEmpty();
    }

    // ─── 헬퍼 ─────────────────────────────────────────────────────────────────

    private DuoRanking ranking(String patch, String tier, int rank, int adcId, int suppId) {
        return DuoRanking.builder()
                .patch(patch).tier(tier).rankPosition(rank)
                .adcChampionId(adcId).supportChampionId(suppId)
                .score(0.55).tierGrade(1)
                .winRate(0.53).pickRate(0.07).games(300)
                .ciLower(0.50).sufficientSample(true)
                .build();
    }

    private DuoRanking rankingWithWinRate(String patch, String tier, int rank,
                                           int adcId, int suppId, double winRate) {
        return DuoRanking.builder()
                .patch(patch).tier(tier).rankPosition(rank)
                .adcChampionId(adcId).supportChampionId(suppId)
                .score(winRate * 0.7).tierGrade(1)
                .winRate(winRate).pickRate(0.07).games(300)
                .ciLower(winRate - 0.03).sufficientSample(true)
                .build();
    }

    private DuoRanking rankingWithPickRate(String patch, String tier, int rank,
                                            int adcId, int suppId, double pickRate) {
        return DuoRanking.builder()
                .patch(patch).tier(tier).rankPosition(rank)
                .adcChampionId(adcId).supportChampionId(suppId)
                .score(0.55).tierGrade(1)
                .winRate(0.53).pickRate(pickRate).games(300)
                .ciLower(0.50).sufficientSample(true)
                .build();
    }
}
