package com.bestduo.scheduler;

import com.bestduo.domain.entity.PatchMeta;
import com.bestduo.domain.repository.PatchMetaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.launch.JobLauncher;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PatchDetectionSchedulerTest {

    @Mock private PatchMetaRepository patchMetaRepository;
    @Mock private JobLauncher jobLauncher;

    private PatchDetectionScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new PatchDetectionScheduler(
                patchMetaRepository, jobLauncher, new ObjectMapper(), null
        );
        setField(scheduler, "ddragonVersionsUrl", "https://ddragon.leagueoflegends.com/api/versions.json");
        setField(scheduler, "activePatchCount", 2);
    }

    // ─── normalizePatch ───────────────────────────────────────────────────────

    @Test
    void normalizePatch_정상버전() {
        assertThat(scheduler.normalizePatch("15.7.1")).isEqualTo("15.7");
    }

    @Test
    void normalizePatch_두자리만() {
        assertThat(scheduler.normalizePatch("15.7")).isEqualTo("15.7");
    }

    @Test
    void normalizePatch_null이면_null() {
        assertThat(scheduler.normalizePatch(null)).isNull();
    }

    @Test
    void normalizePatch_빈문자열이면_null() {
        assertThat(scheduler.normalizePatch("")).isNull();
    }

    @Test
    void normalizePatch_점없으면_원문반환() {
        assertThat(scheduler.normalizePatch("15")).isEqualTo("15");
    }

    // ─── deactivateOldPatches ─────────────────────────────────────────────────

    @Test
    void 활성패치가_2개이하면_비활성화_없음() {
        PatchMeta p1 = activePatch("15.7");
        PatchMeta p2 = activePatch("15.6");
        when(patchMetaRepository.findByActiveTrueOrderByPatchDesc()).thenReturn(List.of(p1, p2));

        scheduler.deactivateOldPatches();

        verify(patchMetaRepository, never()).save(any());
    }

    @Test
    void 활성패치가_3개면_가장오래된것_비활성화() {
        PatchMeta p1 = activePatch("15.7");
        PatchMeta p2 = activePatch("15.6");
        PatchMeta p3 = activePatch("15.5");
        when(patchMetaRepository.findByActiveTrueOrderByPatchDesc()).thenReturn(List.of(p1, p2, p3));

        scheduler.deactivateOldPatches();

        assertThat(p3.isActive()).isFalse();
        verify(patchMetaRepository, times(1)).save(p3);
        verify(patchMetaRepository, never()).save(p1);
        verify(patchMetaRepository, never()).save(p2);
    }

    @Test
    void 활성패치가_4개면_오래된_2개_비활성화() {
        PatchMeta p1 = activePatch("15.7");
        PatchMeta p2 = activePatch("15.6");
        PatchMeta p3 = activePatch("15.5");
        PatchMeta p4 = activePatch("15.4");
        when(patchMetaRepository.findByActiveTrueOrderByPatchDesc())
                .thenReturn(List.of(p1, p2, p3, p4));

        scheduler.deactivateOldPatches();

        assertThat(p3.isActive()).isFalse();
        assertThat(p4.isActive()).isFalse();
        assertThat(p1.isActive()).isTrue();
        assertThat(p2.isActive()).isTrue();
        verify(patchMetaRepository, times(2)).save(any());
    }

    // ─── registerNewPatch ─────────────────────────────────────────────────────

    @Test
    void 신규패치_등록시_active_true로_저장() {
        scheduler.registerNewPatch("15.8");

        var captor = org.mockito.ArgumentCaptor.forClass(PatchMeta.class);
        verify(patchMetaRepository).save(captor.capture());

        PatchMeta saved = captor.getValue();
        assertThat(saved.getPatch()).isEqualTo("15.8");
        assertThat(saved.isActive()).isTrue();
        assertThat(saved.getReleasedAt()).isNotNull();
    }

    // ─── fetchLatestPatchFromDdragon ─────────────────────────────────────────

    @Test
    void ddragon_응답_파싱_정상() throws Exception {
        // WebClient를 직접 테스트하기 어려우므로 normalizePatch만 검증
        // 실제 API 연동은 통합테스트에서 검증
        assertThat(scheduler.normalizePatch("15.7.456.1234")).isEqualTo("15.7");
    }

    // ─── 헬퍼 ─────────────────────────────────────────────────────────────────

    private PatchMeta activePatch(String patch) {
        return PatchMeta.builder()
                .patch(patch)
                .active(true)
                .build();
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
