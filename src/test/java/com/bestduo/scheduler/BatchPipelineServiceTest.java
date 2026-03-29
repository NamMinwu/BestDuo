package com.bestduo.scheduler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.launch.JobLauncher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BatchPipelineServiceTest {

    @Mock private JobLauncher jobLauncher;
    @Mock private Job matchCollectorJob;
    @Mock private Job tierVerificationJob;
    @Mock private Job duoPairAggregatorJob;
    @Mock private Job rankingComputerJob;
    @Mock private JobExecution completedExecution;
    @Mock private JobExecution failedExecution;

    private BatchPipelineService service;

    @BeforeEach
    void setUp() {
        service = new BatchPipelineService(
                jobLauncher,
                matchCollectorJob,
                tierVerificationJob,
                duoPairAggregatorJob,
                rankingComputerJob
        );
        lenient().when(completedExecution.getStatus()).thenReturn(BatchStatus.COMPLETED);
        lenient().when(failedExecution.getStatus()).thenReturn(BatchStatus.FAILED);
    }

    // ─── 정상 흐름 ────────────────────────────────────────────────────────────

    @Test
    void 전체_파이프라인_순서대로_실행() throws Exception {
        when(jobLauncher.run(any(), any())).thenReturn(completedExecution);

        boolean r1 = service.run(matchCollectorJob,    "15.7", 0L, "MatchCollector");
        boolean r2 = service.run(tierVerificationJob,  "15.7", 0L, "TierVerification");
        boolean r3 = service.run(duoPairAggregatorJob, "15.7", 0L, "DuoPairAggregator");
        boolean r4 = service.run(rankingComputerJob,   "15.7", 0L, "RankingComputer");

        assertThat(r1).isTrue();
        assertThat(r2).isTrue();
        assertThat(r3).isTrue();
        assertThat(r4).isTrue();
        verify(jobLauncher, times(4)).run(any(), any());
    }

    // ─── 실패 시 중단 ─────────────────────────────────────────────────────────

    @Test
    void MatchCollector_실패시_이후_단계_미실행() throws Exception {
        when(jobLauncher.run(eq(matchCollectorJob), any())).thenReturn(failedExecution);

        boolean result = service.run(matchCollectorJob, "15.7", 0L, "MatchCollector");

        assertThat(result).isFalse();
        verify(jobLauncher, times(1)).run(any(), any());
    }

    @Test
    void TierVerification_실패시_Aggregator_미실행() throws Exception {
        when(jobLauncher.run(eq(matchCollectorJob), any())).thenReturn(completedExecution);
        when(jobLauncher.run(eq(tierVerificationJob), any())).thenReturn(failedExecution);

        service.run(matchCollectorJob,   "15.7", 0L, "MatchCollector");
        boolean result = service.run(tierVerificationJob, "15.7", 0L, "TierVerification");

        assertThat(result).isFalse();
        verify(jobLauncher, never()).run(eq(duoPairAggregatorJob), any());
    }

    @Test
    void DuoPairAggregator_실패시_RankingComputer_미실행() throws Exception {
        when(jobLauncher.run(eq(matchCollectorJob), any())).thenReturn(completedExecution);
        when(jobLauncher.run(eq(tierVerificationJob), any())).thenReturn(completedExecution);
        when(jobLauncher.run(eq(duoPairAggregatorJob), any())).thenReturn(failedExecution);

        service.run(matchCollectorJob,    "15.7", 0L, "MatchCollector");
        service.run(tierVerificationJob,  "15.7", 0L, "TierVerification");
        boolean result = service.run(duoPairAggregatorJob, "15.7", 0L, "DuoPairAggregator");

        assertThat(result).isFalse();
        verify(jobLauncher, never()).run(eq(rankingComputerJob), any());
    }

    @Test
    void JobLauncher_예외시_false_반환() throws Exception {
        when(jobLauncher.run(any(), any())).thenThrow(new RuntimeException("DB 연결 실패"));

        boolean result = service.run(matchCollectorJob, "15.7", 0L, "MatchCollector");

        assertThat(result).isFalse();
    }

}
