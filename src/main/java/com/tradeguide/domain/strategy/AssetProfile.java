package com.tradeguide.domain.strategy;

import com.tradeguide.domain.trade.Market;
import jakarta.persistence.*;

@Entity
@Table(
        name = "asset_profiles",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_asset_profiles_market_ticker",
                columnNames = {"market", "ticker"}
        )
)
public class AssetProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Market market;

    @Column(nullable = false)
    private String ticker;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InvestmentTrack investmentTrack;

    protected AssetProfile() {
    }

    public AssetProfile(
            Market market,
            String ticker,
            InvestmentTrack investmentTrack) {
        this.market = market;
        this.ticker = ticker;
        this.investmentTrack = investmentTrack;
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

    public InvestmentTrack getInvestmentTrack() {
        return investmentTrack;
    }
}
