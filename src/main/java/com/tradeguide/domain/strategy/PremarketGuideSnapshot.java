package com.tradeguide.domain.strategy;

import com.tradeguide.domain.market.MarketDataProvider;
import com.tradeguide.domain.portfolio.Portfolio;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 포트폴리오의 특정 장전 기준일에 계산한 가이드를 보존하는 실행 기록이다.
 *
 * <p>가이드 페이지를 다시 열었을 때 외부 시세 제공자를 다시 호출하지 않고도 당시 결과를
 * 재현할 수 있도록, 종목별 판단과 조회 불가 사유를 함께 저장한다. 이 기록은 주문을
 * 생성하지 않으며 기존 매매 원장도 변경하지 않는다.
 */
@Entity
@Table(
        name = "premarket_guide_snapshots",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_premarket_guide_snapshots_portfolio_date",
                columnNames = {"portfolio_id", "guide_date"}
        )
)
public class PremarketGuideSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "portfolio_id", nullable = false)
    private Portfolio portfolio;

    @Column(name = "guide_date", nullable = false)
    private LocalDate guideDate;

    @Column(name = "generated_at", nullable = false)
    private LocalDateTime generatedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PremarketGuideStatus status;

    @Column(name = "held_guide_count", nullable = false)
    private int heldGuideCount;

    @Column(name = "candidate_guide_count", nullable = false)
    private int candidateGuideCount;

    @Column(name = "unavailable_count", nullable = false)
    private int unavailableCount;

    @Column(name = "held_empty_reason", length = 50)
    private String heldEmptyReason;

    @Column(name = "held_empty_message", length = 1000)
    private String heldEmptyMessage;

    @Enumerated(EnumType.STRING)
    @Column(name = "candle_market_data_provider", length = 50)
    private MarketDataProvider marketDataProvider;

    @OneToMany(mappedBy = "snapshot", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("scope ASC, market ASC, ticker ASC")
    private List<PremarketGuideItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "snapshot", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("scope ASC, market ASC, ticker ASC")
    private List<PremarketGuideCandleEvidence> candleEvidence = new ArrayList<>();

    @OneToOne(mappedBy = "snapshot", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    private PremarketGuideInputAudit inputAudit;

    protected PremarketGuideSnapshot() {
    }

    public PremarketGuideSnapshot(
            Portfolio portfolio,
            LocalDate guideDate,
            LocalDateTime generatedAt
    ) {
        if (portfolio == null || guideDate == null || generatedAt == null) {
            throw new IllegalArgumentException("장전 가이드 실행 정보가 올바르지 않습니다.");
        }

        this.portfolio = portfolio;
        this.guideDate = guideDate;
        this.generatedAt = generatedAt;
        this.status = PremarketGuideStatus.COMPLETED;
    }

    public void replaceResults(
            PremarketGuideStatus status,
            List<PremarketGuideItem> newItems,
            EmptyHoldingsGuidance emptyHoldingsGuidance,
            LocalDateTime generatedAt,
            MarketDataProvider marketDataProvider
    ) {
        if (status == null || newItems == null || generatedAt == null) {
            throw new IllegalArgumentException("장전 가이드 결과가 올바르지 않습니다.");
        }

        this.status = status;
        this.generatedAt = generatedAt;
        this.marketDataProvider = marketDataProvider;
        this.items.clear();
        newItems.forEach(item -> {
            item.assignSnapshot(this);
            this.items.add(item);
        });
        this.heldGuideCount = count(PremarketGuideScope.HELD, PremarketGuideItemStatus.AVAILABLE);
        this.candidateGuideCount = count(PremarketGuideScope.CANDIDATE, PremarketGuideItemStatus.AVAILABLE);
        this.unavailableCount = (int) this.items.stream()
                .filter(item -> item.getStatus() == PremarketGuideItemStatus.UNAVAILABLE)
                .count();
        this.heldEmptyReason = emptyHoldingsGuidance == null
                ? null
                : emptyHoldingsGuidance.getReason().name();
        this.heldEmptyMessage = emptyHoldingsGuidance == null
                ? null
                : emptyHoldingsGuidance.getMessage();
    }

    public void recordUnverifiedInputs(Instant recordedAt, MarketDataProvider candleProvider) {
        List<PremarketGuideItem> availableItems = items.stream()
                .filter(item -> item.getStatus() == PremarketGuideItemStatus.AVAILABLE)
                .toList();
        boolean candleEvidenceIncomplete = availableItems.stream()
                .anyMatch(item -> candleEvidence.stream()
                        .noneMatch(evidence -> evidence.matches(item, candleProvider)));
        boolean candleReceiptIncomplete = availableItems.isEmpty() || availableItems.stream()
                .anyMatch(item -> candleEvidence.stream()
                        .noneMatch(evidence -> evidence.matches(item, candleProvider)
                                && !evidence.getPageReceivedAt().isEmpty()));
        if (inputAudit == null) {
            inputAudit = new PremarketGuideInputAudit(
                    this, recordedAt, candleProvider, candleEvidenceIncomplete, candleReceiptIncomplete);
        } else {
            inputAudit.markUnverified(
                    recordedAt, candleProvider, candleEvidenceIncomplete, candleReceiptIncomplete);
        }
    }

    public void replaceCandleEvidence(List<PremarketGuideCandleEvidence> observations) {
        candleEvidence.removeIf(existing -> observations.stream().noneMatch(existing::hasAssetKey));
        for (PremarketGuideCandleEvidence observation : observations) {
            candleEvidence.stream()
                    .filter(existing -> existing.hasAssetKey(observation))
                    .findFirst()
                    .ifPresentOrElse(existing -> existing.refreshFrom(observation), () -> {
                        observation.assignSnapshot(this);
                        candleEvidence.add(observation);
                    });
        }
    }

    private int count(PremarketGuideScope scope, PremarketGuideItemStatus status) {
        return (int) items.stream()
                .filter(item -> item.getScope() == scope && item.getStatus() == status)
                .count();
    }

    public Long getId() {
        return id;
    }

    public Portfolio getPortfolio() {
        return portfolio;
    }

    public LocalDate getGuideDate() {
        return guideDate;
    }

    public LocalDateTime getGeneratedAt() {
        return generatedAt;
    }

    public PremarketGuideStatus getStatus() {
        return status;
    }

    public int getHeldGuideCount() {
        return heldGuideCount;
    }

    public int getCandidateGuideCount() {
        return candidateGuideCount;
    }

    public int getUnavailableCount() {
        return unavailableCount;
    }

    public String getHeldEmptyReason() {
        return heldEmptyReason;
    }

    public String getHeldEmptyMessage() {
        return heldEmptyMessage;
    }

    public List<PremarketGuideItem> getItems() {
        return List.copyOf(items);
    }

    public MarketDataProvider getMarketDataProvider() {
        return marketDataProvider;
    }

    public PremarketGuideInputAudit getInputAudit() {
        return inputAudit;
    }

    public List<PremarketGuideCandleEvidence> getCandleEvidence() {
        return List.copyOf(candleEvidence);
    }
}
