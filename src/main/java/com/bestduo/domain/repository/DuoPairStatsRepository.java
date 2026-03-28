package com.bestduo.domain.repository;

import com.bestduo.domain.entity.DuoPairStats;
import com.bestduo.domain.entity.DuoPairStatsId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DuoPairStatsRepository extends JpaRepository<DuoPairStats, DuoPairStatsId> {

    List<DuoPairStats> findByPatchAndTier(String patch, String tier);

    Optional<DuoPairStats> findByPatchAndTierAndAdcChampionIdAndSupportChampionId(
            String patch, String tier, Integer adcChampionId, Integer supportChampionId);

    List<DuoPairStats> findByPatch(String patch);

    void deleteByPatchAndTier(String patch, String tier);
}
