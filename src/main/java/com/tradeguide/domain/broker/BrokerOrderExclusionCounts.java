package com.tradeguide.domain.broker;

/**
 * 주문 이력 한 페이지에서 조회 건수와 제외 건수를 사유별로 보고한다.
 * 조용한 누락을 막는 것이 이 타입의 존재 이유다. 어댑터는 표현할 수 없는 주문을 버릴 때
 * 반드시 사유별 건수를 남긴다.
 *
 * <p>제외 사유는 서로 배타적이며 다음 불변식을 만족한다.
 *
 * <pre>{@code fetchedCount = 페이지에 담긴 레코드 수 + unsupportedMarketCount + unsupportedCurrencyCount}</pre>
 *
 * <p>{@code unknownEnumCount}는 제외 사유가 아니라 <b>신호</b>다. 제공자가 이 클라이언트가 모르는
 * enum 값을 보낸 주문 수이며, 제외 여부와 무관하게 센다. 제공자 명세 변경을 감지하는 용도다.
 */
public record BrokerOrderExclusionCounts(
        int fetchedCount,
        int unsupportedMarketCount,
        int unsupportedCurrencyCount,
        int unknownEnumCount
) {
    public BrokerOrderExclusionCounts {
        if (fetchedCount < 0
                || unsupportedMarketCount < 0
                || unsupportedCurrencyCount < 0
                || unknownEnumCount < 0) {
            throw new IllegalArgumentException("주문 이력 집계 건수는 0 이상이어야 합니다.");
        }
    }

    public static BrokerOrderExclusionCounts none(int fetchedCount) {
        return new BrokerOrderExclusionCounts(fetchedCount, 0, 0, 0);
    }

    /** 제외 사유별 건수의 합. {@code unknownEnumCount}는 제외가 아니므로 포함하지 않는다. */
    public int excludedCount() {
        return unsupportedMarketCount + unsupportedCurrencyCount;
    }
}
