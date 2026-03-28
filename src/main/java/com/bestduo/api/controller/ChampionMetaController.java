package com.bestduo.api.controller;

import com.bestduo.api.dto.ChampionMetaResponse;
import com.bestduo.api.service.ChampionMetaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "챔피언 메타", description = "챔피언 이름/이미지 메타 API")
@RestController
@RequestMapping("/api/v1/meta")
@RequiredArgsConstructor
public class ChampionMetaController {

    private final ChampionMetaService championMetaService;

    @Operation(summary = "전체 챔피언 목록 조회", description = "이름 오름차순 정렬. 프론트에서 championId → 이름/이미지 변환에 사용.")
    @GetMapping("/champions")
    public ResponseEntity<ChampionMetaResponse> getChampions() {
        return ResponseEntity.ok(ChampionMetaResponse.of(championMetaService.findAll()));
    }

    @Operation(summary = "특정 챔피언 조회")
    @GetMapping("/champions/{championId}")
    public ResponseEntity<ChampionMetaResponse.ChampionItem> getChampion(
            @Parameter(description = "챔피언 ID", example = "21") @PathVariable int championId) {

        return championMetaService.findById(championId)
                .map(ChampionMetaResponse.ChampionItem::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "챔피언 메타 수동 동기화 (관리용)", description = "Data Dragon에서 즉시 동기화. 패치 직후 어드민 트리거용.")
    @PostMapping("/champions/sync")
    public ResponseEntity<String> syncChampions(
            @Parameter(description = "Data Dragon 버전", example = "15.6.1")
            @RequestParam String version) {

        int count = championMetaService.syncChampions(version);
        return ResponseEntity.ok("동기화 완료: " + count + "개 챔피언");
    }
}
