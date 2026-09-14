package com.tradeguide.dto.broker;

import com.tradeguide.domain.broker.BrokerHistoryPage;

import java.util.List;
import java.util.function.Function;

/**
 * 증권사 이력 한 페이지의 응답이다.
 *
 * <p>항목만 돌려주면 화면은 "이게 전부인지" 알 수 없다. 그래서 현재 페이지, 페이지 크기,
 * 전체 건수, 다음 페이지 여부를 항상 함께 담는다. {@code hasNext}는 전체 건수에서 다시
 * 계산한 값이 아니라 조회가 알려 준 값을 그대로 옮긴 것이다.
 */
public record BrokerHistoryPageResponse<T>(
        List<T> items,
        int page,
        int size,
        long totalElements,
        boolean hasNext
) {
    public static <E, T> BrokerHistoryPageResponse<T> from(
            BrokerHistoryPage<E> page,
            Function<E, T> mapper
    ) {
        return new BrokerHistoryPageResponse<>(
                page.items().stream().map(mapper).toList(),
                page.page(),
                page.size(),
                page.totalElements(),
                page.hasNext()
        );
    }
}
