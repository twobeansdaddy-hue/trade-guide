package com.tradeguide.dto.valuation;

import com.tradeguide.domain.trade.Currency;
import com.tradeguide.domain.valuation.CurrencyValuationTotals;
import com.tradeguide.domain.valuation.PortfolioValuation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 환율 변환 없이 통화별 합계를 나란히 담는다({@code totalsByCurrency}). 서로 다른
 * 통화의 값을 하나로 합친 단일 총액은 의미가 없으므로 제공하지 않는다.
 */
public class PortfolioValuationResponse {
    private final List<HoldingValuationResponse> holdingValuations;
    private final Map<Currency, CurrencyValuationTotalsResponse> totalsByCurrency;

    public PortfolioValuationResponse(
            List<HoldingValuationResponse> holdingValuations,
            Map<Currency, CurrencyValuationTotalsResponse> totalsByCurrency
    ) {
        this.holdingValuations = holdingValuations;
        this.totalsByCurrency = totalsByCurrency;
    }

    public static PortfolioValuationResponse from(PortfolioValuation valuation) {
        Map<Currency, CurrencyValuationTotalsResponse> totalsByCurrency = new LinkedHashMap<>();
        valuation.getTotalsByCurrency().forEach((currency, totals) ->
                totalsByCurrency.put(currency, CurrencyValuationTotalsResponse.from(totals)));

        return new PortfolioValuationResponse(
                valuation.getHoldingValuations().stream()
                        .map(HoldingValuationResponse::from)
                        .toList(),
                totalsByCurrency
        );
    }

    public List<HoldingValuationResponse> getHoldingValuations() {
        return holdingValuations;
    }

    public Map<Currency, CurrencyValuationTotalsResponse> getTotalsByCurrency() {
        return totalsByCurrency;
    }

    public static class CurrencyValuationTotalsResponse {
        private final BigDecimal totalPurchaseAmount;
        private final BigDecimal totalMarketValue;
        private final BigDecimal totalUnrealizedProfitLoss;
        private final BigDecimal totalReturnRate;

        public CurrencyValuationTotalsResponse(
                BigDecimal totalPurchaseAmount,
                BigDecimal totalMarketValue,
                BigDecimal totalUnrealizedProfitLoss,
                BigDecimal totalReturnRate
        ) {
            this.totalPurchaseAmount = totalPurchaseAmount;
            this.totalMarketValue = totalMarketValue;
            this.totalUnrealizedProfitLoss = totalUnrealizedProfitLoss;
            this.totalReturnRate = totalReturnRate;
        }

        public static CurrencyValuationTotalsResponse from(CurrencyValuationTotals totals) {
            return new CurrencyValuationTotalsResponse(
                    totals.getTotalPurchaseAmount().setScale(2, RoundingMode.HALF_UP),
                    totals.getTotalMarketValue().setScale(2, RoundingMode.HALF_UP),
                    totals.getTotalUnrealizedProfitLoss().setScale(2, RoundingMode.HALF_UP),
                    totals.getTotalReturnRate().setScale(2, RoundingMode.HALF_UP)
            );
        }

        public BigDecimal getTotalPurchaseAmount() {
            return totalPurchaseAmount;
        }

        public BigDecimal getTotalMarketValue() {
            return totalMarketValue;
        }

        public BigDecimal getTotalUnrealizedProfitLoss() {
            return totalUnrealizedProfitLoss;
        }

        public BigDecimal getTotalReturnRate() {
            return totalReturnRate;
        }
    }
}
