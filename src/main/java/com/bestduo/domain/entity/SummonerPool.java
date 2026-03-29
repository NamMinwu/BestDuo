package com.bestduo.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "summoner_pool")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class SummonerPool {

    @Id
    @Column(name = "puuid", nullable = false, length = 78)
    private String puuid;

    // EMERALD, DIAMOND, MASTER, GRANDMASTER, CHALLENGER
    @Column(name = "tier", length = 20)
    private String tier;

    // LEAGUE-V4 티어 확인 완료 여부
    // FALSE: 매치에서 발견됐지만 티어 미확인
    // TRUE: LEAGUE-V4 API로 에메랄드+ 확인 완료 (이후 재검증 시에도 TRUE 유지)
    @Column(name = "is_verified", nullable = false)
    @Builder.Default
    private boolean verified = false;

    // 마지막으로 LEAGUE-V4 API로 티어를 확인한 시각
    // NULL = 한 번도 검증 안 됨
    // N일 이상 경과 시 TierVerificationJob이 재검증 대상으로 포함
    @Column(name = "tier_verified_at")
    private LocalDateTime tierVerifiedAt;

    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;

    public void updateTier(String tier) {
        this.tier = tier;
        this.verified = true;
        this.tierVerifiedAt = LocalDateTime.now();
        this.lastSeenAt = LocalDateTime.now();
    }

    public void updateLastSeen() {
        this.lastSeenAt = LocalDateTime.now();
    }
}
