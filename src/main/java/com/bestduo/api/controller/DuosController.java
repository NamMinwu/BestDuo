package com.bestduo.api.controller;

import com.bestduo.api.dto.DuoRankingResponse;
import com.bestduo.domain.entity.DuoRanking;
import com.bestduo.domain.repository.DuoRankingRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@Tag(name = "듀오", description = "바텀 듀오 랭킹 조회 API")
@RestController
@RequestMapping("/api/v1/duos")
@RequiredArgsConstructor
public class DuosController {

    private final DuoRankingRepository duoRankingRepository;

    @Operation(summary = "바텀 듀오 랭킹 목록 조회")
    @GetMapping
    public ResponseEntity<DuoRankingResponse> getDuos(
            @Parameter(description = "패치 버전 (필수)", example = "15.6", required = true)
            @RequestParam String patch,

            @Parameter(description = "티어 필터", example = "ALL")
            @RequestParam(defaultValue = "ALL") String tier,

            @Parameter(description = "정렬 기준", example = "RANK")
            @RequestParam(defaultValue = "RANK") String sort) {

        List<DuoRanking> rankings = switch (sort.toUpperCase()) {
            case "WIN_RATE" -> duoRankingRepository.findByPatchAndTierOrderByWinRateDesc(patch, tier);
            case "PICK_RATE" -> duoRankingRepository.findByPatchAndTierOrderByPickRateDesc(patch, tier);
            default -> duoRankingRepository.findByPatchAndTierOrderByRankPositionAsc(patch, tier);
        };

        List<DuoRankingResponse.DuoRankingItem> items = rankings.stream()
                .map(DuoRankingResponse.DuoRankingItem::from)
                .toList();

        DuoRankingResponse response = DuoRankingResponse.builder()
                .data(items)
                .meta(DuoRankingResponse.Meta.builder()
                        .patch(patch)
                        .tier(tier)
                        .totalPairs(items.size())
                        .generatedAt(LocalDateTime.now())
                        .build())
                .build();

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "특정 듀오 조합 상세 조회")
    @GetMapping("/{adcChampionId}/{supportChampionId}")
    public ResponseEntity<DuoRankingResponse.DuoRankingItem> getDuoDetail(
            @Parameter(description = "원딜 챔피언 ID", example = "222") @PathVariable Integer adcChampionId,
            @Parameter(description = "서포터 챔피언 ID", example = "412") @PathVariable Integer supportChampionId,
            @Parameter(description = "패치 버전", required = true) @RequestParam String patch,
            @Parameter(description = "티어") @RequestParam(defaultValue = "ALL") String tier) {

        return duoRankingRepository
                .findByPatchAndTierAndAdcChampionIdAndSupportChampionId(patch, tier, adcChampionId, supportChampionId)
                .map(DuoRankingResponse.DuoRankingItem::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
