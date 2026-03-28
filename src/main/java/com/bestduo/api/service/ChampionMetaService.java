package com.bestduo.api.service;

import com.bestduo.domain.entity.ChampionMeta;
import com.bestduo.domain.repository.ChampionMetaRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 챔피언 메타 서비스
 *
 * Data Dragon champion.json을 주기적으로 폴링해서 champion_meta 테이블을 동기화.
 * 패치마다 챔피언 이름/키/이미지가 바뀔 수 있으므로 upsert 방식으로 처리.
 *
 * 스케줄: 매일 오전 5시 (패치 감지 스케줄과 별도 — 챔피언 메타는 드물게 변경됨)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChampionMetaService {

    private final ChampionMetaRepository championMetaRepository;
    private final ObjectMapper objectMapper;

    private final WebClient webClient = WebClient.builder().build();

    @Value("${champion.ddragon-version:15.6.1}")
    private String ddragonVersion;

    @Value("${champion.ddragon-base-url:https://ddragon.leagueoflegends.com}")
    private String ddragonBaseUrl;

    /**
     * 매일 오전 5시 Data Dragon champion.json 동기화
     */
    @Scheduled(cron = "${champion.sync-cron:0 0 5 * * *}")
    public void scheduledSync() {
        log.info("챔피언 메타 동기화 시작 (버전: {})", ddragonVersion);
        int count = syncChampions(ddragonVersion);
        log.info("챔피언 메타 동기화 완료: {}개", count);
    }

    /**
     * 특정 버전의 champion.json으로 동기화
     *
     * @return 처리된 챔피언 수
     */
    @Transactional
    public int syncChampions(String version) {
        String url = ddragonBaseUrl + "/cdn/" + version + "/data/ko_KR/champion.json";

        String json;
        try {
            json = webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
        } catch (Exception e) {
            log.error("champion.json 조회 실패 (version={}): {}", version, e.getMessage());
            return 0;
        }

        if (json == null) return 0;
        return parseAndSave(json, version);
    }

    /**
     * champion.json 파싱 및 DB upsert (테스트 가능하도록 분리)
     */
    @Transactional
    int parseAndSave(String json, String version) {
        List<ChampionMeta> toSave = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode data = root.path("data");

            data.fields().forEachRemaining(entry -> {
                JsonNode champion = entry.getValue();
                int championId = champion.path("key").asInt();
                String name = champion.path("name").asText();
                String key = champion.path("id").asText();  // "MissFortune"
                String imageFile = champion.path("image").path("full").asText();
                String imageUrl = ddragonBaseUrl + "/cdn/" + version + "/img/champion/" + imageFile;

                var existing = championMetaRepository.findById(championId);
                if (existing.isPresent()) {
                    existing.get().update(name, key, imageUrl);
                    toSave.add(existing.get());
                } else {
                    toSave.add(ChampionMeta.builder()
                            .championId(championId)
                            .name(name)
                            .key(key)
                            .imageUrl(imageUrl)
                            .updatedAt(LocalDateTime.now())
                            .build());
                }
            });
        } catch (Exception e) {
            log.error("champion.json 파싱 실패: {}", e.getMessage());
            return 0;
        }

        championMetaRepository.saveAll(toSave);
        return toSave.size();
    }

    /**
     * 전체 챔피언 목록 조회 (이름 오름차순)
     */
    public List<ChampionMeta> findAll() {
        return championMetaRepository.findAllByOrderByNameAsc();
    }

    /**
     * 챔피언 ID로 조회
     */
    public java.util.Optional<ChampionMeta> findById(int championId) {
        return championMetaRepository.findById(championId);
    }
}
