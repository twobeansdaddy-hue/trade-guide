package com.tradeguide.domain.valuation;

import com.tradeguide.domain.trade.Currency;

import java.math.BigDecimal;

/**
 * 한 통화로만 이뤄진 보유 종목들의 평가 합계다. 환율 변환을 하지 않으므로
 * 서로 다른 통화의 {@code CurrencyValuationTotals}를 더해서는 안 된다.
 */
public class CurrencyValuationTotals {

    public static final CurrencyValuationTotals EMPTY_USD = new CurrencyValuationTotals(
            Currency.USD, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    public static final CurrencyValuationTotals EMPTY_KRW = new CurrencyValuationTotals(
            Currency.KRW, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

    private final Currency currency;
    private final BigDecimal totalPurchaseAmount;
    private final BigDecimal totalMarketValue;
    private final BigDecimal totalUnrealizedProfitLoss;
    private final BigDecimal totalReturnRate;

    public CurrencyValuationTotals(
            Currency currency,
            BigDecimal totalPurchaseAmount,
            BigDecimal totalMarketValue,
            BigDecimal totalUnrealizedProfitLoss,
            BigDecimal totalReturnRate
    ) {
        this.currency = currency;
        this.totalPurchaseAmount = totalPurchaseAmount;
        this.totalMarketValue = totalMarketValue;
        this.totalUnrealizedProfitLoss = totalUnrealizedProfitLoss;
        this.totalReturnRate = totalReturnRate;
    }

    public Currency getCurrency() {
        return currency;
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
