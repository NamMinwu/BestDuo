-- H2 테스트 환경용 duo_ranking_staging 테이블 생성
-- (Flyway disabled 상태에서 native query 테스트에 필요)
CREATE TABLE IF NOT EXISTS duo_ranking_staging (
    patch                VARCHAR(10)  NOT NULL,
    tier                 VARCHAR(20)  NOT NULL,
    rank_position        INT          NOT NULL,
    adc_champion_id      INT          NOT NULL,
    support_champion_id  INT          NOT NULL,
    score                FLOAT        NOT NULL,
    tier_grade           INT          NOT NULL,
    win_rate             FLOAT        NOT NULL,
    pick_rate            FLOAT        NOT NULL,
    games                INT          NOT NULL,
    ci_lower             FLOAT        NOT NULL,
    is_sufficient_sample BOOLEAN      NOT NULL,
    PRIMARY KEY (patch, tier, rank_position)
);
