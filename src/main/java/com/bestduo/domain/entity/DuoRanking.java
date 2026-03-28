package com.bestduo.domain.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * 사전 계산된 바텀 듀오 랭킹
 *
 * RankingComputerJob이 (patch, tier) 단위로 staging 테이블에 먼저 쓰고
 * atomic swap으로 이 테이블에 반영함 (라이브 트래픽 읽기 중단 방지)
 *
 * win_rate, pick_rate, games, ci_lower는 duo_pair_stats에서 비정규화.
 * sort=WIN_RATE / sort=PICK_RATE 쿼리 시 JOIN 없이 이 테이블만 사용.
 *
 * tier_grade: 0=S, 1=A, 2=B, 3=C, 4=D
 */
@Entity
@Table(name = "duo_ranking")
@IdClass(DuoRankingId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class DuoRanking {

    @Id
    @Column(name = "patch", nullable = false, length = 10)
    private String patch;

    @Id
    @Column(name = "tier", nullable = false, length = 20)
    private String tier;

    @Id
    @Column(name = "rank_position", nullable = false)
    private Integer rankPosition;

    @Column(name = "adc_champion_id", nullable = false)
    private Integer adcChampionId;

    @Column(name = "support_champion_id", nullable = false)
    private Integer supportChampionId;

    @Column(name = "score", nullable = false)
    private double score;

    // 0=S, 1=A, 2=B, 3=C, 4=D
    @Column(name = "tier_grade", nullable = false)
    private int tierGrade;

    // duo_pair_stats에서 비정규화 (sort 성능)
    @Column(name = "win_rate", nullable = false)
    private double winRate;

    @Column(name = "pick_rate", nullable = false)
    private double pickRate;

    @Column(name = "games", nullable = false)
    private int games;

    @Column(name = "ci_lower", nullable = false)
    private double ciLower;

    @Column(name = "is_sufficient_sample", nullable = false)
    private boolean sufficientSample;
}
