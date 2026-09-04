package com.tradeguide.dto.broker;

import com.tradeguide.domain.broker.BrokerHoldingComparison;
import com.tradeguide.domain.broker.BrokerHoldingPreviewItem;
import com.tradeguide.domain.trade.Market;

import java.math.BigDecimal;

public record BrokerHoldingPreviewItemResponse(
        Market market,
        String ticker,
        BigDecimal brokerQuantity,
        BigDecimal brokerAveragePurchasePrice,
        BigDecimal tradeGuideQuantity,
        BrokerHoldingComparison comparison
) {
    static BrokerHoldingPreviewItemResponse from(BrokerHoldingPreviewItem item) {
        return new BrokerHoldingPreviewItemResponse(
                item.market(),
                item.ticker(),
                item.brokerQuantity(),
                item.brokerAveragePurchasePrice(),
                item.tradeGuideQuantity(),
                item.comparison()
        );
    }
}
