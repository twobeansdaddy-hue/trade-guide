package com.tradeguide.service.valuation;

import com.tradeguide.domain.trade.Currency;
import com.tradeguide.domain.valuation.CurrencyValuationTotals;
import com.tradeguide.domain.valuation.HoldingValuation;
import com.tradeguide.domain.valuation.PortfolioValuation;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PortfolioValuationCalculator {

    public PortfolioValuation calculate(List<HoldingValuation> holdingValuations) {
        Map<Currency, List<HoldingValuation>> holdingsByCurrency = holdingValuations.stream()
                .collect(Collectors.groupingBy(
                        holding -> holding.getMarket().getCurrency(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        Map<Currency, CurrencyValuationTotals> totalsByCurrency = new LinkedHashMap<>();
        for (Map.Entry<Currency, List<HoldingValuation>> entry : holdingsByCurrency.entrySet()) {
            totalsByCurrency.put(entry.getKey(), totalsFor(entry.getKey(), entry.getValue()));
        }

        return new PortfolioValuation(List.copyOf(holdingValuations), totalsByCurrency);
    }

    private CurrencyValuationTotals totalsFor(Currency currency, List<HoldingValuation> holdings) {
        BigDecimal totalPurchaseAmount = holdings.stream()
                .map(HoldingValuation::getPurchaseAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalMarketValue = holdings.stream()
                .map(HoldingValuation::getMarketValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalUnrealizedProfitLoss = holdings.stream()
                .map(HoldingValuation::getUnrealizedProfitLoss)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalReturnRate;
        if (totalPurchaseAmount.compareTo(BigDecimal.ZERO) == 0) {
            totalReturnRate = BigDecimal.ZERO;
        } else {
            totalReturnRate = totalUnrealizedProfitLoss
                    .multiply(BigDecimal.valueOf(100))
                    .divide(totalPurchaseAmount, 10, RoundingMode.HALF_UP);
        }

        return new CurrencyValuationTotals(
                currency, totalPurchaseAmount, totalMarketValue, totalUnrealizedProfitLoss, totalReturnRate);
    }
}
