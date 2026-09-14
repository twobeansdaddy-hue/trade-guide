package com.tradeguide.domain.broker;

import com.tradeguide.domain.trade.Market;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;

/**
 * 증권사가 보고한 주문 1건이다. 체결 1건이 아니라 <b>주문 1건에 집계된 체결 결과</b>이며,
 * 읽기 전용 조회 값이다. 이 타입은 매매 원장을 만들지도 바꾸지도 않는다.
 *
 * <p>{@code averageFilledPrice}는 평균 체결가이고 {@code filledAt}은 최종 체결 시각이다.
 * 개별 체결 시각·개별 체결가는 제공자가 주지 않으므로 복원할 수 없다.
 *
 * <p>{@code providerStatusCode}·{@code providerOrderType}·{@code providerTimeInForce}는
 * 제공자 원문 값을 감사 목적으로 보존한 것이다. 판단은 {@link #lifecycle()}로 한다.
 */
public record BrokerOrderRecord(
        String externalOrderId,
        Market market,
        String ticker,
        BrokerOrderSide side,
        BrokerOrderLifecycle lifecycle,
        String providerStatusCode,
        String providerOrderType,
        String providerTimeInForce,
        String currencyCode,
        BigDecimal orderedQuantity,
        BigDecimal filledQuantity,
        BigDecimal averageFilledPrice,
        BigDecimal filledAmount,
        BigDecimal commission,
        BigDecimal tax,
        Instant orderedAt,
        Instant filledAt,
        LocalDate settlementDate
) {
    public BrokerOrderRecord {
        if (externalOrderId == null || externalOrderId.isBlank()) {
            throw new IllegalArgumentException("증권사 주문 식별자는 필수입니다.");
        }
        if (market == null || ticker == null || ticker.isBlank()) {
            throw new IllegalArgumentException("증권사 주문의 시장과 종목 코드는 필수입니다.");
        }
        if (side == null || lifecycle == null) {
            throw new IllegalArgumentException("증권사 주문의 방향과 라이프사이클 판정은 필수입니다.");
        }
        if (providerStatusCode == null || providerStatusCode.isBlank()) {
            throw new IllegalArgumentException("증권사 주문의 제공자 상태 코드는 필수입니다.");
        }
        if (currencyCode == null || currencyCode.isBlank()) {
            throw new IllegalArgumentException("증권사 주문의 통화 코드는 필수입니다.");
        }
        if (orderedQuantity == null || filledQuantity == null) {
            throw new IllegalArgumentException("증권사 주문의 주문 수량과 체결 수량은 필수입니다.");
        }
        if (orderedQuantity.signum() < 0 || filledQuantity.signum() < 0) {
            throw new IllegalArgumentException("증권사 주문의 수량은 0 이상이어야 합니다.");
        }
        if (orderedAt == null) {
            throw new IllegalArgumentException("증권사 주문의 주문 시각은 필수입니다.");
        }

        externalOrderId = externalOrderId.trim();
        ticker = ticker.trim().toUpperCase(Locale.ROOT);
        currencyCode = currencyCode.trim().toUpperCase(Locale.ROOT);
    }

    /** 실제 체결분이 있는지 여부. 상태와 무관하게 수량만 본다. */
    public boolean hasFill() {
        return filledQuantity.signum() > 0;
    }

    /**
     * 원장 반영 후보인지 여부다. 종료 상태 + 체결분 + 평균 체결가 + 최종 체결 시각이 모두 있어야 한다.
     * 체결 시각이 없는 건은 결제 예정일로 대체하지 않는다. 시각을 합성하지 않기 때문이다.
     *
     * <p>이 판정은 후보 여부만 말한다. 실제 반영 여부는 중복·대조·승인 단계에서 결정한다.
     */
    public boolean isLedgerEligible() {
        return lifecycle == BrokerOrderLifecycle.TERMINAL_WITH_FILL
                && hasFill()
                && averageFilledPrice != null
                && filledAt != null;
    }
}
