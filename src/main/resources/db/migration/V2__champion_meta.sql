-- 챔피언 메타 테이블
-- Data Dragon champion.json 기반으로 주기적 동기화
-- 프론트엔드에서 championId → 이름/이미지 변환에 사용

CREATE TABLE IF NOT EXISTS champion_meta (
    champion_id  INT          NOT NULL PRIMARY KEY,  -- Riot champion key (숫자)
    name         VARCHAR(50)  NOT NULL,               -- 표시 이름 (e.g. 'Miss Fortune')
    champion_key VARCHAR(20)  NOT NULL,               -- 내부 키 (e.g. 'MissFortune', CDN 경로에 사용)
    image_url    VARCHAR(200),                        -- 아이콘 URL (Data Dragon CDN)
    updated_at   TIMESTAMP    NOT NULL
);

CREATE INDEX idx_champion_meta_key ON champion_meta(champion_key);
