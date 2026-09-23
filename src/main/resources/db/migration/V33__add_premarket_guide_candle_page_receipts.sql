ALTER TABLE premarket_guide_candle_evidence
    ADD COLUMN adjusted_requested BOOLEAN;

CREATE TABLE premarket_guide_candle_page_receipts (
    candle_evidence_id BIGINT NOT NULL,
    page_index INTEGER NOT NULL,
    response_received_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    PRIMARY KEY (candle_evidence_id, page_index),
    CONSTRAINT fk_premarket_guide_candle_page_receipts_evidence
        FOREIGN KEY (candle_evidence_id) REFERENCES premarket_guide_candle_evidence (id)
);
