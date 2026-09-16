package com.tradeguide.domain.valuation;

import com.tradeguide.domain.trade.Currency;

import java.util.List;
import java.util.Map;

/**
 * 포트폴리오 평가 결과다. 환율 변환을 하지 않으므로 통화별 합계({@link #totalsByCurrency})를
 * 서로 더하지 않고 별도로 보관·표시한다.
 */
public class PortfolioValuation {
    private final List<HoldingValuation> holdingValuations;
    private final Map<Currency, CurrencyValuationTotals> totalsByCurrency;

    public PortfolioValuation(
            List<HoldingValuation> holdingValuations,
            Map<Currency, CurrencyValuationTotals> totalsByCurrency
    ) {
        this.holdingValuations = holdingValuations;
        this.totalsByCurrency = totalsByCurrency;
    }

    public List<HoldingValuation> getHoldingValuations() {
        return holdingValuations;
    }

    public Map<Currency, CurrencyValuationTotals> getTotalsByCurrency() {
        return totalsByCurrency;
    }

    /** 그 통화의 보유 종목이 없으면 전부 0인 합계를 돌려준다 - 호출부의 null 분기를 없앤다. */
    public CurrencyValuationTotals getTotalsFor(Currency currency) {
        CurrencyValuationTotals totals = totalsByCurrency.get(currency);
        if (totals != null) {
            return totals;
        }
        return currency == Currency.USD ? CurrencyValuationTotals.EMPTY_USD : CurrencyValuationTotals.EMPTY_KRW;
    }
}
