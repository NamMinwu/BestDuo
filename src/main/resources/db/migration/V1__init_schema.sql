-- BestDuo 초기 스키마
-- Phase 0 검증 후 min_sample_threshold 결정 → ranking.min-sample-games 설정값 업데이트

CREATE TABLE IF NOT EXISTS match_raw (
    match_id     VARCHAR(20)  NOT NULL PRIMARY KEY,
    patch        VARCHAR(10)  NOT NULL,
    raw_json     JSONB,                            -- 영구 보존 (NULL화 없음)
    processed    BOOLEAN      NOT NULL DEFAULT FALSE,
    collected_at TIMESTAMP    NOT NULL
);

CREATE INDEX idx_match_raw_patch_processed ON match_raw(patch, processed);

-- -------------------------------------------------------

CREATE TABLE IF NOT EXISTS summoner_pool (
    puuid        VARCHAR(78)  NOT NULL PRIMARY KEY,
    tier         VARCHAR(20),                      -- EMERALD, DIAMOND, MASTER, GRANDMASTER, CHALLENGER
    is_verified  BOOLEAN      NOT NULL DEFAULT FALSE,
    last_seen_at TIMESTAMP
);

CREATE INDEX idx_summoner_pool_verified ON summoner_pool(is_verified);
CREATE INDEX idx_summoner_pool_tier_verified ON summoner_pool(tier, is_verified);

-- -------------------------------------------------------

CREATE TABLE IF NOT EXISTS patch_meta (
    patch        VARCHAR(10)  NOT NULL PRIMARY KEY,
    released_at  TIMESTAMP,
    is_active    BOOLEAN      NOT NULL DEFAULT TRUE
);

-- -------------------------------------------------------

CREATE TABLE IF NOT EXISTS patch_tier_meta (
    patch               VARCHAR(10)  NOT NULL,
    tier                VARCHAR(20)  NOT NULL,
    -- pick_rate 분모: 해당 (patch, tier)의 전체 바텀 게임 수
    -- 계산 방식: 전체 매치 수 × 2 (매치 1건 = 블루팀 바텀 1 + 레드팀 바텀 1)
    total_bottom_games  BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (patch, tier)
);

-- -------------------------------------------------------

CREATE TABLE IF NOT EXISTS duo_pair_stats (
    patch                 VARCHAR(10)  NOT NULL,
    tier                  VARCHAR(20)  NOT NULL,   -- tier='ALL' 행은 전체 합산 사전집계
    adc_champion_id       INT          NOT NULL,
    support_champion_id   INT          NOT NULL,
    games                 INT          NOT NULL DEFAULT 0,
    wins                  INT          NOT NULL DEFAULT 0,
    win_rate              FLOAT        NOT NULL DEFAULT 0,
    pick_rate             FLOAT        NOT NULL DEFAULT 0,
    ci_lower              FLOAT        NOT NULL DEFAULT 0,
    ci_upper              FLOAT        NOT NULL DEFAULT 0,
    is_sufficient_sample  BOOLEAN      NOT NULL DEFAULT FALSE,
    PRIMARY KEY (patch, tier, adc_champion_id, support_champion_id)
);

-- sort=WIN_RATE, sort=PICK_RATE 인덱스
CREATE INDEX idx_duo_pair_patch_tier_winrate
    ON duo_pair_stats(patch, tier, win_rate DESC);
CREATE INDEX idx_duo_pair_patch_tier_pickrate
    ON duo_pair_stats(patch, tier, pick_rate DESC);
-- 상세 조회 인덱스 (adc+support 기준)
CREATE INDEX idx_duo_pair_adc_support_patch_tier
    ON duo_pair_stats(adc_champion_id, support_champion_id, patch, tier);

-- -------------------------------------------------------

-- 라이브 랭킹 테이블 (RankingComputerJob이 staging → atomic swap으로 반영)
CREATE TABLE IF NOT EXISTS duo_ranking (
    patch                 VARCHAR(10)  NOT NULL,
    tier                  VARCHAR(20)  NOT NULL,
    rank_position         INT          NOT NULL,
    adc_champion_id       INT          NOT NULL,
    support_champion_id   INT          NOT NULL,
    score                 FLOAT        NOT NULL,
    tier_grade            INTEGER      NOT NULL,   -- 0=S, 1=A, 2=B, 3=C, 4=D
    -- duo_pair_stats에서 비정규화 (sort=WIN_RATE/PICK_RATE JOIN 없이 처리)
    win_rate              FLOAT        NOT NULL,
    pick_rate             FLOAT        NOT NULL,
    games                 INT          NOT NULL,
    ci_lower              FLOAT        NOT NULL,
    is_sufficient_sample  BOOLEAN      NOT NULL,
    PRIMARY KEY (patch, tier, rank_position)
);

CREATE INDEX idx_duo_ranking_patch_tier_winrate
    ON duo_ranking(patch, tier, win_rate DESC);
CREATE INDEX idx_duo_ranking_patch_tier_pickrate
    ON duo_ranking(patch, tier, pick_rate DESC);
CREATE INDEX idx_duo_ranking_adc_support
    ON duo_ranking(adc_champion_id, support_champion_id, patch, tier);

-- Staging 테이블: RankingComputerJob이 여기에 먼저 쓰고 atomic swap
-- 구조는 duo_ranking과 동일
CREATE TABLE IF NOT EXISTS duo_ranking_staging (LIKE duo_ranking INCLUDING ALL);
