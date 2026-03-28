package com.bestduo.api.dto;

import com.bestduo.domain.entity.PatchMeta;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Schema(description = "패치 정보")
@Getter
@Builder
public class PatchResponse {

    @Schema(description = "패치 버전", example = "15.6")
    private String patch;

    @Schema(description = "활성 패치 여부", example = "true")
    private boolean active;

    public static PatchResponse from(PatchMeta meta) {
        return PatchResponse.builder()
                .patch(meta.getPatch())
                .active(meta.isActive())
                .build();
    }
}
