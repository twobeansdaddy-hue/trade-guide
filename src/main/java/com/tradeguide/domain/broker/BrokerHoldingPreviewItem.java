package com.tradeguide.domain.broker;

import com.tradeguide.domain.trade.Market;

import java.math.BigDecimal;

/**
 * 종목 한 건에 대한 증권사 보유 수량과 Trade Guide 보유 수량의 비교 결과다.
 * 한쪽에만 존재하는 종목은 해당 수량이 {@code null}이다.
 */
public record BrokerHoldingPreviewItem(
        Market market,
        String ticker,
        String displayName,
        BigDecimal brokerQuantity,
        BigDecimal brokerAveragePurchasePrice,
        BigDecimal tradeGuideQuantity,
        BrokerHoldingComparison comparison
) {
    public BrokerHoldingPreviewItem(
            Market market,
            String ticker,
            BigDecimal brokerQuantity,
            BigDecimal brokerAveragePurchasePrice,
            BigDecimal tradeGuideQuantity,
            BrokerHoldingComparison comparison
    ) {
        this(market, ticker, ticker, brokerQuantity, brokerAveragePurchasePrice, tradeGuideQuantity, comparison);
    }
}
