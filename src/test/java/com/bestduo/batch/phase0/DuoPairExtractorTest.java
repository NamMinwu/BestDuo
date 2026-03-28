package com.bestduo.batch.phase0;

import com.bestduo.domain.model.BottomPair;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DuoPairExtractorTest {

    private DuoPairExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new DuoPairExtractor(new ObjectMapper());
    }

    // ─── extractPatch ────────────────────────────────────────────────────────

    @Test
    void extractPatch_정상버전() {
        assertThat(extractor.extractPatch("15.6.123.4567")).isEqualTo("15.6");
    }

    @Test
    void extractPatch_두자리만() {
        assertThat(extractor.extractPatch("15.6")).isEqualTo("15.6");
    }

    @Test
    void extractPatch_null이면_unknown() {
        assertThat(extractor.extractPatch(null)).isEqualTo("unknown");
    }

    @Test
    void extractPatch_빈문자열이면_unknown() {
        assertThat(extractor.extractPatch("")).isEqualTo("unknown");
    }

    @Test
    void extractPatch_점없으면_원문반환() {
        assertThat(extractor.extractPatch("15")).isEqualTo("15");
    }

    // ─── extract (full JSON parsing) ─────────────────────────────────────────

    @Test
    void extract_정상매치_2쌍추출() {
        String json = buildMatchJson(
                participant(100, "BOTTOM", 235, true),
                participant(100, "UTILITY", 412, true),
                participant(100, "TOP", 10, true),
                participant(100, "JUNGLE", 11, true),
                participant(100, "MIDDLE", 12, true),
                participant(200, "BOTTOM", 51, false),
                participant(200, "UTILITY", 99, false),
                participant(200, "TOP", 20, false),
                participant(200, "JUNGLE", 21, false),
                participant(200, "MIDDLE", 22, false)
        );

        List<BottomPair> pairs = extractor.extract(json);

        assertThat(pairs).hasSize(2);

        BottomPair blue = pairs.stream().filter(p -> p.getTeamId() == 100).findFirst().orElseThrow();
        assertThat(blue.getAdcChampionId()).isEqualTo(235);
        assertThat(blue.getSupportChampionId()).isEqualTo(412);
        assertThat(blue.isWin()).isTrue();
        assertThat(blue.getPatch()).isEqualTo("15.6");
        assertThat(blue.getMatchId()).isEqualTo("KR_123456");

        BottomPair red = pairs.stream().filter(p -> p.getTeamId() == 200).findFirst().orElseThrow();
        assertThat(red.getAdcChampionId()).isEqualTo(51);
        assertThat(red.getSupportChampionId()).isEqualTo(99);
        assertThat(red.isWin()).isFalse();
    }

    @Test
    void extract_teamPosition누락시_해당팀_스킵() {
        String json = buildMatchJson(
                // 블루팀: teamPosition 없음
                participantNoPosition(100, 235, true),
                participant(100, "UTILITY", 412, true),
                participant(100, "TOP", 10, true),
                participant(100, "JUNGLE", 11, true),
                participant(100, "MIDDLE", 12, true),
                // 레드팀: 정상
                participant(200, "BOTTOM", 51, false),
                participant(200, "UTILITY", 99, false),
                participant(200, "TOP", 20, false),
                participant(200, "JUNGLE", 21, false),
                participant(200, "MIDDLE", 22, false)
        );

        List<BottomPair> pairs = extractor.extract(json);

        // 블루팀 스킵, 레드팀만 추출
        assertThat(pairs).hasSize(1);
        assertThat(pairs.get(0).getTeamId()).isEqualTo(200);
    }

    @Test
    void extract_BOTTOM중복시_해당팀_스킵() {
        String json = buildMatchJson(
                participant(100, "BOTTOM", 235, true),
                participant(100, "BOTTOM", 67, true),  // 중복
                participant(100, "UTILITY", 412, true),
                participant(100, "TOP", 10, true),
                participant(100, "JUNGLE", 11, true),
                participant(200, "BOTTOM", 51, false),
                participant(200, "UTILITY", 99, false),
                participant(200, "TOP", 20, false),
                participant(200, "JUNGLE", 21, false),
                participant(200, "MIDDLE", 22, false)
        );

        List<BottomPair> pairs = extractor.extract(json);

        assertThat(pairs).hasSize(1);
        assertThat(pairs.get(0).getTeamId()).isEqualTo(200);
    }

    @Test
    void extract_UTILITY중복시_해당팀_스킵() {
        String json = buildMatchJson(
                participant(100, "BOTTOM", 235, true),
                participant(100, "UTILITY", 412, true),
                participant(100, "UTILITY", 26, true),  // 중복
                participant(100, "TOP", 10, true),
                participant(100, "JUNGLE", 11, true),
                participant(200, "BOTTOM", 51, false),
                participant(200, "UTILITY", 99, false),
                participant(200, "TOP", 20, false),
                participant(200, "JUNGLE", 21, false),
                participant(200, "MIDDLE", 22, false)
        );

        List<BottomPair> pairs = extractor.extract(json);

        assertThat(pairs).hasSize(1);
        assertThat(pairs.get(0).getTeamId()).isEqualTo(200);
    }

    @Test
    void extract_BOTTOM없으면_해당팀_스킵() {
        String json = buildMatchJson(
                // 블루팀: BOTTOM 없음
                participant(100, "UTILITY", 412, true),
                participant(100, "TOP", 10, true),
                participant(100, "JUNGLE", 11, true),
                participant(100, "MIDDLE", 12, true),
                participant(100, "MIDDLE", 13, true),  // 5번째 자리 채우기
                participant(200, "BOTTOM", 51, false),
                participant(200, "UTILITY", 99, false),
                participant(200, "TOP", 20, false),
                participant(200, "JUNGLE", 21, false),
                participant(200, "MIDDLE", 22, false)
        );

        List<BottomPair> pairs = extractor.extract(json);

        assertThat(pairs).hasSize(1);
        assertThat(pairs.get(0).getTeamId()).isEqualTo(200);
    }

    @Test
    void extract_UTILITY없으면_해당팀_스킵() {
        String json = buildMatchJson(
                participant(100, "BOTTOM", 235, true),
                // 블루팀: UTILITY 없음
                participant(100, "TOP", 10, true),
                participant(100, "JUNGLE", 11, true),
                participant(100, "MIDDLE", 12, true),
                participant(100, "MIDDLE", 13, true),
                participant(200, "BOTTOM", 51, false),
                participant(200, "UTILITY", 99, false),
                participant(200, "TOP", 20, false),
                participant(200, "JUNGLE", 21, false),
                participant(200, "MIDDLE", 22, false)
        );

        List<BottomPair> pairs = extractor.extract(json);

        assertThat(pairs).hasSize(1);
        assertThat(pairs.get(0).getTeamId()).isEqualTo(200);
    }

    @Test
    void extract_잘못된JSON이면_빈리스트반환() {
        List<BottomPair> pairs = extractor.extract("this is not json");
        assertThat(pairs).isEmpty();
    }

    @Test
    void extract_양팀모두오염이면_빈리스트반환() {
        String json = buildMatchJson(
                participantNoPosition(100, 235, true),
                participant(100, "UTILITY", 412, true),
                participant(100, "TOP", 10, true),
                participant(100, "JUNGLE", 11, true),
                participant(100, "MIDDLE", 12, true),
                participantNoPosition(200, 51, false),
                participant(200, "UTILITY", 99, false),
                participant(200, "TOP", 20, false),
                participant(200, "JUNGLE", 21, false),
                participant(200, "MIDDLE", 22, false)
        );

        List<BottomPair> pairs = extractor.extract(json);
        assertThat(pairs).isEmpty();
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private String buildMatchJson(String... participants) {
        String joined = String.join(",", participants);
        return """
                {
                  "metadata": { "matchId": "KR_123456" },
                  "info": {
                    "gameVersion": "15.6.523.1234",
                    "participants": [%s]
                  }
                }
                """.formatted(joined);
    }

    private String participant(int teamId, String position, int championId, boolean win) {
        return """
                {"teamId":%d,"teamPosition":"%s","championId":%d,"win":%b}
                """.formatted(teamId, position, championId, win).strip();
    }

    private String participantNoPosition(int teamId, int championId, boolean win) {
        return """
                {"teamId":%d,"teamPosition":"","championId":%d,"win":%b}
                """.formatted(teamId, championId, win).strip();
    }
}
