package com.bestduo.calculator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class WilsonScoreCalculatorTest {

    private WilsonScoreCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new WilsonScoreCalculator();
    }

    @Test
    @DisplayName("정상 케이스: n=100, wins=55 → lower가 원시 승률보다 낮아야 함")
    void calculateLower_normalCase() {
        double lower = calculator.calculateLower(55, 100);
        double rawWinRate = 55.0 / 100;

        assertThat(lower).isLessThan(rawWinRate);
        assertThat(lower).isBetween(0.45, 0.55);  // 대략 0.452 예상
    }

    @Test
    @DisplayName("소표본 n=30: Wilson lower가 근사식보다 보수적이어야 함")
    void calculateLower_smallSample() {
        // n=30, win_rate=0.55 → wilson_lower ≈ 0.37 (설계 문서 경고값)
        double lower = calculator.calculateLower(17, 30);

        assertThat(lower).isLessThan(0.55);
        assertThat(lower).isGreaterThan(0.30);
        // 근사식 값 (설계 문서 언급): ~0.37
        assertThat(lower).isBetween(0.35, 0.42);
    }

    @Test
    @DisplayName("wins=0: lower가 0에 가까워야 함 (ZeroDivision 없음)")
    void calculateLower_zeroWins() {
        double lower = calculator.calculateLower(0, 100);

        assertThat(lower).isGreaterThanOrEqualTo(0.0);
        assertThat(lower).isLessThan(0.05);
    }

    @Test
    @DisplayName("wins=games (100% 승률): lower가 0.9 이상이어야 함")
    void calculateLower_perfectWinRate() {
        double lower = calculator.calculateLower(100, 100);

        assertThat(lower).isGreaterThan(0.9);
        assertThat(lower).isLessThanOrEqualTo(1.0);
    }

    @Test
    @DisplayName("games=0: IllegalArgumentException 발생")
    void calculateLower_zeroGames_throwsException() {
        assertThatThrownBy(() -> calculator.calculateLower(0, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("games는 0보다 커야 합니다");
    }

    @Test
    @DisplayName("wins > games: IllegalArgumentException 발생")
    void calculateLower_winsExceedsGames_throwsException() {
        assertThatThrownBy(() -> calculator.calculateLower(101, 100))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("wins");
    }

    @Test
    @DisplayName("wins < 0: IllegalArgumentException 발생")
    void calculateLower_negativeWins_throwsException() {
        assertThatThrownBy(() -> calculator.calculateLower(-1, 100))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("wins는 0 이상이어야 합니다");
    }

    @Test
    @DisplayName("lower는 항상 upper보다 작아야 함")
    void lowerAlwaysLessThanUpper() {
        int[][] cases = {{55, 100}, {17, 30}, {1, 10}, {99, 100}};
        for (int[] c : cases) {
            double lower = calculator.calculateLower(c[0], c[1]);
            double upper = calculator.calculateUpper(c[0], c[1]);
            assertThat(lower).isLessThan(upper);
        }
    }

    @Test
    @DisplayName("n=1000: lower가 원시 승률에 가까워야 함 (대표본 수렴)")
    void calculateLower_largeSample_convergesToRawRate() {
        double rawRate = 550.0 / 1000;
        double lower = calculator.calculateLower(550, 1000);

        assertThat(lower).isCloseTo(rawRate, within(0.04));
    }
}
