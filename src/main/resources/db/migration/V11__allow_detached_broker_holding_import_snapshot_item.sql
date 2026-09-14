-- 증권사 연결을 삭제하면 그 연결에서 파생된 보유 종목 스냅샷과 항목도 함께 사라진다.
-- 이미 취소(REVOKED)된 개시 잔고 승인 이력은 감사 목적으로 보존해야 하므로,
-- 스냅샷 항목 참조만 끊을 수 있도록 NOT NULL 제약을 완화한다.
-- 승인 당시 수량·평단가·표시명·스냅샷 기준 시각·매매 기록 ID는 이 테이블에 이미
-- 복사되어 있어 참조가 끊겨도 이력 자체는 그대로 조회할 수 있다.
-- uk_broker_holding_imports_snapshot_item 유니크 제약은 NULL을 중복으로 보지 않으므로
-- 유효한 승인의 중복 방지 효과는 그대로 유지된다.
ALTER TABLE portfolio_broker_holding_imports
    ALTER COLUMN snapshot_item_id DROP NOT NULL;
