-- 실행 한 건의 주문 항목을 서버 페이징으로 읽는 조회가 생겼다.
-- 실행 하나에 수천 건이 담길 수 있어, 인덱스 없이는 페이지를 넘길 때마다 실행 전체를 읽고
-- 정렬한 뒤 버리게 된다.
--
-- 정렬은 주문 시각 최신순이며 id를 동점 기준으로 함께 둔다. 같은 초에 주문된 건이 흔해서
-- 동점 기준이 없으면 페이지 경계에서 같은 항목이 두 페이지에 나오거나 아예 빠진다.
--
-- 기존 idx_broker_order_import_items_run_filled_at 은 체결 시각 기준이라 이 정렬을 돕지 못하고,
-- 반영 대상 판정(기준 시각 이후 체결분)이 계속 사용하므로 그대로 둔다.

CREATE INDEX idx_broker_order_import_items_run_ordered_at
    ON broker_order_import_items (run_id, ordered_at DESC, id DESC);
