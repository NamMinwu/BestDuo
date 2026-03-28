package com.bestduo.api.controller;

import com.bestduo.domain.entity.DuoRanking;
import com.bestduo.domain.repository.DuoRankingRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DuosController.class)
class DuosControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DuoRankingRepository duoRankingRepository;

    // ─── 정렬 기준 ────────────────────────────────────────────────────────────

    @Test
    void sort_RANK_기본값_랭킹순_정렬_호출() throws Exception {
        when(duoRankingRepository.findByPatchAndTierOrderByRankPositionAsc("15.6", "ALL"))
                .thenReturn(List.of(ranking(1, 235, 412, 0.54, 0.08, 0)));

        mockMvc.perform(get("/api/v1/duos").param("patch", "15.6"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].rank").value(1))
                .andExpect(jsonPath("$.data[0].adcChampionId").value(235))
                .andExpect(jsonPath("$.data[0].supportChampionId").value(412));

        verify(duoRankingRepository).findByPatchAndTierOrderByRankPositionAsc("15.6", "ALL");
        verify(duoRankingRepository, never()).findByPatchAndTierOrderByWinRateDesc(any(), any());
        verify(duoRankingRepository, never()).findByPatchAndTierOrderByPickRateDesc(any(), any());
    }

    @Test
    void sort_WIN_RATE_승률순_정렬_호출() throws Exception {
        when(duoRankingRepository.findByPatchAndTierOrderByWinRateDesc("15.6", "ALL"))
                .thenReturn(List.of(ranking(1, 235, 412, 0.60, 0.05, 0)));

        mockMvc.perform(get("/api/v1/duos")
                        .param("patch", "15.6")
                        .param("sort", "WIN_RATE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].winRate").value(0.60));

        verify(duoRankingRepository).findByPatchAndTierOrderByWinRateDesc("15.6", "ALL");
    }

    @Test
    void sort_PICK_RATE_픽률순_정렬_호출() throws Exception {
        when(duoRankingRepository.findByPatchAndTierOrderByPickRateDesc("15.6", "ALL"))
                .thenReturn(List.of(ranking(1, 51, 99, 0.52, 0.12, 1)));

        mockMvc.perform(get("/api/v1/duos")
                        .param("patch", "15.6")
                        .param("sort", "PICK_RATE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].pickRate").value(0.12));

        verify(duoRankingRepository).findByPatchAndTierOrderByPickRateDesc("15.6", "ALL");
    }

    @Test
    void sort_소문자도_정상처리() throws Exception {
        when(duoRankingRepository.findByPatchAndTierOrderByWinRateDesc("15.6", "ALL"))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/duos")
                        .param("patch", "15.6")
                        .param("sort", "win_rate"))
                .andExpect(status().isOk());

        verify(duoRankingRepository).findByPatchAndTierOrderByWinRateDesc("15.6", "ALL");
    }

    @Test
    void sort_알수없는값이면_RANK_기본값() throws Exception {
        when(duoRankingRepository.findByPatchAndTierOrderByRankPositionAsc("15.6", "ALL"))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/duos")
                        .param("patch", "15.6")
                        .param("sort", "INVALID"))
                .andExpect(status().isOk());

        verify(duoRankingRepository).findByPatchAndTierOrderByRankPositionAsc("15.6", "ALL");
    }

    // ─── 티어 필터 ────────────────────────────────────────────────────────────

    @Test
    void tier_기본값_ALL() throws Exception {
        when(duoRankingRepository.findByPatchAndTierOrderByRankPositionAsc("15.6", "ALL"))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/duos").param("patch", "15.6"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.tier").value("ALL"));
    }

    @Test
    void tier_DIAMOND_필터_전달() throws Exception {
        when(duoRankingRepository.findByPatchAndTierOrderByRankPositionAsc("15.6", "DIAMOND"))
                .thenReturn(List.of(ranking(1, 235, 412, 0.55, 0.07, 0)));

        mockMvc.perform(get("/api/v1/duos")
                        .param("patch", "15.6")
                        .param("tier", "DIAMOND"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.tier").value("DIAMOND"))
                .andExpect(jsonPath("$.data", hasSize(1)));

        verify(duoRankingRepository).findByPatchAndTierOrderByRankPositionAsc("15.6", "DIAMOND");
    }

    // ─── meta 응답 검증 ───────────────────────────────────────────────────────

    @Test
    void 응답_meta에_patch_tier_totalPairs_포함() throws Exception {
        when(duoRankingRepository.findByPatchAndTierOrderByRankPositionAsc("15.6", "ALL"))
                .thenReturn(List.of(ranking(1, 235, 412, 0.54, 0.08, 0),
                                    ranking(2, 51, 99, 0.51, 0.06, 1)));

        mockMvc.perform(get("/api/v1/duos").param("patch", "15.6"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.patch").value("15.6"))
                .andExpect(jsonPath("$.meta.tier").value("ALL"))
                .andExpect(jsonPath("$.meta.totalPairs").value(2));
    }

    // ─── tierGrade 문자열 변환 ────────────────────────────────────────────────

    @Test
    void tierGrade_0이면_S_반환() throws Exception {
        when(duoRankingRepository.findByPatchAndTierOrderByRankPositionAsc("15.6", "ALL"))
                .thenReturn(List.of(ranking(1, 235, 412, 0.58, 0.09, 0)));

        mockMvc.perform(get("/api/v1/duos").param("patch", "15.6"))
                .andExpect(jsonPath("$.data[0].tierGrade").value("S"));
    }

    @Test
    void tierGrade_4이면_D_반환() throws Exception {
        when(duoRankingRepository.findByPatchAndTierOrderByRankPositionAsc("15.6", "ALL"))
                .thenReturn(List.of(ranking(1, 235, 412, 0.48, 0.02, 4)));

        mockMvc.perform(get("/api/v1/duos").param("patch", "15.6"))
                .andExpect(jsonPath("$.data[0].tierGrade").value("D"));
    }

    // ─── 상세 조회 ────────────────────────────────────────────────────────────

    @Test
    void 상세조회_존재하면_200() throws Exception {
        when(duoRankingRepository.findByPatchAndTierAndAdcChampionIdAndSupportChampionId(
                "15.6", "ALL", 235, 412))
                .thenReturn(Optional.of(ranking(1, 235, 412, 0.54, 0.08, 0)));

        mockMvc.perform(get("/api/v1/duos/235/412")
                        .param("patch", "15.6"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.adcChampionId").value(235))
                .andExpect(jsonPath("$.supportChampionId").value(412));
    }

    @Test
    void 상세조회_없으면_404() throws Exception {
        when(duoRankingRepository.findByPatchAndTierAndAdcChampionIdAndSupportChampionId(
                "15.6", "ALL", 999, 888))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/duos/999/888")
                        .param("patch", "15.6"))
                .andExpect(status().isNotFound());
    }

    @Test
    void 상세조회_tier_파라미터_전달() throws Exception {
        when(duoRankingRepository.findByPatchAndTierAndAdcChampionIdAndSupportChampionId(
                "15.6", "MASTER", 235, 412))
                .thenReturn(Optional.of(ranking(1, 235, 412, 0.55, 0.07, 0)));

        mockMvc.perform(get("/api/v1/duos/235/412")
                        .param("patch", "15.6")
                        .param("tier", "MASTER"))
                .andExpect(status().isOk());

        verify(duoRankingRepository)
                .findByPatchAndTierAndAdcChampionIdAndSupportChampionId("15.6", "MASTER", 235, 412);
    }

    // ─── 헬퍼 ─────────────────────────────────────────────────────────────────

    private DuoRanking ranking(int rank, int adcId, int suppId,
                               double winRate, double pickRate, int tierGrade) {
        return DuoRanking.builder()
                .patch("15.6")
                .tier("ALL")
                .rankPosition(rank)
                .adcChampionId(adcId)
                .supportChampionId(suppId)
                .score(winRate * 0.7 + pickRate * 0.3)
                .tierGrade(tierGrade)
                .winRate(winRate)
                .pickRate(pickRate)
                .games(500)
                .ciLower(winRate - 0.03)
                .sufficientSample(true)
                .build();
    }
}
