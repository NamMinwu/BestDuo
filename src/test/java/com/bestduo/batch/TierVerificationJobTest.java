package com.bestduo.batch;

import com.bestduo.client.RiotApiClient;
import com.bestduo.domain.entity.SummonerPool;
import com.bestduo.domain.repository.SummonerPoolRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.item.ItemProcessor;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TierVerificationJobTest {

    @Mock
    private RiotApiClient riotApiClient;

    @Mock
    private SummonerPoolRepository summonerPoolRepository;

    private TierVerificationJob job;
    private ItemProcessor<SummonerPool, SummonerPool> processor;

    @BeforeEach
    void setUp() {
        job = new TierVerificationJob(null, null, riotApiClient, summonerPoolRepository, new ObjectMapper());
        processor = job.tierVerificationProcessor();
    }

    @Test
    void 에메랄드_플러스_소환사_verified_true_업데이트() throws Exception {
        SummonerPool summoner = unverified("puuid-1");

        when(riotApiClient.fetchSummonerByPuuid("puuid-1"))
                .thenReturn("{\"id\":\"summoner-id-1\"}");
        when(riotApiClient.fetchSummonerLeagueEntry("summoner-id-1"))
                .thenReturn(leagueEntry("DIAMOND"));

        SummonerPool result = processor.process(summoner);

        assertThat(result.getTier()).isEqualTo("DIAMOND");
        assertThat(result.isVerified()).isTrue();
    }

    @Test
    void 챌린저_소환사_verified_true() throws Exception {
        SummonerPool summoner = unverified("puuid-2");

        when(riotApiClient.fetchSummonerByPuuid("puuid-2"))
                .thenReturn("{\"id\":\"summoner-id-2\"}");
        when(riotApiClient.fetchSummonerLeagueEntry("summoner-id-2"))
                .thenReturn(leagueEntry("CHALLENGER"));

        SummonerPool result = processor.process(summoner);

        assertThat(result.getTier()).isEqualTo("CHALLENGER");
        assertThat(result.isVerified()).isTrue();
    }

    @Test
    void 솔로랭크_없으면_UNRANKED() throws Exception {
        SummonerPool summoner = unverified("puuid-3");

        when(riotApiClient.fetchSummonerByPuuid("puuid-3"))
                .thenReturn("{\"id\":\"summoner-id-3\"}");
        // FLEX만 있고 솔로랭크 없는 경우
        when(riotApiClient.fetchSummonerLeagueEntry("summoner-id-3"))
                .thenReturn("[{\"queueType\":\"RANKED_FLEX_SR\",\"tier\":\"GOLD\"}]");

        SummonerPool result = processor.process(summoner);

        assertThat(result.getTier()).isEqualTo("UNRANKED");
        assertThat(result.isVerified()).isTrue();
    }

    @Test
    void summonerId_빈값이면_UNRANKED() throws Exception {
        SummonerPool summoner = unverified("puuid-4");

        when(riotApiClient.fetchSummonerByPuuid("puuid-4"))
                .thenReturn("{\"id\":\"\"}");

        SummonerPool result = processor.process(summoner);

        assertThat(result.getTier()).isEqualTo("UNRANKED");
        assertThat(result.isVerified()).isTrue();
        verify(riotApiClient, never()).fetchSummonerLeagueEntry(anyString());
    }

    @Test
    void API_실패시_UNRANKED_처리() throws Exception {
        SummonerPool summoner = unverified("puuid-5");

        when(riotApiClient.fetchSummonerByPuuid("puuid-5"))
                .thenThrow(new RuntimeException("API timeout"));

        SummonerPool result = processor.process(summoner);

        assertThat(result.getTier()).isEqualTo("UNRANKED");
        assertThat(result.isVerified()).isTrue();
    }

    @Test
    void 소환사정보_null이면_UNRANKED() throws Exception {
        SummonerPool summoner = unverified("puuid-6");

        when(riotApiClient.fetchSummonerByPuuid("puuid-6"))
                .thenReturn(null);

        SummonerPool result = processor.process(summoner);

        assertThat(result.getTier()).isEqualTo("UNRANKED");
        assertThat(result.isVerified()).isTrue();
    }

    // ─── 헬퍼 ────────────────────────────────────────────────────────────────

    private SummonerPool unverified(String puuid) {
        return SummonerPool.builder()
                .puuid(puuid)
                .tier(null)
                .verified(false)
                .lastSeenAt(LocalDateTime.now())
                .build();
    }

    private String leagueEntry(String tier) {
        return "[{\"queueType\":\"RANKED_SOLO_5x5\",\"tier\":\"" + tier + "\",\"rank\":\"I\"}]";
    }
}
