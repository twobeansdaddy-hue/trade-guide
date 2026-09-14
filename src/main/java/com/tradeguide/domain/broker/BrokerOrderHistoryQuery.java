package com.tradeguide.domain.broker;

import java.time.LocalDate;

/**
 * 주문 이력 한 페이지를 요청하는 조건이다.
 *
 * <p>기간은 <b>체결일이 아니라 주문일 기준</b>이다. 미국 시장은 KST로 보면 주문일과 체결일이
 * 하루 어긋나는 일이 흔하므로, 이름에 {@code ordered}를 남겨 호출자가 오해하지 않게 한다.
 * 체결일 기준으로 걸러야 하는 책임은 조회 결과를 쓰는 쪽에 있다.
 */
public record BrokerOrderHistoryQuery(
        BrokerOrderStatusGroup statusGroup,
        LocalDate orderedFrom,
        LocalDate orderedTo,
        String cursor,
        int pageSize
) {
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    public BrokerOrderHistoryQuery {
        if (statusGroup == null) {
            throw new IllegalArgumentException("주문 이력 조회 상태 그룹은 필수입니다.");
        }
        if (orderedFrom != null && orderedTo != null && orderedFrom.isAfter(orderedTo)) {
            throw new IllegalArgumentException("주문 이력 조회 시작일은 종료일보다 늦을 수 없습니다.");
        }
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("주문 이력 페이지 크기는 1 이상 " + MAX_PAGE_SIZE + " 이하여야 합니다.");
        }
        if (cursor != null && cursor.isBlank()) {
            cursor = null;
        }
        if (statusGroup == BrokerOrderStatusGroup.OPEN && cursor != null) {
            throw new IllegalArgumentException("진행 중 주문 조회는 커서를 사용하지 않습니다.");
        }
    }

    /** 종료된 주문의 첫 페이지. 기간 경계 보정은 호출자가 이미 끝냈다고 본다. */
    public static BrokerOrderHistoryQuery closedFirstPage(LocalDate orderedFrom, LocalDate orderedTo, int pageSize) {
        return new BrokerOrderHistoryQuery(BrokerOrderStatusGroup.CLOSED, orderedFrom, orderedTo, null, pageSize);
    }

    /**
     * 진행 중 주문. 제공자가 전량을 한 번에 돌려주므로 커서와 페이지 크기는 의미가 없다.
     * 표시 전용이며 원장 반영 대상이 아니다.
     */
    public static BrokerOrderHistoryQuery open(LocalDate orderedFrom, LocalDate orderedTo) {
        return new BrokerOrderHistoryQuery(
                BrokerOrderStatusGroup.OPEN, orderedFrom, orderedTo, null, DEFAULT_PAGE_SIZE);
    }

    /** 같은 구간의 다음 페이지 조건. 커서 순회는 호출자가 수행한다. */
    public BrokerOrderHistoryQuery withCursor(String nextCursor) {
        return new BrokerOrderHistoryQuery(statusGroup, orderedFrom, orderedTo, nextCursor, pageSize);
    }
}
