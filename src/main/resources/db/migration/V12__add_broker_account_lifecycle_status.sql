-- 증권사 연결을 다시 검증할 때 계좌 행을 지우고 새로 만들면, 그 계좌를 참조하는
-- 과거 보유 종목 스냅샷(broker_holding_snapshots.broker_account_id)의 외래키 제약이
-- 깨져 재검증이 실패한다. 계좌 행은 이력의 식별자이므로 삭제하지 않고 상태로만 구분한다.
--
-- ACTIVE   : 가장 최근 검증에서 증권사가 반환한 계좌. 후보 목록에 노출되고 연결할 수 있다.
-- DETACHED : 가장 최근 검증에서 반환되지 않은 계좌. 후보에서 숨기고 새 링크를 만들 수 없지만,
--            과거 스냅샷과 개시 잔고 승인 이력이 계속 참조할 수 있도록 행은 남긴다.
--            증권사가 다시 반환하면 같은 행이 ACTIVE로 돌아온다.
--
-- 기존 계좌 행은 모두 마지막 검증 결과이므로 ACTIVE로 채운다. 데이터 손실이나
-- 이력 재작성은 없고, 스냅샷·링크·승인 이력 행은 이 마이그레이션에서 건드리지 않는다.
ALTER TABLE broker_accounts
    ADD COLUMN status VARCHAR(20);

UPDATE broker_accounts
SET status = 'ACTIVE'
WHERE status IS NULL;

ALTER TABLE broker_accounts
    ALTER COLUMN status SET NOT NULL;

-- 분리된 시점만 기록한다. ACTIVE인 동안에는 NULL이다.
ALTER TABLE broker_accounts
    ADD COLUMN detached_at TIMESTAMP(6);

-- 연결 후보와 신규 링크 검증은 연결별 활성 계좌만 읽는다.
CREATE INDEX idx_broker_accounts_connection_status
    ON broker_accounts (broker_connection_id, status);
