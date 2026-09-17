-- 드라이런(모의 제출) 실행과 실거래 실행을 감사 화면에서 절대 섞이지 않게 구분한다.
-- tradeguide.broker.order-execution.live-enabled가 꺼져 있는(기본값) 동안 기록되는
-- 모든 행은 dry_run=true다.
ALTER TABLE broker_order_execution_runs
    ADD COLUMN dry_run BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE broker_order_execution_runs
    ALTER COLUMN dry_run DROP DEFAULT;
