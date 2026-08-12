package com.tradeguide.dto.strategy;

import com.tradeguide.domain.strategy.InvestmentTrack;
import com.tradeguide.domain.trade.Market;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class AssetProfileCreateRequest {

    @NotNull(message = "시장은 필수입니다.")
    private final Market market;

    @NotBlank(message = "티커는 필수입니다.")
    private final String ticker;

    @NotNull(message = "투자 트랙은 필수입니다.")
    private final InvestmentTrack investmentTrack;

    public AssetProfileCreateRequest(
            Market market,
            String ticker,
            InvestmentTrack investmentTrack
    ) {
        this.market = market;
        this.ticker = ticker;
        this.investmentTrack = investmentTrack;
    }

    public Market getMarket() {
        return market;
    }

    public String getTicker() {
        return ticker;
    }

    public InvestmentTrack getInvestmentTrack() {
        return investmentTrack;
    }
}
