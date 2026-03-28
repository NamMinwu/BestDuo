package com.bestduo.client;

import com.google.common.util.concurrent.RateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Riot API 클라이언트 — 복수 API 키 round-robin + Proactive Rate Limiting
 *
 * 전략: 429가 뜨면 developer 키 재발급 필요 → 429 자체를 원천 차단
 * Guava RateLimiter(0.7 req/s) per key: 100 req/2min 한도의 85%
 *
 * Round-robin:
 *   AtomicInteger로 키 인덱스 순환
 *   RateLimiter.acquire()가 필요 시 블로킹 → 429 발생 방지
 *
 * KR 서버 엔드포인트: https://kr.api.riotgames.com
 * 매치 데이터 엔드포인트: https://asia.api.riotgames.com
 */
@Slf4j
@Component
public class RiotApiClient {

    private final List<String> apiKeys;
    private final Map<String, RateLimiter> rateLimiters;
    private final AtomicInteger keyIndex = new AtomicInteger(0);
    private final WebClient krClient;
    private final WebClient asiaClient;

    public RiotApiClient(
            @Value("${riot.api.keys}") String keysConfig,
            @Value("${riot.api.rate-limit-per-second:0.7}") double rateLimitPerSecond,
            @Value("${riot.api.base-url}") String baseUrl,
            @Value("${riot.api.match-base-url}") String matchBaseUrl) {

        this.apiKeys = Arrays.asList(keysConfig.split(","));
        this.rateLimiters = apiKeys.stream()
                .collect(Collectors.toMap(
                        key -> key,
                        key -> RateLimiter.create(rateLimitPerSecond)
                ));

        this.krClient = WebClient.builder().baseUrl(baseUrl).build();
        this.asiaClient = WebClient.builder().baseUrl(matchBaseUrl).build();

        log.info("RiotApiClient 초기화: {}개 키, {}req/s per key", apiKeys.size(), rateLimitPerSecond);
    }

    /**
     * 다음 사용 가능한 API 키를 반환 (round-robin + rate limit 적용)
     * RateLimiter.acquire()가 필요 시 블로킹 — 429 원천 차단
     */
    private String acquireKey() {
        int index = Math.abs(keyIndex.getAndIncrement() % apiKeys.size());
        String key = apiKeys.get(index);
        rateLimiters.get(key).acquire();  // 필요 시 블로킹
        return key;
    }

    /**
     * KR 챌린저 래더 조회
     */
    public String fetchChallengerLadder() {
        String key = acquireKey();
        return krClient.get()
                .uri("/lol/league/v4/challengerleagues/by-queue/RANKED_SOLO_5x5")
                .header("X-Riot-Token", key)
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    /**
     * KR 그랜드마스터 래더 조회
     */
    public String fetchGrandmasterLadder() {
        String key = acquireKey();
        return krClient.get()
                .uri("/lol/league/v4/grandmasterleagues/by-queue/RANKED_SOLO_5x5")
                .header("X-Riot-Token", key)
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    /**
     * KR 마스터 래더 조회
     */
    public String fetchMasterLadder() {
        String key = acquireKey();
        return krClient.get()
                .uri("/lol/league/v4/masterleagues/by-queue/RANKED_SOLO_5x5")
                .header("X-Riot-Token", key)
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    /**
     * LEAGUE-V4 페이지네이션 (에메랄드/다이아)
     * 빈 배열 반환 시 페이지 순회 종료
     */
    public String fetchLeagueEntries(String tier, String division, int page) {
        String key = acquireKey();
        return krClient.get()
                .uri("/lol/league/v4/entries/RANKED_SOLO_5x5/{tier}/{division}?page={page}",
                        tier, division, page)
                .header("X-Riot-Token", key)
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    /**
     * 소환사 티어 확인 (BFS TierVerificationJob용)
     */
    public String fetchSummonerLeagueEntry(String encryptedSummonerId) {
        String key = acquireKey();
        return krClient.get()
                .uri("/lol/league/v4/entries/by-summoner/{encryptedSummonerId}", encryptedSummonerId)
                .header("X-Riot-Token", key)
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    /**
     * 소환사 최근 매치 ID 목록 (솔로랭크, 최근 N건)
     */
    public String fetchMatchIds(String puuid, int count) {
        String key = acquireKey();
        return asiaClient.get()
                .uri("/lol/match/v5/matches/by-puuid/{puuid}/ids?queue=420&count={count}",
                        puuid, count)
                .header("X-Riot-Token", key)
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    /**
     * 매치 상세 조회
     */
    public String fetchMatchDetail(String matchId) {
        String key = acquireKey();
        return asiaClient.get()
                .uri("/lol/match/v5/matches/{matchId}", matchId)
                .header("X-Riot-Token", key)
                .retrieve()
                .bodyToMono(String.class)
                .onErrorResume(WebClientResponseException.class, ex -> {
                    log.warn("매치 {} 조회 실패: {} {}", matchId, ex.getStatusCode(), ex.getMessage());
                    return reactor.core.publisher.Mono.empty();
                })
                .block();
    }
}
