package com.bestduo.api.controller;

import com.bestduo.api.dto.PatchResponse;
import com.bestduo.api.dto.RankingMethodologyResponse;
import com.bestduo.domain.repository.PatchMetaRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "메타", description = "패치 정보 및 랭킹 알고리즘 공개 API")
@RestController
@RequestMapping("/api/v1/meta")
@RequiredArgsConstructor
public class MetaController {

    private final PatchMetaRepository patchMetaRepository;

    @Value("${ranking.min-sample-games}")
    private int minSampleGames;

    @Value("${ranking.tier-thresholds.s}")
    private double thresholdS;

    @Value("${ranking.tier-thresholds.a}")
    private double thresholdA;

    @Value("${ranking.tier-thresholds.b}")
    private double thresholdB;

    @Value("${ranking.tier-thresholds.c}")
    private double thresholdC;

    @Operation(summary = "활성 패치 목록 조회 (최신 2개)")
    @GetMapping("/patches")
    public ResponseEntity<List<PatchResponse>> getPatches() {
        List<PatchResponse> patches = patchMetaRepository.findByActiveTrueOrderByPatchDesc()
                .stream()
                .map(PatchResponse::from)
                .toList();
        return ResponseEntity.ok(patches);
    }

    @Operation(summary = "랭킹 알고리즘 공개 정보")
    @GetMapping("/ranking-methodology")
    public ResponseEntity<RankingMethodologyResponse> getRankingMethodology() {
        RankingMethodologyResponse response = RankingMethodologyResponse.builder()
                .formula("score = wilson_lower(wins, games) × 0.7 + normalized_pick_rate × 0.3")
                .tierThresholds(Map.of(
                        "S", thresholdS,
                        "A", thresholdA,
                        "B", thresholdB,
                        "C", thresholdC
                ))
                .minSampleGames(minSampleGames)
                .confidenceInterval("95%")
                .build();
        return ResponseEntity.ok(response);
    }
}
