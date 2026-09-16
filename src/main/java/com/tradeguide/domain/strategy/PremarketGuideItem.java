package com.tradeguide.domain.strategy;

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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Entity
@Table(name = "premarket_guide_items")
public class PremarketGuideItem {

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
    private PremarketGuideItemStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Market market;

    @Column(nullable = false, length = 100)
    private String ticker;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private StrategyAction action;

    @Column(length = 2000)
    private String reason;

    @Column(name = "reference_price", precision = 19, scale = 6)
    private BigDecimal referencePrice;

    @Column(name = "data_as_of")
    private LocalDate dataAsOf;

    @Column(name = "strategy_id", length = 100)
    private String strategyId;

    @Column(name = "strategy_version", length = 50)
    private String strategyVersion;

    @Column(length = 50)
    private String confidence;

    @Column(name = "caveats", columnDefinition = "TEXT")
    private String caveats;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private StrategyTrend trend;

    @Enumerated(EnumType.STRING)
    @Column(name = "signal_event", length = 30)
    private StrategySignalEvent signalEvent;

    @Column(name = "weeks_since_cross")
    private Integer weeksSinceCross;

    @Column(name = "entry_timing_status", length = 50)
    private String entryTimingStatus;

    @Column(name = "entry_timing_message", length = 2000)
    private String entryTimingMessage;

    @Column(name = "stop_loss_status", length = 50)
    private String stopLossStatus;

    @Column(name = "stop_loss_ratio", precision = 8, scale = 6)
    private BigDecimal stopLossRatio;

    @Column(name = "stop_loss_price", precision = 19, scale = 6)
    private BigDecimal stopLossPrice;

    @Column(name = "stop_loss_message", length = 2000)
    private String stopLossMessage;

    @Enumerated(EnumType.STRING)
    @Column(name = "unavailable_reason", length = 50)
    private StrategyGuideUnavailableReason unavailableReason;

    protected PremarketGuideItem() {
    }

    private PremarketGuideItem(PremarketGuideScope scope, Market market, String ticker) {
        if (scope == null || market == null || ticker == null || ticker.isBlank()) {
            throw new IllegalArgumentException("장전 가이드 종목 정보가 올바르지 않습니다.");
        }

        this.scope = scope;
        this.market = market;
        this.ticker = ticker;
    }

    public static PremarketGuideItem available(
            PremarketGuideScope scope,
            AssetStrategyGuide guide
    ) {
        if (guide == null || guide.getStrategyDecision() == null) {
            throw new IllegalArgumentException("저장할 전략 가이드가 없습니다.");
        }

        PremarketGuideItem item = new PremarketGuideItem(scope, guide.getMarket(), guide.getTicker());
        StrategyDecision decision = guide.getStrategyDecision();
        StrategySignal signal = decision.getSignal();
        StrategyMetadata metadata = signal.getMetadata();
        StrategyDecisionGuidance guidance = decision.getGuidance();

        item.status = PremarketGuideItemStatus.AVAILABLE;
        item.action = decision.getAction();
        item.reason = decision.getReason();
        item.referencePrice = signal.getReferencePrice();
        item.dataAsOf = metadata == null ? null : metadata.getDataAsOf();
        item.strategyId = metadata == null ? null : metadata.getStrategyId();
        item.strategyVersion = metadata == null ? null : metadata.getStrategyVersion();
        item.confidence = metadata == null ? null : metadata.getConfidence();
        item.caveats = metadata == null ? null : String.join("\n", metadata.getCaveats());
        item.trend = signal.getTrend();
        item.signalEvent = signal.getSignalEvent();
        item.weeksSinceCross = signal.getWeeksSinceCross();
        item.entryTimingStatus = guidance == null ? null : guidance.getEntryTimingStatus();
        item.entryTimingMessage = guidance == null ? null : guidance.getEntryTimingMessage();
        item.stopLossStatus = guidance == null ? null : guidance.getStopLossStatus();
        item.stopLossRatio = guidance == null ? null : guidance.getStopLossRatio();
        item.stopLossPrice = guidance == null ? null : guidance.getStopLossPrice();
        item.stopLossMessage = guidance == null ? null : guidance.getStopLossMessage();
        return item;
    }

    public static PremarketGuideItem unavailable(
            PremarketGuideScope scope,
            UnavailableAsset unavailableAsset
    ) {
        if (unavailableAsset == null) {
            throw new IllegalArgumentException("저장할 조회 불가 종목이 없습니다.");
        }

        PremarketGuideItem item = new PremarketGuideItem(
                scope,
                unavailableAsset.getMarket(),
                unavailableAsset.getTicker()
        );
        item.status = PremarketGuideItemStatus.UNAVAILABLE;
        item.reason = unavailableAsset.getMessage();
        item.unavailableReason = unavailableAsset.getReason();
        return item;
    }

    void assignSnapshot(PremarketGuideSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    public Long getId() {
        return id;
    }

    public PremarketGuideScope getScope() {
        return scope;
    }

    public PremarketGuideItemStatus getStatus() {
        return status;
    }

    public Market getMarket() {
        return market;
    }

    public String getTicker() {
        return ticker;
    }

    public StrategyAction getAction() {
        return action;
    }

    public String getReason() {
        return reason;
    }

    public BigDecimal getReferencePrice() {
        return referencePrice;
    }

    public LocalDate getDataAsOf() {
        return dataAsOf;
    }

    public String getStrategyId() {
        return strategyId;
    }

    public String getStrategyVersion() {
        return strategyVersion;
    }

    public String getConfidence() {
        return confidence;
    }

    public List<String> getCaveats() {
        return caveats == null || caveats.isBlank()
                ? List.of()
                : List.of(caveats.split("\\n"));
    }

    public StrategyTrend getTrend() {
        return trend;
    }

    public StrategySignalEvent getSignalEvent() {
        return signalEvent;
    }

    public Integer getWeeksSinceCross() {
        return weeksSinceCross;
    }

    public String getEntryTimingStatus() {
        return entryTimingStatus;
    }

    public String getEntryTimingMessage() {
        return entryTimingMessage;
    }

    public String getStopLossStatus() {
        return stopLossStatus;
    }

    public BigDecimal getStopLossRatio() {
        return stopLossRatio;
    }

    public BigDecimal getStopLossPrice() {
        return stopLossPrice;
    }

    public String getStopLossMessage() {
        return stopLossMessage;
    }

    public StrategyGuideUnavailableReason getUnavailableReason() {
        return unavailableReason;
    }
}
