package com.bestduo.scheduler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 패치별 전체 배치 파이프라인 실행 서비스
 *
 * 실행 순서:
 *   1. MatchCollectorJob    — 새 패치 매치 수집 + BFS 소환사 풀 확장
 *   2. TierVerificationJob  — 미검증/만료 소환사 티어 확인
 *   3. DuoPairAggregatorJob — 듀오 통계 집계 (duo_pair_stats)
 *   4. RankingComputerJob   — 랭킹 계산 → duo_ranking atomic swap
 *
 * 각 Job이 COMPLETED가 아니면 이후 단계를 실행하지 않음.
 * @Async로 호출되어 스케줄러 스레드를 블로킹하지 않음.
 */
@Slf4j
@Service
public class BatchPipelineService {

    private final JobLauncher jobLauncher;
    private final Job matchCollectorJob;
    private final Job tierVerificationJob;
    private final Job duoPairAggregatorJob;
    private final Job rankingComputerJob;

    public BatchPipelineService(
            JobLauncher jobLauncher,
            @Qualifier("matchCollectorBatchJob")    Job matchCollectorJob,
            @Qualifier("tierVerificationBatchJob")  Job tierVerificationJob,
            @Qualifier("duoPairAggregatorBatchJob") Job duoPairAggregatorJob,
            @Qualifier("rankingComputerBatchJob")   Job rankingComputerJob) {
        this.jobLauncher = jobLauncher;
        this.matchCollectorJob = matchCollectorJob;
        this.tierVerificationJob = tierVerificationJob;
        this.duoPairAggregatorJob = duoPairAggregatorJob;
        this.rankingComputerJob = rankingComputerJob;
    }

    @Async
    public void runPipeline(String patch) {
        log.info("[Pipeline] 시작 — patch={}", patch);
        long baseTime = System.currentTimeMillis();

        // Step 1: 매치 수집
        if (!run(matchCollectorJob, patch, baseTime, "MatchCollector")) return;

        // Step 2: 티어 검증
        if (!run(tierVerificationJob, patch, baseTime, "TierVerification")) return;

        // Step 3: 듀오 통계 집계
        if (!run(duoPairAggregatorJob, patch, baseTime, "DuoPairAggregator")) return;

        // Step 4: 랭킹 계산
        if (!run(rankingComputerJob, patch, baseTime, "RankingComputer")) return;

        log.info("[Pipeline] 완료 — patch={}", patch);
    }

    // true = COMPLETED, false = 실패 (이후 단계 중단)
    boolean run(Job job, String patch, long baseTime, String jobName) {
        try {
            JobParameters params = new JobParametersBuilder()
                    .addString("patch", patch)
                    .addLong("triggeredAt", baseTime)
                    .addString("step", jobName)
                    .toJobParameters();

            JobExecution execution = jobLauncher.run(job, params);
            BatchStatus status = execution.getStatus();

            if (status == BatchStatus.COMPLETED) {
                log.info("[Pipeline] {} 완료 — patch={}", jobName, patch);
                return true;
            } else {
                log.error("[Pipeline] {} 실패 (status={}) — 이후 단계 중단, patch={}", jobName, status, patch);
                return false;
            }
        } catch (Exception e) {
            log.error("[Pipeline] {} 실행 중 예외 — 이후 단계 중단, patch={}: {}", jobName, patch, e.getMessage());
            return false;
        }
    }
}
