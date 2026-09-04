package com.tradeguide.domain.broker;

import com.tradeguide.domain.trade.Market;

import java.math.BigDecimal;

/**
 * 증권사가 보고한 보유 종목 한 건이다. 읽기 전용 미리보기 값이며
 * Trade Guide 매매 기록으로 저장되지 않는다.
 */
public record BrokerHolding(
        Market market,
        String ticker,
        String displayName,
        BigDecimal quantity,
        BigDecimal averagePurchasePrice
) {
    public BrokerHolding(Market market, String ticker, BigDecimal quantity, BigDecimal averagePurchasePrice) {
        this(market, ticker, ticker, quantity, averagePurchasePrice);
    }

    public BrokerHolding {
        if (market == null || ticker == null || ticker.isBlank()) {
            throw new IllegalArgumentException("증권사 보유 종목 정보가 올바르지 않습니다.");
        }
        if (displayName == null || displayName.isBlank()) {
            displayName = ticker;
        }
        if (quantity == null || averagePurchasePrice == null) {
            throw new IllegalArgumentException("증권사 보유 종목 수량과 평균 매입가는 필수입니다.");
        }
    }
}
