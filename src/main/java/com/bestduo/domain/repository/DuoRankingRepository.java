package com.bestduo.domain.repository;

import com.bestduo.domain.entity.DuoRanking;
import com.bestduo.domain.entity.DuoRankingId;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DuoRankingRepository extends JpaRepository<DuoRanking, DuoRankingId> {

    @Query("SELECT d FROM DuoRanking d WHERE d.patch = :patch AND d.tier = :tier " +
           "AND (:adcId IS NULL OR d.adcChampionId = :adcId) " +
           "AND (:supportId IS NULL OR d.supportChampionId = :supportId)")
    List<DuoRanking> findWithChampionFilter(
            @Param("patch") String patch, @Param("tier") String tier,
            @Param("adcId") Integer adcId, @Param("supportId") Integer supportId,
            Sort sort);

    Optional<DuoRanking> findByPatchAndTierAndAdcChampionIdAndSupportChampionId(
            String patch, String tier, Integer adcChampionId, Integer supportChampionId);

    void deleteByPatchAndTier(String patch, String tier);

    /**
     * Atomic swap Step 2: staging → duo_ranking INSERT SELECT
     * deleteByPatchAndTier + 이 메서드를 @Transactional 안에서 순서대로 호출
     */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(
            value = """
                    INSERT INTO duo_ranking
                    SELECT * FROM duo_ranking_staging WHERE patch = :patch AND tier = :tier
                    """,
            nativeQuery = true)
    void insertFromStaging(
            @Param("patch") String patch,
            @Param("tier") String tier);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(
            value = "DELETE FROM duo_ranking_staging WHERE patch = :patch AND tier = :tier",
            nativeQuery = true)
    void clearStaging(
            @Param("patch") String patch,
            @Param("tier") String tier);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(
            value = """
                    INSERT INTO duo_ranking_staging
                    (patch, tier, rank_position, adc_champion_id, support_champion_id,
                     score, tier_grade, win_rate, pick_rate, games, ci_lower, is_sufficient_sample)
                    VALUES
                    (:patch, :tier, :rankPosition, :adcChampionId, :supportChampionId,
                     :score, :tierGrade, :winRate, :pickRate, :games, :ciLower, :sufficientSample)
                    """,
            nativeQuery = true)
    void insertIntoStaging(
            @Param("patch") String patch,
            @Param("tier") String tier,
            @Param("rankPosition") int rankPosition,
            @Param("adcChampionId") int adcChampionId,
            @Param("supportChampionId") int supportChampionId,
            @Param("score") double score,
            @Param("tierGrade") int tierGrade,
            @Param("winRate") double winRate,
            @Param("pickRate") double pickRate,
            @Param("games") int games,
            @Param("ciLower") double ciLower,
            @Param("sufficientSample") boolean sufficientSample);
}
