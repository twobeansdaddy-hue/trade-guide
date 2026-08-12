package com.tradeguide.service.valuation;

import com.tradeguide.domain.holding.Holding;
import com.tradeguide.domain.market.MarketPrice;
import com.tradeguide.domain.valuation.HoldingValuation;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class HoldingValuationCalculator {

    public HoldingValuation calculate(Holding holding, MarketPrice marketPrice) {

        if (holding.getMarket() != marketPrice.getMarket()
                || !holding.getTicker().equals(marketPrice.getTicker())
        ) {
            throw new IllegalArgumentException("보유 종목과 현재가 종목이 일치하지 않습니다.");
        }

        BigDecimal averagePurchasePrice = holding.getAveragePurchasePrice();
        BigDecimal quantity = holding.getQuantity();
        BigDecimal currentPrice = marketPrice.getCurrentPrice();

        BigDecimal purchaseAmount = averagePurchasePrice.multiply(quantity);
        BigDecimal marketValue = currentPrice.multiply(quantity);
        BigDecimal unrealizedProfitLoss = marketValue.subtract(purchaseAmount);
        BigDecimal returnRate = unrealizedProfitLoss
                .multiply(BigDecimal.valueOf(100))
                .divide(purchaseAmount, 10, RoundingMode.HALF_UP);

        return new HoldingValuation(
                holding.getMarket(),
                holding.getTicker(),
                quantity,
                averagePurchasePrice,
                currentPrice,
                purchaseAmount,
                marketValue,
                unrealizedProfitLoss,
                returnRate
        );
    }
}
