package com.tradeguide.domain.strategy;

import com.tradeguide.domain.market.MarketDataProvider;
import com.tradeguide.domain.trade.Market;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "premarket_guide_candle_evidence", uniqueConstraints = @UniqueConstraint(
        name = "uk_premarket_guide_candle_evidence_asset",
        columnNames = {"snapshot_id", "scope", "market", "ticker"}))
public class PremarketGuideCandleEvidence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "snapshot_id", nullable = false)
    private PremarketGuideSnapshot snapshot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PremarketGuideScope scope;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Market market;

    @Column(nullable = false, length = 100)
    private String ticker;

    @Enumerated(EnumType.STRING)
    @Column(name = "candle_provider", nullable = false, length = 50)
    private MarketDataProvider candleProvider;

    @Column(name = "load_completed_at", nullable = false)
    private Instant loadCompletedAt;

    @Column(name = "candle_sha256", nullable = false, length = 64)
    private String candleSha256;

    @Column(name = "candle_count", nullable = false)
    private int candleCount;

    @Column(name = "data_as_of", nullable = false)
    private LocalDate dataAsOf;

    protected PremarketGuideCandleEvidence() {
    }

    public PremarketGuideCandleEvidence(PremarketGuideScope scope, Market market, String ticker,
                                       MarketDataProvider candleProvider, Instant loadCompletedAt,
                                       String candleSha256, int candleCount, LocalDate dataAsOf) {
        if (scope == null || market == null || ticker == null || ticker.isBlank()
                || candleProvider == null || loadCompletedAt == null || candleSha256 == null
                || candleSha256.length() != 64 || candleCount < 1 || dataAsOf == null) {
            throw new IllegalArgumentException("장전 가이드 시세 근거가 올바르지 않습니다.");
        }
        this.scope = scope;
        this.market = market;
        this.ticker = ticker;
        this.candleProvider = candleProvider;
        this.loadCompletedAt = loadCompletedAt;
        this.candleSha256 = candleSha256;
        this.candleCount = candleCount;
        this.dataAsOf = dataAsOf;
    }

    void assignSnapshot(PremarketGuideSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    boolean hasAssetKey(PremarketGuideCandleEvidence other) {
        return scope == other.scope && market == other.market && ticker.equals(other.ticker);
    }

    boolean matches(PremarketGuideItem item, MarketDataProvider provider) {
        return scope == item.getScope() && market == item.getMarket()
                && ticker.equals(item.getTicker()) && candleProvider == provider
                && dataAsOf.equals(item.getDataAsOf());
    }

    void refreshFrom(PremarketGuideCandleEvidence other) {
        candleProvider = other.candleProvider;
        loadCompletedAt = other.loadCompletedAt;
        candleSha256 = other.candleSha256;
        candleCount = other.candleCount;
        dataAsOf = other.dataAsOf;
    }

    public PremarketGuideScope getScope() { return scope; }
    public Market getMarket() { return market; }
    public String getTicker() { return ticker; }
    public MarketDataProvider getCandleProvider() { return candleProvider; }
    public Instant getLoadCompletedAt() { return loadCompletedAt; }
    public String getCandleSha256() { return candleSha256; }
    public int getCandleCount() { return candleCount; }
    public LocalDate getDataAsOf() { return dataAsOf; }
}
