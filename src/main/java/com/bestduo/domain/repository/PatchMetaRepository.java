package com.bestduo.domain.repository;

import com.bestduo.domain.entity.PatchMeta;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PatchMetaRepository extends JpaRepository<PatchMeta, String> {

    List<PatchMeta> findByActiveTrueOrderByPatchDesc();

    boolean existsByPatch(String patch);
}
