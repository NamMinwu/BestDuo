package com.bestduo.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "match_raw")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class MatchRaw {

    @Id
    @Column(name = "match_id", nullable = false, length = 20)
    private String matchId;

    @Column(name = "patch", nullable = false, length = 10)
    private String patch;

    // raw_json은 영구 보존 (NULL화 없음 — 재처리 가능성 유지)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_json", columnDefinition = "jsonb")
    private String rawJson;

    @Column(name = "processed", nullable = false)
    @Builder.Default
    private boolean processed = false;

    @Column(name = "collected_at", nullable = false)
    private LocalDateTime collectedAt;

    public void markProcessed() {
        this.processed = true;
    }
}
