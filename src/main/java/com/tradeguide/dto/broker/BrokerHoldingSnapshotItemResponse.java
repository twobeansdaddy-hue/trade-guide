package com.tradeguide.dto.broker;

import com.tradeguide.domain.broker.PortfolioBrokerHoldingSnapshotItem;
import com.tradeguide.domain.trade.Market;

import java.math.BigDecimal;

public record BrokerHoldingSnapshotItemResponse(
        Market market,
        String ticker,
        String displayName,
        BigDecimal quantity,
        BigDecimal averagePurchasePrice
) {
    static BrokerHoldingSnapshotItemResponse from(PortfolioBrokerHoldingSnapshotItem item) {
        return new BrokerHoldingSnapshotItemResponse(
                item.getMarket(),
                item.getTicker(),
                item.getDisplayName(),
                item.getQuantity(),
                item.getAveragePurchasePrice()
        );
    }
}
