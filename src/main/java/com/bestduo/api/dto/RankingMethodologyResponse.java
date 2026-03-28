package com.bestduo.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.Map;

@Schema(description = "랭킹 알고리즘 공개 정보")
@Getter
@Builder
public class RankingMethodologyResponse {

    @Schema(description = "랭킹 점수 공식",
            example = "score = wilson_lower(wins, games) × 0.7 + normalized_pick_rate × 0.3")
    private String formula;

    @Schema(description = "티어 등급 경계값 (score 기준)")
    private Map<String, Double> tierThresholds;

    @Schema(description = "최소 표본 게임 수 (이하: 데이터 부족 표시)", example = "30")
    private int minSampleGames;

    @Schema(description = "Wilson score 신뢰구간", example = "95%")
    private String confidenceInterval;
}
