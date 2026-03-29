package com.bestduo.api.controller;

import com.bestduo.scheduler.BatchPipelineService;
import com.bestduo.scheduler.PatchDetectionScheduler;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Phase 0 검증용 어드민 엔드포인트.
 * 프로덕션에서는 제거 또는 인증 추가 필요.
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final PatchDetectionScheduler patchDetectionScheduler;
    private final BatchPipelineService batchPipelineService;

    /** 패치 감지 스케줄러를 즉시 실행 (Data Dragon 조회 + 파이프라인 트리거) */
    @PostMapping("/trigger-patch-detection")
    public String triggerPatchDetection() {
        patchDetectionScheduler.detectNewPatch();
        return "패치 감지 트리거 완료";
    }

    /** 특정 패치로 파이프라인 강제 실행 */
    @PostMapping("/trigger-pipeline")
    public String triggerPipeline(@RequestParam String patch) {
        batchPipelineService.runPipeline(patch);
        return "파이프라인 트리거 완료: " + patch;
    }
}
