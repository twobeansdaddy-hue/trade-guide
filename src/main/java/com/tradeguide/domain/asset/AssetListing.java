package com.tradeguide.domain.asset;

import com.tradeguide.domain.trade.Market;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "asset_listings",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_asset_listings_market_ticker",
                columnNames = {"market", "ticker"}
        )
)
public class AssetListing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Market market;

    @Column(nullable = false)
    private String ticker;

    @Column(nullable = false)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ListingStatus listingStatus;

    protected AssetListing() {
    }

    public AssetListing(
            Market market,
            String ticker,
            String displayName,
            ListingStatus listingStatus
    ) {
        this.market = market;
        this.ticker = ticker;
        this.displayName = displayName;
        this.listingStatus = listingStatus;
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

    public ListingStatus getListingStatus() {
        return listingStatus;
    }
}
