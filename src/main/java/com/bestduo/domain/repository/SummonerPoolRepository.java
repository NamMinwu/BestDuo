package com.bestduo.domain.repository;

import com.bestduo.domain.entity.SummonerPool;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface SummonerPoolRepository extends JpaRepository<SummonerPool, String> {

    List<SummonerPool> findByVerifiedTrue();

    List<SummonerPool> findByVerifiedFalse();

    List<SummonerPool> findByTierInAndVerifiedTrue(List<String> tiers);

    // 미검증(신규 BFS 발견) + 검증 만료(tierVerifiedAt이 cutoff보다 오래됨) 대상
    @Query("SELECT s FROM SummonerPool s WHERE s.verified = false OR s.tierVerifiedAt < :cutoff")
    List<SummonerPool> findNeedingVerification(@Param("cutoff") LocalDateTime cutoff);
}
