package com.tradeguide.dto.strategy;

import com.tradeguide.domain.strategy.InvestmentTrack;
import jakarta.validation.constraints.NotNull;

public class PortfolioAssetStrategyProfileUpsertRequest {

    @NotNull(message = "투자 트랙은 필수입니다.")
    private final InvestmentTrack investmentTrack;

    public PortfolioAssetStrategyProfileUpsertRequest(InvestmentTrack investmentTrack) {
        this.investmentTrack = investmentTrack;
    }

    public InvestmentTrack getInvestmentTrack() {
        return investmentTrack;
    }
}
