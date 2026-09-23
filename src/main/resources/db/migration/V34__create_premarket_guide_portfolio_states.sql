CREATE TABLE premarket_guide_portfolio_states (
    guide_snapshot_id BIGINT PRIMARY KEY,
    state_schema_version INTEGER NOT NULL,
    read_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    ledger_sha256 VARCHAR(64) NOT NULL,
    ledger_transaction_count INTEGER NOT NULL,
    holdings_sha256 VARCHAR(64) NOT NULL,
    strategy_overrides_sha256 VARCHAR(64) NOT NULL,
    risk_settings_sha256 VARCHAR(64) NOT NULL,
    candidate_source VARCHAR(20) NOT NULL,
    candidate_set_sha256 VARCHAR(64) NOT NULL,
    asset_catalog_sha256 VARCHAR(64) NOT NULL,
    -- 증권사 보유 스냅샷은 연결 삭제 시 함께 지워지므로 외래키를 두지 않는다. 당시 참조한 식별자만 남긴다.
    broker_snapshot_id BIGINT,
    state_sha256 VARCHAR(64) NOT NULL,
    CONSTRAINT fk_premarket_guide_portfolio_states_snapshot
        FOREIGN KEY (guide_snapshot_id) REFERENCES premarket_guide_snapshots (id)
);
