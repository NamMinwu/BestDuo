package com.bestduo.calculator;

import org.springframework.stereotype.Component;

/**
 * Wilson score confidence interval (하한) 계산기
 *
 * 공식: Newcombe (1998) Wilson score interval
 *
 *   lower = (p̂ + z²/2n - z√(p̂(1-p̂)/n + z²/4n²)) / (1 + z²/n)
 *
 * 여기서:
 *   p̂ = wins / games (관찰 승률)
 *   z  = 1.96 (95% 신뢰구간)
 *   n  = games
 *
 * 주의: 단순 근사식(p̂ - z√(p̂(1-p̂)/n)) 사용 금지.
 * 소표본(n=30)에서 오차가 유의미하게 발생함.
 */
@Component
public class WilsonScoreCalculator {

    private static final double Z = 1.96;  // 95% 신뢰구간
    private static final double Z_SQUARED = Z * Z;

    /**
     * Wilson score 하한 계산
     *
     * @param wins  승리 횟수
     * @param games 총 게임 수
     * @return Wilson score lower bound (0.0 ~ 1.0)
     * @throws IllegalArgumentException games <= 0 또는 wins > games 또는 wins < 0
     */
    public double calculateLower(int wins, int games) {
        if (games <= 0) {
            throw new IllegalArgumentException("games는 0보다 커야 합니다: " + games);
        }
        if (wins < 0) {
            throw new IllegalArgumentException("wins는 0 이상이어야 합니다: " + wins);
        }
        if (wins > games) {
            throw new IllegalArgumentException("wins(" + wins + ")가 games(" + games + ")보다 클 수 없습니다");
        }

        double p = (double) wins / games;
        double n = games;

        double numerator = p + Z_SQUARED / (2 * n)
                - Z * Math.sqrt(p * (1 - p) / n + Z_SQUARED / (4 * n * n));
        double denominator = 1 + Z_SQUARED / n;

        return numerator / denominator;
    }

    /**
     * Wilson score 상한 계산
     */
    public double calculateUpper(int wins, int games) {
        if (games <= 0) {
            throw new IllegalArgumentException("games는 0보다 커야 합니다: " + games);
        }
        if (wins < 0) {
            throw new IllegalArgumentException("wins는 0 이상이어야 합니다: " + wins);
        }
        if (wins > games) {
            throw new IllegalArgumentException("wins(" + wins + ")가 games(" + games + ")보다 클 수 없습니다");
        }

        double p = (double) wins / games;
        double n = games;

        double numerator = p + Z_SQUARED / (2 * n)
                + Z * Math.sqrt(p * (1 - p) / n + Z_SQUARED / (4 * n * n));
        double denominator = 1 + Z_SQUARED / n;

        return numerator / denominator;
    }
}
