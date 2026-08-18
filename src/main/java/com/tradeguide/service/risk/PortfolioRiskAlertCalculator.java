package com.tradeguide.service.risk;

import com.tradeguide.domain.risk.HoldingExposure;
import com.tradeguide.domain.risk.PortfolioRiskAlert;
import com.tradeguide.domain.risk.PortfolioRiskPolicy;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class PortfolioRiskAlertCalculator {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    public List<PortfolioRiskAlert> calculate(
            List<HoldingExposure> exposures,
            PortfolioRiskPolicy riskPolicy
    ) {
        BigDecimal maxExposureRate = riskPolicy.getMaxSingleAssetExposureRatio().multiply(ONE_HUNDRED);

        return exposures.stream()
                .filter(exposure -> exposure.getExposureRate().compareTo(maxExposureRate) > 0)
                .map(exposure -> new PortfolioRiskAlert(
                        exposure.getMarket(),
                        exposure.getTicker(),
                        exposure.getExposureRate(),
                        maxExposureRate,
                        "종목별 최대 노출 비율을 초과했습니다."))
                .toList();
    }
}
