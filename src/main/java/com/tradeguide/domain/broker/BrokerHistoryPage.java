package com.tradeguide.domain.broker;

import java.util.List;

/**
 * 증권사 이력 한 페이지다. 항목뿐 아니라 현재 페이지·페이지 크기·전체 건수·다음 페이지 여부를
 * 함께 담는다. 호출자가 "이게 전부인지" 응답만 보고 판단할 수 있어야 하기 때문이다.
 *
 * <p>{@code hasNext}는 전체 건수에서 다시 계산하지 않고 저장소가 알려 준 값을 그대로 옮긴다.
 * 두 값이 서로 다른 근거에서 나오면 경계에서 어긋날 수 있다.
 */
public record BrokerHistoryPage<T>(
        List<T> items,
        int page,
        int size,
        long totalElements,
        boolean hasNext
) {
    public BrokerHistoryPage {
        if (items == null) {
            throw new IllegalArgumentException("이력 페이지 항목은 필수입니다.");
        }
        if (page < 0 || size < 1 || totalElements < 0) {
            throw new IllegalArgumentException("이력 페이지 정보가 올바르지 않습니다.");
        }
        items = List.copyOf(items);
    }

    public static <T> BrokerHistoryPage<T> empty(BrokerHistoryPageRequest pageRequest) {
        return new BrokerHistoryPage<>(List.of(), pageRequest.page(), pageRequest.size(), 0L, false);
    }
}
