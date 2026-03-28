package com.bestduo.domain.repository;

import com.bestduo.domain.entity.ChampionMeta;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChampionMetaRepository extends JpaRepository<ChampionMeta, Integer> {

    Optional<ChampionMeta> findByKey(String key);

    List<ChampionMeta> findAllByOrderByNameAsc();
}
