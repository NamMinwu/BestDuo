package com.bestduo.domain.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * 바텀 듀오 조합별 집계 통계
 *
 * tier='ALL'은 에메랄드~챌린저 전체 합산 사전집계 행 (별도 행으로 저장)
 * pick_rate = games / patch_tier_meta.total_bottom_games
 *
 * total_bottom_games = 해당 (patch, tier)의 전체 매치 수 × 2
 * (매치 1건 = 바텀 페어 2개: 블루팀 1 + 레드팀 1)
 */
@Entity
@Table(name = "duo_pair_stats")
@IdClass(DuoPairStatsId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class DuoPairStats {

    @Id
    @Column(name = "patch", nullable = false, length = 10)
    private String patch;

    @Id
    @Column(name = "tier", nullable = false, length = 20)
    private String tier;

    @Id
    @Column(name = "adc_champion_id", nullable = false)
    private Integer adcChampionId;

    @Id
    @Column(name = "support_champion_id", nullable = false)
    private Integer supportChampionId;

    @Column(name = "games", nullable = false)
    private int games;

    @Column(name = "wins", nullable = false)
    private int wins;

    @Column(name = "win_rate", nullable = false)
    private double winRate;

    @Column(name = "pick_rate", nullable = false)
    private double pickRate;

    @Column(name = "ci_lower", nullable = false)
    private double ciLower;

    @Column(name = "ci_upper", nullable = false)
    private double ciUpper;

    @Column(name = "is_sufficient_sample", nullable = false)
    private boolean sufficientSample;

    /**
     * 기존 집계값에 신규 게임 결과를 누적한 새 인스턴스 반환 (불변 스타일 업데이트)
     * win_rate, ci_lower, ci_upper, pick_rate는 외부에서 재계산 후 별도 호출로 세팅 필요
     */
    public DuoPairStats addGames(int additionalGames, int additionalWins) {
        return DuoPairStats.builder()
                .patch(this.patch)
                .tier(this.tier)
                .adcChampionId(this.adcChampionId)
                .supportChampionId(this.supportChampionId)
                .games(this.games + additionalGames)
                .wins(this.wins + additionalWins)
                .winRate(this.winRate)
                .pickRate(this.pickRate)
                .ciLower(this.ciLower)
                .ciUpper(this.ciUpper)
                .sufficientSample(this.sufficientSample)
                .build();
    }

    public DuoPairStats withComputedStats(double winRate, double pickRate,
                                          double ciLower, double ciUpper,
                                          boolean sufficientSample) {
        return DuoPairStats.builder()
                .patch(this.patch)
                .tier(this.tier)
                .adcChampionId(this.adcChampionId)
                .supportChampionId(this.supportChampionId)
                .games(this.games)
                .wins(this.wins)
                .winRate(winRate)
                .pickRate(pickRate)
                .ciLower(ciLower)
                .ciUpper(ciUpper)
                .sufficientSample(sufficientSample)
                .build();
    }
}
