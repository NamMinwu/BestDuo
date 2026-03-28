package com.bestduo.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 챔피언 메타 정보 (Data Dragon champion.json 동기화)
 *
 * champion_id: Riot champion key 숫자 (e.g. 21 = Miss Fortune)
 * key: Data Dragon 내부 식별자 (e.g. "MissFortune") — CDN 이미지 경로 구성에 사용
 * image_url: 완성된 아이콘 URL (Data Dragon CDN)
 */
@Entity
@Table(name = "champion_meta")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class ChampionMeta {

    @Id
    @Column(name = "champion_id", nullable = false)
    private Integer championId;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    // Data Dragon 내부 키 (e.g. "MissFortune")
    @Column(name = "key", nullable = false, length = 20)
    private String key;

    @Column(name = "image_url", length = 200)
    private String imageUrl;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public void update(String name, String key, String imageUrl) {
        this.name = name;
        this.key = key;
        this.imageUrl = imageUrl;
        this.updatedAt = LocalDateTime.now();
    }
}
