package com.bestduo.api.dto;

import com.bestduo.domain.entity.DuoRanking;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "바텀 듀오 랭킹 목록 응답")
@Getter
@Builder
public class DuoRankingResponse {

    private List<DuoRankingItem> data;
    private Meta meta;

    @Schema(description = "개별 듀오 랭킹 항목")
    @Getter
    @Builder
    public static class DuoRankingItem {

        @Schema(description = "랭킹 순위", example = "1")
        private int rank;

        @Schema(description = "원딜 챔피언 ID (Riot champion key)", example = "222")
        private int adcChampionId;

        @Schema(description = "서포터 챔피언 ID", example = "412")
        private int supportChampionId;

        @Schema(description = "총 게임 수", example = "1240")
        private int games;

        @Schema(description = "승률 (0.0~1.0)", example = "0.543")
        private double winRate;

        @Schema(description = "픽률 (0.0~1.0)", example = "0.087")
        private double pickRate;

        @Schema(description = "Wilson score 하한 (랭킹 점수 기반)", example = "0.518")
        private double ciLower;

        @Schema(description = "티어 등급 문자열", example = "S", allowableValues = {"S", "A", "B", "C", "D"})
        private String tierGrade;

        @Schema(description = "최소 표본 충족 여부", example = "true")
        private boolean sufficientSample;

        public static DuoRankingItem from(DuoRanking ranking) {
            return DuoRankingItem.builder()
                    .rank(ranking.getRankPosition())
                    .adcChampionId(ranking.getAdcChampionId())
                    .supportChampionId(ranking.getSupportChampionId())
                    .games(ranking.getGames())
                    .winRate(ranking.getWinRate())
                    .pickRate(ranking.getPickRate())
                    .ciLower(ranking.getCiLower())
                    .tierGrade(TierGradeConverter.toLabel(ranking.getTierGrade()))
                    .sufficientSample(ranking.isSufficientSample())
                    .build();
        }
    }

    @Schema(description = "응답 메타 정보")
    @Getter
    @Builder
    public static class Meta {
        private String patch;
        private String tier;
        private int totalPairs;
        private LocalDateTime generatedAt;
    }
}
