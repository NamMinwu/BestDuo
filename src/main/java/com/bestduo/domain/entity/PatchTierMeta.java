package com.bestduo.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;

@Entity
@Table(name = "patch_tier_meta")
@IdClass(PatchTierMeta.PatchTierMetaId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class PatchTierMeta {

    @Id
    @Column(name = "patch", nullable = false, length = 10)
    private String patch;

    @Id
    @Column(name = "tier", nullable = false, length = 20)
    private String tier;

    /**
     * 해당 (patch, tier)의 전체 바텀 게임 수
     *
     * pick_rate 분모로 사용.
     * 계산: 전체 매치 수 × 2
     * (매치 1건에 블루팀 바텀 페어 1개 + 레드팀 바텀 페어 1개 = 2개)
     */
    @Column(name = "total_bottom_games", nullable = false)
    private long totalBottomGames;

    @Getter
    @EqualsAndHashCode
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PatchTierMetaId implements Serializable {
        private String patch;
        private String tier;
    }
}
