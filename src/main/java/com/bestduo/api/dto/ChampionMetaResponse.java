package com.bestduo.api.dto;

import com.bestduo.domain.entity.ChampionMeta;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Schema(description = "챔피언 메타 목록 응답")
@Getter
@Builder
public class ChampionMetaResponse {

    private List<ChampionItem> champions;
    private int total;

    @Schema(description = "챔피언 항목")
    @Getter
    @Builder
    public static class ChampionItem {

        @Schema(description = "챔피언 ID (Riot key)", example = "21")
        private int championId;

        @Schema(description = "표시 이름", example = "미스 포츈")
        private String name;

        @Schema(description = "Data Dragon 내부 키", example = "MissFortune")
        private String key;

        @Schema(description = "아이콘 이미지 URL")
        private String imageUrl;

        public static ChampionItem from(ChampionMeta meta) {
            return ChampionItem.builder()
                    .championId(meta.getChampionId())
                    .name(meta.getName())
                    .key(meta.getKey())
                    .imageUrl(meta.getImageUrl())
                    .build();
        }
    }

    public static ChampionMetaResponse of(List<ChampionMeta> champions) {
        return ChampionMetaResponse.builder()
                .champions(champions.stream().map(ChampionItem::from).toList())
                .total(champions.size())
                .build();
    }
}
