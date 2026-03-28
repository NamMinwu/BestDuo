package com.bestduo.domain.repository;

import com.bestduo.domain.entity.PatchTierMeta;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PatchTierMetaRepository extends JpaRepository<PatchTierMeta, PatchTierMeta.PatchTierMetaId> {

    Optional<PatchTierMeta> findByPatchAndTier(String patch, String tier);
}
