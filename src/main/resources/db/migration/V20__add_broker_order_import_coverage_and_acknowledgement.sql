-- 서버 구간 분할(S6b)이 예산 부족으로 요청 구간을 끝까지 커버하지 못했을 때,
-- 어디까지 커버했는지 남긴다. NULL이면 요청 구간을 끝까지 커버했다는 뜻이다.
ALTER TABLE broker_order_import_runs
    ADD COLUMN covered_ordered_to DATE;

-- 불완전 이력(fullyCovered=false)이면서 보유 수량 대조가 MATCHED가 아닌 실행을
-- 사용자가 그 사실을 알고 승인했다는 기록이다. NULL이면 아직 확인하지 않았거나
-- 애초에 확인이 필요하지 않았던 실행이다(완전 커버 또는 대조 MATCHED).
ALTER TABLE broker_order_import_runs
    ADD COLUMN coverage_acknowledged_at TIMESTAMP(6);

ALTER TABLE broker_order_import_runs
    ADD COLUMN coverage_acknowledged_by_member_id BIGINT;

ALTER TABLE broker_order_import_runs
    ADD CONSTRAINT fk_broker_order_import_runs_coverage_ack_member
        FOREIGN KEY (coverage_acknowledged_by_member_id) REFERENCES members (id);
