package com.bestduo.api.controller;

import com.bestduo.scheduler.BatchPipelineService;
import com.bestduo.scheduler.PatchDetectionScheduler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 어드민 엔드포인트.
 *
 * X-Admin-Key 헤더로 인증. ADMIN_SECRET_KEY 환경변수 미설정 시 모든 요청 거부.
 * 운영 환경: ADMIN_SECRET_KEY=<랜덤 시크릿> 환경변수 필수.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final PatchDetectionScheduler patchDetectionScheduler;
    private final BatchPipelineService batchPipelineService;

    @Value("${admin.secret-key:}")
    private String secretKey;

    /** 패치 감지 스케줄러를 즉시 실행 (Data Dragon 조회 + 파이프라인 트리거) */
    @PostMapping("/trigger-patch-detection")
    public String triggerPatchDetection(@RequestHeader("X-Admin-Key") String key) {
        authenticate(key);
        patchDetectionScheduler.detectNewPatch();
        return "패치 감지 트리거 완료";
    }

    /** 특정 패치로 파이프라인 강제 실행 */
    @PostMapping("/trigger-pipeline")
    public String triggerPipeline(@RequestHeader("X-Admin-Key") String key,
                                  @RequestParam String patch) {
        authenticate(key);
        batchPipelineService.runPipeline(patch);
        return "파이프라인 트리거 완료: " + patch;
    }

    private void authenticate(String key) {
        if (secretKey == null || secretKey.isBlank()) {
            log.warn("[Admin] ADMIN_SECRET_KEY 미설정 — 요청 거부");
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Admin API 비활성화 (ADMIN_SECRET_KEY 미설정)");
        }
        if (!secretKey.equals(key)) {
            log.warn("[Admin] 잘못된 X-Admin-Key");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "인증 실패");
        }
    }
}
