package com.bestduo.domain.repository;

import com.bestduo.domain.entity.DuoRanking;
import com.bestduo.domain.entity.DuoRankingId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DuoRankingRepository extends JpaRepository<DuoRanking, DuoRankingId> {

    List<DuoRanking> findByPatchAndTierOrderByRankPositionAsc(String patch, String tier);

    List<DuoRanking> findByPatchAndTierOrderByWinRateDesc(String patch, String tier);

    List<DuoRanking> findByPatchAndTierOrderByPickRateDesc(String patch, String tier);

    Optional<DuoRanking> findByPatchAndTierAndAdcChampionIdAndSupportChampionId(
            String patch, String tier, Integer adcChampionId, Integer supportChampionId);

    void deleteByPatchAndTier(String patch, String tier);
}
