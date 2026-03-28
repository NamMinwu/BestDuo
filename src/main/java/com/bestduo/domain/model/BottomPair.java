package com.bestduo.domain.model;

import lombok.Value;

/**
 * 매치에서 추출한 바텀 페어 데이터 (불변 값 객체)
 *
 * 매치 1건에서 2개 추출됨:
 *   - 블루팀(teamId=100): BOTTOM + UTILITY
 *   - 레드팀(teamId=200): BOTTOM + UTILITY
 */
@Value
public class BottomPair {
    int adcChampionId;
    int supportChampionId;
    int teamId;
    boolean win;
    String patch;
    String matchId;
}
