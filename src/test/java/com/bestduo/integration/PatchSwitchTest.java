package com.bestduo.integration;

import com.bestduo.domain.entity.PatchMeta;
import com.bestduo.domain.repository.DuoRankingRepository;
import com.bestduo.domain.repository.PatchMetaRepository;
import com.bestduo.scheduler.PatchDetectionScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 패치 전환 E2E 통합 테스트
 *
 * 검증 항목:
 *   1. 신규 패치 등록 → patch_meta에 is_active=TRUE로 저장
 *   2. 2개 초과 시 오래된 패치 자동 비활성화 (최신 2개만 active)
 *   3. 비활성화된 패치의 duo_ranking 데이터는 보존됨
 *   4. active 패치만 findByActiveTrueOrderByPatchDesc에서 반환
 */
@SpringBootTest
class PatchSwitchTest {

    @Autowired
    private PatchMetaRepository patchMetaRepository;

    @Autowired
    private DuoRankingRepository duoRankingRepository;

    @Autowired
    private PatchDetectionScheduler scheduler;

    @BeforeEach
    @Transactional
    void setUp() {
        duoRankingRepository.deleteAll();
        patchMetaRepository.deleteAll();
    }

    // ─── 패치 등록 ────────────────────────────────────────────────────────────

    @Test
    @Transactional
    void 신규패치_등록_active_true() {
        scheduler.registerNewPatch("15.7");

        PatchMeta saved = patchMetaRepository.findById("15.7").orElseThrow();
        assertThat(saved.isActive()).isTrue();
        assertThat(saved.getReleasedAt()).isNotNull();
    }

    @Test
    @Transactional
    void 같은패치_중복등록_불가_확인() {
        scheduler.registerNewPatch("15.7");
        scheduler.registerNewPatch("15.7"); // 두 번째 호출

        // 동일 patch PK이므로 1건만 존재 (upsert 대신 save → 두번째 덮어씀)
        long count = patchMetaRepository.findAll().stream()
                .filter(p -> "15.7".equals(p.getPatch()))
                .count();
        assertThat(count).isEqualTo(1);
    }

    // ─── 패치 비활성화 ────────────────────────────────────────────────────────

    @Test
    @Transactional
    void 패치_2개이하면_비활성화_없음() {
        patchMetaRepository.save(active("15.6"));
        patchMetaRepository.save(active("15.7"));

        scheduler.deactivateOldPatches();

        List<PatchMeta> activePatches = patchMetaRepository.findByActiveTrueOrderByPatchDesc();
        assertThat(activePatches).hasSize(2);
        assertThat(activePatches).allMatch(PatchMeta::isActive);
    }

    @Test
    @Transactional
    void 패치_3개이면_가장오래된_1개_비활성화() {
        patchMetaRepository.save(active("15.5"));
        patchMetaRepository.save(active("15.6"));
        patchMetaRepository.save(active("15.7"));

        scheduler.deactivateOldPatches();

        List<PatchMeta> activePatches = patchMetaRepository.findByActiveTrueOrderByPatchDesc();
        assertThat(activePatches).hasSize(2);
        assertThat(activePatches).extracting(PatchMeta::getPatch)
                .containsExactly("15.7", "15.6");

        PatchMeta old = patchMetaRepository.findById("15.5").orElseThrow();
        assertThat(old.isActive()).isFalse();
    }

    @Test
    @Transactional
    void 패치_4개이면_오래된_2개_비활성화() {
        patchMetaRepository.save(active("15.4"));
        patchMetaRepository.save(active("15.5"));
        patchMetaRepository.save(active("15.6"));
        patchMetaRepository.save(active("15.7"));

        scheduler.deactivateOldPatches();

        List<PatchMeta> activePatches = patchMetaRepository.findByActiveTrueOrderByPatchDesc();
        assertThat(activePatches).hasSize(2);
        assertThat(activePatches).extracting(PatchMeta::getPatch)
                .containsExactly("15.7", "15.6");
    }

    // ─── 비활성화 후 랭킹 데이터 보존 ────────────────────────────────────────

    @Test
    @Transactional
    void 비활성화된_패치의_랭킹데이터_보존() {
        // 15.5, 15.6, 15.7 등록
        patchMetaRepository.save(active("15.5"));
        patchMetaRepository.save(active("15.6"));
        patchMetaRepository.save(active("15.7"));

        // 15.5 패치에 랭킹 데이터 저장
        duoRankingRepository.save(ranking("15.5", 235, 412));

        // 패치 전환 (15.5 비활성화)
        scheduler.deactivateOldPatches();

        // 15.5 patch_meta는 비활성화
        assertThat(patchMetaRepository.findById("15.5").orElseThrow().isActive()).isFalse();

        // 그러나 15.5의 duo_ranking 데이터는 여전히 조회 가능
        var oldRankings = duoRankingRepository.findByPatchAndTierOrderByRankPositionAsc("15.5", "ALL");
        assertThat(oldRankings).hasSize(1);
        assertThat(oldRankings.get(0).getAdcChampionId()).isEqualTo(235);
    }

    // ─── 헬퍼 ─────────────────────────────────────────────────────────────────

    private PatchMeta active(String patch) {
        return PatchMeta.builder()
                .patch(patch)
                .active(true)
                .build();
    }

    private com.bestduo.domain.entity.DuoRanking ranking(String patch, int adcId, int suppId) {
        return com.bestduo.domain.entity.DuoRanking.builder()
                .patch(patch).tier("ALL").rankPosition(1)
                .adcChampionId(adcId).supportChampionId(suppId)
                .score(0.55).tierGrade(1)
                .winRate(0.53).pickRate(0.07).games(300)
                .ciLower(0.50).sufficientSample(true)
                .build();
    }
}
