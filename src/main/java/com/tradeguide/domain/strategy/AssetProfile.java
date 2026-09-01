package com.tradeguide.domain.strategy;

import com.tradeguide.domain.asset.AssetListing;
import com.tradeguide.domain.asset.ListingStatus;
import com.tradeguide.domain.trade.Market;
import jakarta.persistence.*;

@Entity
@Table(
        name = "asset_profiles",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_asset_profiles_listing",
                columnNames = "listing_id"
        )
)
public class AssetProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(cascade = CascadeType.ALL, optional = false)
    @JoinColumn(name = "listing_id", nullable = false)
    private AssetListing listing;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InvestmentTrack investmentTrack;

    protected AssetProfile() {
    }

    public AssetProfile(
            Market market,
            String ticker,
            InvestmentTrack investmentTrack) {
        this.listing = new AssetListing(
                market,
                ticker,
                ticker,
                ListingStatus.ACTIVE
        );
        this.investmentTrack = investmentTrack;
    }

    public Long getId() {
        return id;
    }

    public Market getMarket() {
        return listing.getMarket();
    }

    public String getTicker() {
        return listing.getTicker();
    }

    public AssetListing getListing() {
        return listing;
    }

    public InvestmentTrack getInvestmentTrack() {
        return investmentTrack;
    }
}
