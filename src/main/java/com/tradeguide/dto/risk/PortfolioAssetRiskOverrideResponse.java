package com.tradeguide.dto.risk;

import com.tradeguide.domain.risk.PortfolioAssetRiskOverride;
import com.tradeguide.domain.trade.Market;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 포트폴리오 종목별 손절 기준 재정의 한 건의 응답이다. {@code stopLossRatio}는 백분율
 * 문자열로 변환하지 않고 비율(0~1 사이 소수) 그대로 반환한다.
 */
public class PortfolioAssetRiskOverrideResponse {

    private final Market market;
    private final String ticker;
    private final BigDecimal stopLossRatio;
    private final LocalDateTime updatedAt;

    public PortfolioAssetRiskOverrideResponse(
            Market market,
            String ticker,
            BigDecimal stopLossRatio,
            LocalDateTime updatedAt
    ) {
        this.market = market;
        this.ticker = ticker;
        this.stopLossRatio = stopLossRatio;
        this.updatedAt = updatedAt;
    }

    public static PortfolioAssetRiskOverrideResponse from(PortfolioAssetRiskOverride override) {
        return new PortfolioAssetRiskOverrideResponse(
                override.getMarket(),
                override.getTicker(),
                override.getStopLossRatio(),
                override.getUpdatedAt()
        );
    }

    public Market getMarket() {
        return market;
    }

    public String getTicker() {
        return ticker;
    }

    public BigDecimal getStopLossRatio() {
        return stopLossRatio;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
