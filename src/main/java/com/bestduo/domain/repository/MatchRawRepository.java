package com.bestduo.domain.repository;

import com.bestduo.domain.entity.MatchRaw;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface MatchRawRepository extends JpaRepository<MatchRaw, String> {

    boolean existsByMatchId(String matchId);

    List<MatchRaw> findByPatchAndProcessedFalse(String patch);

    List<MatchRaw> findByProcessedFalse();

    @Modifying
    @Query("UPDATE MatchRaw m SET m.processed = true WHERE m.processed = false")
    int markAllProcessed();
}
