-- 증권사 이력 조회를 서버 페이징으로 바꾸면서 정렬 기준에 id 동점 기준을 추가했다.
-- 정렬이 완전히 결정되지 않으면 페이지 경계에서 같은 행이 두 페이지에 나오거나 아예 빠진다.
-- 특히 일괄 개시 잔고 반영은 여러 승인 이력이 같은 approved_at 값을 갖기 때문에
-- 동점 기준 없이는 반드시 흔들린다.
--
-- 기존 인덱스는 새 인덱스가 앞 컬럼을 그대로 포함하므로 그대로 대체한다.

DROP INDEX idx_broker_holding_imports_portfolio_approved_at;

CREATE INDEX idx_broker_holding_imports_portfolio_approved_at
    ON portfolio_broker_holding_imports (portfolio_id, approved_at DESC, id DESC);

DROP INDEX idx_broker_order_import_runs_portfolio_started_at;

CREATE INDEX idx_broker_order_import_runs_portfolio_started_at
    ON broker_order_import_runs (portfolio_id, started_at DESC, id DESC);
