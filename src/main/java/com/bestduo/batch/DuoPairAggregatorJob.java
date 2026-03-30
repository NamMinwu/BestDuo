package com.bestduo.batch;

import com.bestduo.batch.phase0.DuoPairExtractor;
import com.bestduo.calculator.WilsonScoreCalculator;
import com.bestduo.domain.entity.DuoPairStats;
import com.bestduo.domain.entity.PatchTierMeta;
import com.bestduo.domain.model.BottomPair;
import com.bestduo.domain.repository.DuoPairStatsRepository;
import com.bestduo.domain.repository.MatchRawRepository;
import com.bestduo.domain.repository.PatchTierMetaRepository;
import com.bestduo.domain.repository.SummonerPoolRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * match_raw → duo_pair_stats 집계 배치 잡
 *
 * 흐름:
 *   1. aggregatePairsStep  — processed=FALSE 매치에서 바텀 페어 추출 → duo_pair_stats 누적
 *   2. aggregateAllTierStep — 개별 티어 stats 합산 → tier='ALL' 사전집계 행 생성/갱신
 *   3. markProcessedStep   — match_raw.processed = TRUE 업데이트
 *
 * 티어 결정:
 *   매치 JSON 참가자 PUUID → summoner_pool.tier 조회 (ADC 기준)
 *   티어 미확인/UNRANKED → tier='ALL' 기여만 함 (tier-specific 행 생성 안 함)
 *
 * pick_rate 분모:
 *   patch_tier_meta.total_bottom_games = 해당 (patch, tier)에서 유효하게 추출된 바텀 페어 수
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class DuoPairAggregatorJob {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final MatchRawRepository matchRawRepository;
    private final DuoPairStatsRepository duoPairStatsRepository;
    private final PatchTierMetaRepository patchTierMetaRepository;
    private final SummonerPoolRepository summonerPoolRepository;
    private final DuoPairExtractor duoPairExtractor;
    private final WilsonScoreCalculator wilsonCalculator;
    private final ObjectMapper objectMapper;

    @Value("${ranking.min-sample-games:30}")
    private int minSampleGames;

    // ─── Job ──────────────────────────────────────────────────────────────────

    @Bean
    public Job duoPairAggregatorBatchJob() {
        return new JobBuilder("duoPairAggregatorJob", jobRepository)
                .start(aggregatePairsStep())
                .next(aggregateAllTierStep())
                .next(markProcessedStep())
                .build();
    }

    // ─── Step 1: 페어 집계 ───────────────────────────────────────────────────

    @Bean
    public Step aggregatePairsStep() {
        return new StepBuilder("aggregatePairsStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    log.info("[Step 1] 바텀 페어 집계 시작");

                    var unprocessed = matchRawRepository.findByProcessedFalse().stream()
                            .filter(m -> m.getRawJson() != null)
                            .toList();

                    log.info("[Step 1] 처리 대상 매치: {}건", unprocessed.size());

                    // 인메모리 집계: (patch, tier, adcId, suppId) → [games, wins]
                    Map<PairKey, int[]> accumulator = new LinkedHashMap<>();
                    // (patch, tier) → total valid pairs extracted
                    Map<String, Long> patchTierPairCount = new LinkedHashMap<>();

                    for (var match : unprocessed) {
                        List<BottomPair> pairs = duoPairExtractor.extract(match.getRawJson());
                        Map<Integer, String> teamIdToAdcPuuid = extractAdcPuuids(match.getRawJson());

                        for (BottomPair pair : pairs) {
                            String patch = pair.getPatch();

                            // tier='ALL' 항상 집계
                            PairKey allKey = new PairKey(patch, "ALL", pair.getAdcChampionId(), pair.getSupportChampionId());
                            accumulate(accumulator, allKey, pair.isWin());
                            incrementPairCount(patchTierPairCount, patch + ":ALL");

                            // tier-specific: ADC PUUID → summoner_pool.tier
                            String adcPuuid = teamIdToAdcPuuid.get(pair.getTeamId());
                            if (adcPuuid != null) {
                                String tier = summonerPoolRepository.findById(adcPuuid)
                                        .map(s -> s.isVerified() && s.getTier() != null
                                                && !"UNRANKED".equals(s.getTier())
                                                && !"UNKNOWN".equals(s.getTier()) ? s.getTier() : null)
                                        .orElse(null);

                                if (tier != null) {
                                    PairKey tierKey = new PairKey(patch, tier, pair.getAdcChampionId(), pair.getSupportChampionId());
                                    accumulate(accumulator, tierKey, pair.isWin());
                                    incrementPairCount(patchTierPairCount, patch + ":" + tier);
                                }
                            }
                        }
                    }

                    // DB upsert
                    upsertDuoPairStats(accumulator);

                    // patch_tier_meta 업데이트
                    upsertPatchTierMeta(patchTierPairCount);

                    log.info("[Step 1] 집계 완료: 고유 페어 {}개, patch-tier 조합 {}개",
                            accumulator.size(), patchTierPairCount.size());

                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    // ─── Step 2: tier='ALL' 사전집계 ────────────────────────────────────────

    @Bean
    public Step aggregateAllTierStep() {
        return new StepBuilder("aggregateAllTierStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    log.info("[Step 2] tier='ALL' 사전집계 시작");

                    // Step 1에서 이미 'ALL' 행이 직접 집계됨
                    // 여기서는 pick_rate 재계산 (total_bottom_games 기준)
                    Set<String> patches = new HashSet<>();
                    duoPairStatsRepository.findAll().forEach(s -> patches.add(s.getPatch()));

                    for (String patch : patches) {
                        recalculatePickRates(patch, "ALL");
                        for (String tier : List.of("EMERALD", "DIAMOND", "MASTER", "GRANDMASTER", "CHALLENGER")) {
                            recalculatePickRates(patch, tier);
                        }
                    }

                    log.info("[Step 2] pick_rate 재계산 완료");
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    // ─── Step 3: processed 마킹 ─────────────────────────────────────────────

    @Bean
    public Step markProcessedStep() {
        return new StepBuilder("markProcessedStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    log.info("[Step 3] match_raw.processed = TRUE 업데이트");

                    int updated = matchRawRepository.markAllProcessed();

                    log.info("[Step 3] {}건 processed 처리 완료", updated);
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    // ─── 내부 헬퍼 ──────────────────────────────────────────────────────────

    private Map<Integer, String> extractAdcPuuids(String matchJson) {
        Map<Integer, String> teamIdToAdcPuuid = new HashMap<>();
        try {
            JsonNode participants = objectMapper.readTree(matchJson).path("info").path("participants");
            for (JsonNode p : participants) {
                if ("BOTTOM".equals(p.path("teamPosition").asText(""))) {
                    int teamId = p.path("teamId").asInt();
                    String puuid = p.path("puuid").asText("");
                    if (!puuid.isEmpty()) teamIdToAdcPuuid.put(teamId, puuid);
                }
            }
        } catch (Exception e) {
            log.warn("ADC PUUID 추출 실패: {}", e.getMessage());
        }
        return teamIdToAdcPuuid;
    }

    private void accumulate(Map<PairKey, int[]> acc, PairKey key, boolean win) {
        acc.computeIfAbsent(key, k -> new int[]{0, 0});
        acc.get(key)[0]++;       // games
        if (win) acc.get(key)[1]++;  // wins
    }

    private void incrementPairCount(Map<String, Long> map, String key) {
        map.merge(key, 1L, Long::sum);
    }

    @Transactional
    void upsertDuoPairStats(Map<PairKey, int[]> accumulator) {
        for (var entry : accumulator.entrySet()) {
            PairKey key = entry.getKey();
            int addGames = entry.getValue()[0];
            int addWins = entry.getValue()[1];

            var existing = duoPairStatsRepository.findByPatchAndTierAndAdcChampionIdAndSupportChampionId(
                    key.patch(), key.tier(), key.adcChampionId(), key.supportChampionId());

            DuoPairStats stats;
            if (existing.isPresent()) {
                stats = existing.get().addGames(addGames, addWins);
            } else {
                stats = DuoPairStats.builder()
                        .patch(key.patch())
                        .tier(key.tier())
                        .adcChampionId(key.adcChampionId())
                        .supportChampionId(key.supportChampionId())
                        .games(addGames)
                        .wins(addWins)
                        .winRate(0)
                        .pickRate(0)
                        .ciLower(0)
                        .ciUpper(0)
                        .sufficientSample(false)
                        .build();
            }

            // win_rate, ci_lower/upper 즉시 계산 (pick_rate는 Step 2에서 재계산)
            int totalGames = stats.getGames();
            int totalWins = stats.getWins();
            double winRate = totalGames > 0 ? (double) totalWins / totalGames : 0;
            double ciLower = totalGames > 0 ? wilsonCalculator.calculateLower(totalWins, totalGames) : 0;
            double ciUpper = totalGames > 0 ? wilsonCalculator.calculateUpper(totalWins, totalGames) : 0;
            boolean sufficient = totalGames >= minSampleGames;

            duoPairStatsRepository.save(
                    stats.withComputedStats(winRate, 0, ciLower, ciUpper, sufficient));
        }
    }

    @Transactional
    void upsertPatchTierMeta(Map<String, Long> patchTierPairCount) {
        for (var entry : patchTierPairCount.entrySet()) {
            String[] parts = entry.getKey().split(":", 2);
            String patch = parts[0];
            String tier = parts[1];
            long newCount = entry.getValue();

            var existing = patchTierMetaRepository.findByPatchAndTier(patch, tier);
            if (existing.isPresent()) {
                PatchTierMeta updated = PatchTierMeta.builder()
                        .patch(patch)
                        .tier(tier)
                        .totalBottomGames(existing.get().getTotalBottomGames() + newCount)
                        .build();
                patchTierMetaRepository.save(updated);
            } else {
                patchTierMetaRepository.save(PatchTierMeta.builder()
                        .patch(patch)
                        .tier(tier)
                        .totalBottomGames(newCount)
                        .build());
            }
        }
    }

    @Transactional
    void recalculatePickRates(String patch, String tier) {
        var meta = patchTierMetaRepository.findByPatchAndTier(patch, tier);
        if (meta.isEmpty()) return;
        long totalBottomGames = meta.get().getTotalBottomGames();
        if (totalBottomGames == 0) return;

        List<DuoPairStats> stats = duoPairStatsRepository.findByPatchAndTier(patch, tier);
        for (DuoPairStats s : stats) {
            double pickRate = (double) s.getGames() / totalBottomGames;
            duoPairStatsRepository.save(
                    s.withComputedStats(s.getWinRate(), pickRate, s.getCiLower(), s.getCiUpper(), s.isSufficientSample()));
        }
    }

    record PairKey(String patch, String tier, int adcChampionId, int supportChampionId) {}
}
