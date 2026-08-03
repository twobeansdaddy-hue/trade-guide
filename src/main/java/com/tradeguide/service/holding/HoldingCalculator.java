package com.tradeguide.service.holding;

import com.tradeguide.domain.holding.Holding;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.domain.trade.TradeTransaction;
import com.tradeguide.domain.trade.TradeType;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class HoldingCalculator {

    private static final int CALCULATION_SCALE = 10;

    public List<Holding> calculate(List<TradeTransaction> transactions) {
        Map<String, HoldingState> holdingStates = new LinkedHashMap<>();

        transactions.stream()
                .sorted(Comparator.comparing(TradeTransaction::getTradedAt))
                .forEach(transaction -> {
                    String key = createKey(transaction);

                    HoldingState holdingState = holdingStates.computeIfAbsent(
                            key,
                            ignored -> new HoldingState(
                                    transaction.getMarket(),
                                    transaction.getTicker()
                            )
                    );

                    if (transaction.getTradeType() == TradeType.BUY) {
                        holdingState.buy(transaction);
                    } else {
                        holdingState.sell(transaction);
                    }
                });

        return holdingStates.values().stream()
                .filter(HoldingState::hasQuantity)
                .map(HoldingState::toHolding)
                .toList();
    }

    private String createKey(TradeTransaction transaction) {
        return transaction.getMarket() + ":" + transaction.getTicker();
    }

    private static class HoldingState {

        private final Market market;
        private final String ticker;
        private BigDecimal quantity = BigDecimal.ZERO;
        private BigDecimal averagePurchasePrice = BigDecimal.ZERO;

        private HoldingState(
                Market market,
                String ticker
        ) {
            this.market = market;
            this.ticker = ticker;
        }

        private void buy(TradeTransaction transaction) {
            BigDecimal existingTotalCost =
                    quantity.multiply(averagePurchasePrice);

            BigDecimal purchaseCost = transaction.getExecutedPrice()
                    .multiply(transaction.getQuantity())
                    .add(transaction.getFee());

            BigDecimal newQuantity =
                    quantity.add(transaction.getQuantity());

            averagePurchasePrice = existingTotalCost
                    .add(purchaseCost)
                    .divide(
                            newQuantity,
                            CALCULATION_SCALE,
                            RoundingMode.HALF_UP
                    );

            quantity = newQuantity;
        }

        private void sell(TradeTransaction transaction) {
            if (quantity.compareTo(transaction.getQuantity()) < 0) {
                throw new IllegalArgumentException(
                        "매도 수량이 보유 수량보다 많습니다."
                );
            }

            quantity = quantity.subtract(transaction.getQuantity());

            if (quantity.compareTo(BigDecimal.ZERO) == 0) {
                averagePurchasePrice = BigDecimal.ZERO;
            }
        }

        private boolean hasQuantity() {
            return quantity.compareTo(BigDecimal.ZERO) > 0;
        }

        private Holding toHolding() {
            return new Holding(
                    market,
                    ticker,
                    quantity,
                    averagePurchasePrice
            );
        }
    }
}