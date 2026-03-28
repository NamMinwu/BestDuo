package com.bestduo.domain.entity;

import lombok.*;

import java.io.Serializable;

@Getter
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class DuoPairStatsId implements Serializable {
    private String patch;
    private String tier;
    private Integer adcChampionId;
    private Integer supportChampionId;
}
