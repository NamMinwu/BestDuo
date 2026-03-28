package com.bestduo.domain.repository;

import com.bestduo.domain.entity.SummonerPool;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SummonerPoolRepository extends JpaRepository<SummonerPool, String> {

    List<SummonerPool> findByVerifiedTrue();

    List<SummonerPool> findByVerifiedFalse();

    List<SummonerPool> findByTierInAndVerifiedTrue(List<String> tiers);
}
