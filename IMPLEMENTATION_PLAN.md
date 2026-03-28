# BestDuo 구현 계획

## 현재 상태 (2026-03-28)

GitHub: https://github.com/NamMinwu/BestDuo (main 브랜치)

완료:
- Spring Boot 3.5.0 + Gradle + Java 21 프로젝트 구조
- 엔티티 6개, 레포지토리 6개, 컨트롤러 2개
- WilsonScoreCalculator (정확한 공식 + 단위 테스트 9개 통과)
- RiotApiClient (Guava RateLimiter 0.7req/s per key, round-robin)
- Flyway V1 마이그레이션 (duo_ranking_staging 포함)
- docker-compose.yml (PostgreSQL 16)

---

## 확정된 설계 결정사항

| 항목 | 결정 |
|---|---|
| tier=ALL | duo_pair_stats에 사전집계 행 저장 (wins/games 합산 후 Wilson 재계산) |
| match_raw.raw_json | 영구 보존 (NULL화 없음) |
| 429 방지 | Guava RateLimiter(0.7 req/s) per key — 사전 차단 |
| BFS 티어 확인 | 별도 병렬 TierVerificationJob (매치 수집 중 즉시 확인 금지) |
| RankingComputerJob | duo_ranking_staging → atomic swap (읽기 중단 방지) |
| duo_ranking | win_rate, pick_rate, games, ci_lower 비정규화 (sort 성능) |
| total_bottom_games | 전체 매치 수 × 2 (매치 1건 = 블루팀 바텀 1 + 레드팀 바텀 1) |
| Wilson score | Newcombe(1998) 정확한 공식 (근사식 금지) |

---

## STEP 1: Phase 0 데이터 밀도 검증 (3~4일)

### 목표
KR Challenger/GM/Master 매치 수집 → 바텀 페어 분포 확인 → min_sample_threshold 결정

### 구현할 파일
```
src/main/java/com/bestduo/batch/phase0/
  Phase0DataValidator.java      ← 메인 실행 클래스 (CommandLineRunner)
  Phase0CollectionService.java  ← 수집 로직
  Phase0ReportService.java      ← 결과 분석 및 출력
```

### 구현 순서
1. `Phase0CollectionService`
   - `RiotApiClient.fetchChallengerLadder()` → PUUID 목록 추출
   - `RiotApiClient.fetchGrandmasterLadder()` + `fetchMasterLadder()`
   - 각 PUUID에서 `fetchMatchIds(puuid, 20)` → 최근 20 매치
   - 중복 제거 후 `fetchMatchDetail(matchId)` → DB 저장
   - 목표: 2,000~5,000 매치

2. 바텀 페어 추출 로직
   - participants 중 teamPosition=`BOTTOM` + `UTILITY` 같은 teamId 쌍
   - 불일치/누락 시 해당 매치 스킵
   - 매치당 2쌍 추출 (블루팀 1 + 레드팀 1)

3. `Phase0ReportService`
   - 페어별 게임 수 분포 (히스토그램)
   - 하위 25% 페어의 중앙값
   - Wilson score 95% CI 하한 계산
   - min_sample_threshold 추천값 출력

### 실행 방법
```bash
# 로컬 DB 시작
docker-compose up -d

# 환경변수 설정
export RIOT_API_KEYS=key1,key2,key3

# Phase 0 실행 (spring.batch.job.enabled=false이므로 별도 profile 필요)
./gradlew bootRun --args='--spring.profiles.active=phase0'
```

### 확인할 숫자
- 페어당 평균 게임 수: 얼마인가?
- 하위 25% 페어의 게임 수: 얼마인가?
- min_sample_threshold 추천: 30? 50?
- Phase 1 진행 가능 여부 판단

---

## STEP 2: Spring Batch Job 구현 (2~3주)

### 구현 순서 (Lane A와 B 병렬 가능)

#### Lane A: Batch Jobs
```
src/main/java/com/bestduo/batch/
  MatchCollectorJob.java        ← Challenger~Emerald 소환사 → 매치 수집
  TierVerificationJob.java      ← is_verified=FALSE 소환사 배치 티어 확인
  DuoPairAggregatorJob.java     ← match_raw → duo_pair_stats 집계
  RankingComputerJob.java       ← Wilson score → duo_ranking_staging → atomic swap
  PatchDetectionScheduler.java  ← Data Dragon 15분 폴링
```

#### Lane B: 추가 테스트
```
src/test/java/com/bestduo/
  batch/RankingComputerJobTest.java   ← @DataJpaTest rollback 테스트
  api/controller/DuosControllerTest.java  ← @WebMvcTest
  integration/RankingRollbackTest.java    ← @SpringBootTest E2E
```

---

### MatchCollectorJob 핵심 로직

