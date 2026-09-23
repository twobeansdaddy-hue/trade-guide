package com.tradeguide.domain.strategy;

import com.tradeguide.domain.market.MarketDataProvider;
import com.tradeguide.domain.trade.Market;
import jakarta.persistence.Column;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

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

    @Column(name = "adjusted_requested")
    private Boolean adjustedRequested;

    @ElementCollection
    @CollectionTable(name = "premarket_guide_candle_page_receipts",
            joinColumns = @JoinColumn(name = "candle_evidence_id"))
    @OrderColumn(name = "page_index")
    @Column(name = "response_received_at", nullable = false)
    private List<Instant> pageReceivedAt = new ArrayList<>();

    protected PremarketGuideCandleEvidence() {
    }

    public PremarketGuideCandleEvidence(PremarketGuideScope scope, Market market, String ticker,
                                       MarketDataProvider candleProvider, Instant loadCompletedAt,
                                       String candleSha256, int candleCount, LocalDate dataAsOf) {
        this(scope, market, ticker, candleProvider, loadCompletedAt,
                candleSha256, candleCount, dataAsOf, List.of(), null);
    }

    public PremarketGuideCandleEvidence(PremarketGuideScope scope, Market market, String ticker,
                                       MarketDataProvider candleProvider, Instant loadCompletedAt,
                                       String candleSha256, int candleCount, LocalDate dataAsOf,
                                       List<Instant> pageReceivedAt, Boolean adjustedRequested) {
        if (scope == null || market == null || ticker == null || ticker.isBlank()
                || candleProvider == null || loadCompletedAt == null || candleSha256 == null
                || candleSha256.length() != 64 || candleCount < 1 || dataAsOf == null
                || pageReceivedAt == null || (pageReceivedAt.isEmpty() != (adjustedRequested == null))) {
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
        this.pageReceivedAt.addAll(pageReceivedAt);
        this.adjustedRequested = adjustedRequested;
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
        adjustedRequested = other.adjustedRequested;
        pageReceivedAt.clear();
        pageReceivedAt.addAll(other.pageReceivedAt);
    }

    public PremarketGuideScope getScope() { return scope; }
    public Market getMarket() { return market; }
    public String getTicker() { return ticker; }
    public MarketDataProvider getCandleProvider() { return candleProvider; }
    public Instant getLoadCompletedAt() { return loadCompletedAt; }
    public String getCandleSha256() { return candleSha256; }
    public int getCandleCount() { return candleCount; }
    public LocalDate getDataAsOf() { return dataAsOf; }
    public List<Instant> getPageReceivedAt() { return List.copyOf(pageReceivedAt); }
    public Boolean getAdjustedRequested() { return adjustedRequested; }
}
