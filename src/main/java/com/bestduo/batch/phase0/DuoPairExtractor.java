package com.bestduo.batch.phase0;

import com.bestduo.domain.model.BottomPair;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 매치 JSON에서 바텀 듀오 페어를 추출
 *
 * 추출 기준:
 *   - teamPosition == "BOTTOM" (원딜) + "UTILITY" (서포터)
 *   - 같은 teamId (100=블루, 200=레드)
 *   - 매치당 최대 2쌍 추출 (팀당 1쌍)
 *
 * 스킵 조건 (오염 데이터):
 *   - teamPosition 누락 또는 빈 문자열
 *   - 같은 팀에 BOTTOM 또는 UTILITY가 2명 이상
 *   - adc/support 중 한 명만 있는 경우
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DuoPairExtractor {

    private final ObjectMapper objectMapper;

    /**
     * 매치 JSON 문자열에서 바텀 페어 목록 추출
     *
     * @param matchJson Riot match-v5 API 응답 JSON 문자열
     * @return 추출된 바텀 페어 목록 (정상이면 2개, 오염 시 0~1개)
     */
    public List<BottomPair> extract(String matchJson) {
        List<BottomPair> pairs = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(matchJson);
            String matchId = root.path("metadata").path("matchId").asText();
            String gameVersion = root.path("info").path("gameVersion").asText();
            String patch = extractPatch(gameVersion);
            JsonNode participants = root.path("info").path("participants");

            // 팀별 페어 추출
            extractPairForTeam(participants, 100, matchId, patch).ifPresent(pairs::add);
            extractPairForTeam(participants, 200, matchId, patch).ifPresent(pairs::add);

        } catch (Exception e) {
            log.warn("매치 JSON 파싱 실패: {}", e.getMessage());
        }
        return pairs;
    }

    /**
     * 특정 팀의 바텀 페어 추출
     */
    private Optional<BottomPair> extractPairForTeam(JsonNode participants, int teamId,
                                                      String matchId, String patch) {
        Integer adcChampionId = null;
        Integer supportChampionId = null;
        boolean win = false;

        for (JsonNode p : participants) {
            if (p.path("teamId").asInt() != teamId) continue;

            String position = p.path("teamPosition").asText("");
            if (position.isEmpty()) {
                log.debug("매치 {} 팀 {} teamPosition 누락 → 스킵", matchId, teamId);
                return Optional.empty();
            }

            if ("BOTTOM".equals(position)) {
                if (adcChampionId != null) {
                    log.debug("매치 {} 팀 {} BOTTOM 중복 → 스킵", matchId, teamId);
                    return Optional.empty();
                }
                adcChampionId = p.path("championId").asInt();
                win = p.path("win").asBoolean();
            } else if ("UTILITY".equals(position)) {
                if (supportChampionId != null) {
                    log.debug("매치 {} 팀 {} UTILITY 중복 → 스킵", matchId, teamId);
                    return Optional.empty();
                }
                supportChampionId = p.path("championId").asInt();
            }
        }

        if (adcChampionId == null || supportChampionId == null) {
            log.debug("매치 {} 팀 {} 바텀 페어 불완전 → 스킵", matchId, teamId);
            return Optional.empty();
        }

        return Optional.of(new BottomPair(adcChampionId, supportChampionId, teamId, win, patch, matchId));
    }

    /**
     * gameVersion("15.6.123.4567") → patch("15.6")
     */
    String extractPatch(String gameVersion) {
        if (gameVersion == null || gameVersion.isEmpty()) return "unknown";
        String[] parts = gameVersion.split("\\.");
        if (parts.length < 2) return gameVersion;
        return parts[0] + "." + parts[1];
    }
}