```
소환사 수집:
  Challenger/GM/Master → 래더 API 직접
  Emerald/Diamond → LEAGUE-V4 페이지네이션 (빈 배열 → 종료)

BFS 확장:
  매치 10명 참가자 PUUID → summoner_pool 저장 (is_verified=FALSE)
  TierVerificationJob이 별도로 배치 확인

중복 방지:
  match_id UNIQUE 제약 → DB 레벨 보장 (idempotent)
```

### DuoPairAggregatorJob 핵심 로직

```
1. match_raw WHERE processed=FALSE 조회
2. participants에서 teamPosition=BOTTOM+UTILITY, 같은 teamId 추출
3. duo_pair_stats 집계 (patch, tier별)
4. tier='ALL' 사전집계 행 생성 (wins+games 합산 후 Wilson 재계산)
5. patch_tier_meta.total_bottom_games = 처리 매치 수 × 2
6. match_raw.processed = TRUE 업데이트
```

### RankingComputerJob 핵심 로직

```
score = wilson_lower(wins, games) × 0.7 + normalized_pick_rate × 0.3
  normalized_pick_rate = pick_rate / max(pick_rate) [같은 patch, tier 기준]

tier_grade:
  score >= threshold.s → 0 (S)
  score >= threshold.a → 1 (A)
  score >= threshold.b → 2 (B)
  score >= threshold.c → 3 (C)
  else                 → 4 (D)

Atomic swap:
  1. duo_ranking_staging에 INSERT
  2. 단일 트랜잭션: DELETE duo_ranking WHERE patch=? AND tier=?
                  + INSERT INTO duo_ranking SELECT * FROM duo_ranking_staging

is_sufficient_sample = (games >= ranking.min-sample-games)
```

### PatchDetectionScheduler 핵심 로직

```
cron: 매 15분 Data Dragon 버전 API 폴링
  GET https://ddragon.leagueoflegends.com/api/versions.json

신규 패치 감지 시:
  1. patch_meta에 신규 패치 등록
  2. MatchCollectorJob 트리거
  3. RankingComputerJob 완료 후:
     - 최신 2개 패치만 is_active=TRUE 유지
     - 나머지 is_active=FALSE

주의: 패치 배포 직후 KR 게임 데이터 없음
  → 첫 수집에서 매치 0건이어도 에러 아님
  → 다음 스케줄 주기에 재수집
```

---

## STEP 3: 추가 필요 사항

### champion_meta 테이블 (Outside Voice 지적)

외부 검토에서 지적된 미구현 항목. 프론트엔드에서 챔피언 ID → 이름/이미지 필요.

```sql
-- V2__champion_meta.sql
CREATE TABLE champion_meta (
    champion_id   INT          NOT NULL PRIMARY KEY,
    name          VARCHAR(50)  NOT NULL,
    key           VARCHAR(20)  NOT NULL,  -- e.g. 'Jinx'
    image_url     VARCHAR(200),
    updated_at    TIMESTAMP    NOT NULL
);
```

```
GET /api/v1/meta/champions  ← 챔피언 목록 (Riot CDN champion.json 기반)
```

Data Dragon `champion.json`을 주기적으로 폴링해서 동기화.

### summoner_pool 재검증 주기 (Outside Voice 지적)

현재 미정. 권장: 패치마다 1회 전체 재검증 (TierVerificationJob에서 처리).

---

## Critical Gaps (구현 전 반드시 해결)

1. **WilsonScoreCalculator games=0 방어** — 이미 구현됨 (IllegalArgumentException)
2. **RankingComputerJob staging swap** — STEP 2에서 구현
3. **Riot API 5xx 재시도 + 실패 기록** — `RiotApiClient.fetchMatchDetail()`에 재시도 추가 필요

---

## 테스트 파일 목록 (구현 시 함께 작성)

| 파일 | 테스트 내용 |
|---|---|
| `WilsonScoreCalculatorTest` | 이미 완료 (9개 통과) |
| `DuoPairExtractorTest` | teamPosition 추출 로직 |
| `RiotApiClientTest` | round-robin, RateLimiter |
| `RankingComputerJobTest` | tier_grade 경계값, is_sufficient_sample |
| `DuosControllerTest` | 정렬/필터 API 응답 |
| `RankingRollbackTest` | staging swap rollback E2E |
| `PatchSwitchTest` | 패치 전환 E2E |

---

## 로컬 실행 방법

```bash
# PostgreSQL 시작
docker-compose up -d

# 환경변수
export RIOT_API_KEYS=발급받은_키1,발급받은_키2
export DB_USERNAME=bestduo
export DB_PASSWORD=bestduo

# 애플리케이션 실행
./gradlew bootRun

# 테스트
./gradlew test

# Swagger UI
http://localhost:8080/api/docs/swagger-ui
```

---

## 커밋 규칙

- prefix만 영어: `FEAT`, `FIX`, `REFACTOR`, `TEST`, `DOCS`, `CHORE`
- 나머지 제목/본문은 한글
- 예시: `FEAT : 바텀 듀오 매치 수집 배치 Job 구현`
