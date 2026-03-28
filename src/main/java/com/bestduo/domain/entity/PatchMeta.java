package com.bestduo.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "patch_meta")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class PatchMeta {

    @Id
    @Column(name = "patch", nullable = false, length = 10)
    private String patch;

    @Column(name = "released_at")
    private LocalDateTime releasedAt;

    // 항상 최신 2개 패치만 TRUE
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    public void deactivate() {
        this.active = false;
    }
}
