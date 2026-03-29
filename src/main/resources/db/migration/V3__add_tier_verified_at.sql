-- summoner_pool 티어 재검증 지원
-- tier_verified_at: 마지막으로 LEAGUE-V4 API로 티어를 확인한 시각
-- NULL = 한 번도 검증 안 됨, N일 이상 경과 시 TierVerificationJob이 재검증
ALTER TABLE summoner_pool ADD COLUMN tier_verified_at TIMESTAMP;

CREATE INDEX idx_summoner_pool_tier_verified_at ON summoner_pool(tier_verified_at);
