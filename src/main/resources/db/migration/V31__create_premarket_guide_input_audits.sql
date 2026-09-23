CREATE TABLE premarket_guide_input_audits (
    guide_snapshot_id BIGINT PRIMARY KEY,
    recorded_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    evidence_status VARCHAR(20) NOT NULL,
    candle_provider VARCHAR(50),
    response_received_at TIMESTAMP(6) WITH TIME ZONE,
    adjustment_mode VARCHAR(40),
    input_sha256 VARCHAR(64),
    portfolio_state_ref VARCHAR(100),
    missing_reasons VARCHAR(1000) NOT NULL,
    CONSTRAINT fk_premarket_guide_input_audits_snapshot
        FOREIGN KEY (guide_snapshot_id) REFERENCES premarket_guide_snapshots (id)
);
