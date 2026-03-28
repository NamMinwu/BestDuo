package com.bestduo.domain.repository;

import com.bestduo.domain.entity.MatchRaw;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MatchRawRepository extends JpaRepository<MatchRaw, String> {

    boolean existsByMatchId(String matchId);

    List<MatchRaw> findByPatchAndProcessedFalse(String patch);
}
