package com.tradeguide.domain.broker;

import com.tradeguide.domain.trade.Market;

import java.math.BigDecimal;

/**
 * 종목 한 건에 대한 증권사 보유 수량과 Trade Guide 보유 수량의 비교 결과다.
 * 한쪽에만 존재하는 종목은 해당 수량이 {@code null}이다. {@code snapshotItemId}는
 * 저장된 스냅샷 항목에서 비교한 경우에만 채워지며, Trade Guide에만 있는 항목이거나
 * 저장되지 않은 실시간 미리보기에서는 {@code null}이다.
 */
public record BrokerHoldingPreviewItem(
        Market market,
        String ticker,
        String displayName,
        BigDecimal brokerQuantity,
        BigDecimal brokerAveragePurchasePrice,
        BigDecimal tradeGuideQuantity,
        BrokerHoldingComparison comparison,
        Long snapshotItemId
) {
    public BrokerHoldingPreviewItem(
            Market market,
            String ticker,
            String displayName,
            BigDecimal brokerQuantity,
            BigDecimal brokerAveragePurchasePrice,
            BigDecimal tradeGuideQuantity,
            BrokerHoldingComparison comparison
    ) {
        this(market, ticker, displayName, brokerQuantity, brokerAveragePurchasePrice, tradeGuideQuantity, comparison, null);
    }

    public BrokerHoldingPreviewItem(
            Market market,
            String ticker,
            BigDecimal brokerQuantity,
            BigDecimal brokerAveragePurchasePrice,
            BigDecimal tradeGuideQuantity,
            BrokerHoldingComparison comparison
    ) {
        this(market, ticker, ticker, brokerQuantity, brokerAveragePurchasePrice, tradeGuideQuantity, comparison, null);
    }

    public BrokerHoldingPreviewItem withSnapshotItemId(Long snapshotItemId) {
        return new BrokerHoldingPreviewItem(
                market, ticker, displayName, brokerQuantity, brokerAveragePurchasePrice, tradeGuideQuantity, comparison, snapshotItemId
        );
    }
}
