package com.tradeguide.dto.strategy;

import com.tradeguide.domain.strategy.InvestmentTrack;
import com.tradeguide.domain.strategy.PortfolioCandidateAsset;
import com.tradeguide.domain.trade.Market;

import java.time.LocalDateTime;

public class PortfolioCandidateAssetResponse {

    private final Long id;
    private final Market market;
    private final String ticker;
    private final String displayName;
    private final InvestmentTrack investmentTrack;
    private final LocalDateTime createdAt;

    public PortfolioCandidateAssetResponse(
            Long id,
            Market market,
            String ticker,
            String displayName,
            InvestmentTrack investmentTrack,
            LocalDateTime createdAt
    ) {
        this.id = id;
        this.market = market;
        this.ticker = ticker;
        this.displayName = displayName;
        this.investmentTrack = investmentTrack;
        this.createdAt = createdAt;
    }

    public static PortfolioCandidateAssetResponse from(PortfolioCandidateAsset candidate) {
        return new PortfolioCandidateAssetResponse(
                candidate.getId(),
                candidate.getMarket(),
                candidate.getTicker(),
                candidate.getDisplayName(),
                candidate.getInvestmentTrack(),
                candidate.getCreatedAt()
        );
    }

    public Long getId() {
        return id;
    }

    public Market getMarket() {
        return market;
    }

    public String getTicker() {
        return ticker;
    }

    public String getDisplayName() {
        return displayName;
    }

    public InvestmentTrack getInvestmentTrack() {
        return investmentTrack;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
