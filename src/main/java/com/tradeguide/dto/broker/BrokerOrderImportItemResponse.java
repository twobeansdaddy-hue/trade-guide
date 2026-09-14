package com.tradeguide.dto.broker;

import com.tradeguide.domain.broker.BrokerOrderImportItem;
import com.tradeguide.domain.broker.BrokerOrderLifecycle;
import com.tradeguide.domain.broker.BrokerOrderSide;
import com.tradeguide.domain.broker.BrokerOrderSkipReason;
import com.tradeguide.domain.broker.BrokerOrderStagingStatus;
import com.tradeguide.domain.trade.Market;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 스테이징된 주문 한 건의 응답이다. 계좌번호·자격 증명·증권사 원문 응답은 담지 않는다.
 *
 * <p>{@code averageFilledPrice}가 평균 체결가라는 사실과 {@code filledAt}이 최종 체결 시각이라는
 * 사실은 필드 이름에 남긴다. 개별 체결 단위 값을 기대하고 쓰면 나중에 실현손익 계산이 조용히 틀린다.
 */
public record BrokerOrderImportItemResponse(
        Long id,
        String externalOrderId,
        Market market,
        String ticker,
        String displayName,
        BrokerOrderSide orderSide,
        String providerStatusCode,
        String providerOrderType,
        String providerTimeInForce,
        BrokerOrderLifecycle lifecycle,
        BigDecimal orderedQuantity,
        BigDecimal filledQuantity,
        BigDecimal averageFilledPrice,
        BigDecimal filledAmount,
        BigDecimal commission,
        BigDecimal tax,
        String currencyCode,
        Instant orderedAt,
        Instant filledAt,
        LocalDate settlementDate,
        BrokerOrderStagingStatus stagingStatus,
        BrokerOrderSkipReason skipReasonCode,
        boolean amountMismatch,
        boolean feeUnknown,
        boolean buyTax
) {
    public static BrokerOrderImportItemResponse from(BrokerOrderImportItem item) {
        return new BrokerOrderImportItemResponse(
                item.getId(),
                item.getExternalOrderId(),
                item.getMarket(),
                item.getTicker(),
                item.getDisplayName(),
                item.getOrderSide(),
                item.getProviderStatusCode(),
                item.getProviderOrderType(),
                item.getProviderTimeInForce(),
                item.getLifecycle(),
                item.getOrderedQuantity(),
                item.getFilledQuantity(),
                item.getAverageFilledPrice(),
                item.getFilledAmount(),
                item.getCommission(),
                item.getTax(),
                item.getCurrencyCode(),
                item.getOrderedAt(),
                item.getFilledAt(),
                item.getSettlementDate(),
                item.getStagingStatus(),
                item.getSkipReasonCode(),
                item.isAmountMismatch(),
                item.isFeeUnknown(),
                item.isBuyTax()
        );
    }
}
