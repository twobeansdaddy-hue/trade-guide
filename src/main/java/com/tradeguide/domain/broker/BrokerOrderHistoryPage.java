package com.tradeguide.domain.broker;

import java.util.List;

/**
 * 주문 이력 조회 한 페이지다. 다음 페이지 순회는 이 타입을 받는 쪽이 수행한다.
 *
 * <p>{@code nextCursor}가 없으면 {@code hasNext}는 항상 거짓이다. 커서 없이 "다음이 있다"고
 * 보고하는 응답을 그대로 믿으면 순회가 끝나지 않으므로, 이 타입이 경계에서 막는다.
 *
 * <p>레코드 수와 제외 건수의 합은 제공자가 반환한 주문 수와 일치해야 한다.
 * 일치하지 않으면 어딘가에서 주문이 조용히 사라진 것이므로 만들지 못하게 한다.
 */
public record BrokerOrderHistoryPage(
        List<BrokerOrderRecord> records,
        String nextCursor,
        boolean hasNext,
        BrokerOrderExclusionCounts exclusions
) {
    public BrokerOrderHistoryPage {
        if (records == null) {
            throw new IllegalArgumentException("주문 이력 목록은 필수입니다.");
        }
        if (exclusions == null) {
            throw new IllegalArgumentException("주문 이력 집계 건수는 필수입니다.");
        }
        if (nextCursor != null && nextCursor.isBlank()) {
            nextCursor = null;
        }
        if (nextCursor == null) {
            hasNext = false;
        }
        if (exclusions.fetchedCount() != records.size() + exclusions.excludedCount()) {
            throw new IllegalArgumentException("주문 이력 조회 건수와 제외 건수 합계가 일치하지 않습니다.");
        }

        records = List.copyOf(records);
    }

    public static BrokerOrderHistoryPage lastPage(
            List<BrokerOrderRecord> records,
            BrokerOrderExclusionCounts exclusions
    ) {
        return new BrokerOrderHistoryPage(records, null, false, exclusions);
    }
}
