package com.tradeguide.service.broker;

import com.tradeguide.domain.broker.BrokerHolding;
import com.tradeguide.domain.broker.BrokerHoldingComparison;
import com.tradeguide.domain.broker.BrokerHoldingPreviewItem;
import com.tradeguide.domain.holding.Holding;
import com.tradeguide.domain.trade.Market;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 증권사 보유 종목과 Trade Guide 매매 기록에서 계산한 보유 종목의 차이를 정리한다.
 * 비교 결과는 사용자가 검토할 정보이며, 어느 쪽 데이터도 수정하지 않는다.
 */
@Component
public class BrokerHoldingPreviewCalculator {

    public List<BrokerHoldingPreviewItem> compare(
            List<BrokerHolding> brokerHoldings,
            List<Holding> tradeGuideHoldings
    ) {
        Map<AssetKey, BrokerHolding> brokerByAsset = new LinkedHashMap<>();
        for (BrokerHolding brokerHolding : brokerHoldings) {
            brokerByAsset.put(toKey(brokerHolding.market(), brokerHolding.ticker()), brokerHolding);
        }

        Map<AssetKey, Holding> tradeGuideByAsset = new LinkedHashMap<>();
        for (Holding holding : tradeGuideHoldings) {
            tradeGuideByAsset.put(toKey(holding.getMarket(), holding.getTicker()), holding);
        }

        List<BrokerHoldingPreviewItem> items = new ArrayList<>();

        for (Map.Entry<AssetKey, BrokerHolding> entry : brokerByAsset.entrySet()) {
            BrokerHolding brokerHolding = entry.getValue();
            Holding tradeGuideHolding = tradeGuideByAsset.get(entry.getKey());

            items.add(new BrokerHoldingPreviewItem(
                    brokerHolding.market(),
                    brokerHolding.ticker(),
                    brokerHolding.quantity(),
                    brokerHolding.averagePurchasePrice(),
                    tradeGuideHolding == null ? null : tradeGuideHolding.getQuantity(),
                    compareQuantity(brokerHolding.quantity(), tradeGuideHolding)
            ));
        }

        for (Map.Entry<AssetKey, Holding> entry : tradeGuideByAsset.entrySet()) {
            if (brokerByAsset.containsKey(entry.getKey())) {
                continue;
            }

            Holding holding = entry.getValue();
            items.add(new BrokerHoldingPreviewItem(
                    holding.getMarket(),
                    holding.getTicker(),
                    null,
                    null,
                    holding.getQuantity(),
                    BrokerHoldingComparison.ONLY_IN_TRADE_GUIDE
            ));
        }

        items.sort(Comparator
                .comparing(BrokerHoldingPreviewItem::market)
                .thenComparing(BrokerHoldingPreviewItem::ticker));

        return List.copyOf(items);
    }

    private BrokerHoldingComparison compareQuantity(BigDecimal brokerQuantity, Holding tradeGuideHolding) {
        if (tradeGuideHolding == null) {
            return BrokerHoldingComparison.ONLY_IN_BROKER;
        }

        return brokerQuantity.compareTo(tradeGuideHolding.getQuantity()) == 0
                ? BrokerHoldingComparison.MATCHED
                : BrokerHoldingComparison.QUANTITY_MISMATCH;
    }

    private AssetKey toKey(Market market, String ticker) {
        return new AssetKey(market, ticker.trim().toUpperCase(Locale.ROOT));
    }

    private record AssetKey(Market market, String ticker) {
    }
}
