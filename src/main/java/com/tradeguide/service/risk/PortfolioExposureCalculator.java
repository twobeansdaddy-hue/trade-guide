package com.tradeguide.service.risk;

import com.tradeguide.domain.risk.HoldingExposure;
import com.tradeguide.domain.trade.Currency;
import com.tradeguide.domain.valuation.HoldingValuation;
import com.tradeguide.domain.valuation.PortfolioValuation;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 종목별 노출 비중은 현재 USD 보유 종목만 대상으로 계산한다. KRW 보유 종목은 환율 변환
 * 정책이 없어 USD 총액과 합산할 수 없고, 위험 한도({@code PortfolioRiskPolicy})도 아직
 * 국내 시장을 대상으로 설계되지 않았다 - KR 위험 관리는 별도 정책 결정이 필요하다.
 */
@Component
public class PortfolioExposureCalculator {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    public List<HoldingExposure> calculate(
            PortfolioValuation portfolioValuation
    ) {
        BigDecimal totalMarketValue = portfolioValuation.getTotalsFor(Currency.USD).getTotalMarketValue();

        if (totalMarketValue.signum() == 0) {
            return List.of();
        }

        return portfolioValuation.getHoldingValuations().stream()
                .filter(holding -> holding.getMarket().getCurrency() == Currency.USD)
                .map(holdingValuation -> createExposure(
                        holdingValuation,
                        totalMarketValue
                ))
                .toList();
    }

    private HoldingExposure createExposure(
            HoldingValuation holdingValuation,
            BigDecimal totalMarketValue
    ) {
        BigDecimal exposureRate = holdingValuation.getMarketValue()
                .multiply(ONE_HUNDRED)
                .divide(totalMarketValue, 2, RoundingMode.HALF_UP);

        return new HoldingExposure(
                holdingValuation.getMarket(),
                holdingValuation.getTicker(),
                holdingValuation.getMarketValue(),
                exposureRate
        );
    }
}
