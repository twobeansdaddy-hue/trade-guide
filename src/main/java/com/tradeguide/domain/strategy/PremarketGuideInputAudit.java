package com.tradeguide.domain.strategy;

import com.tradeguide.domain.market.MarketDataProvider;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "premarket_guide_input_audits")
public class PremarketGuideInputAudit {

    public static final String MISSING_REASONS =
            "CANDLE_RECEIPT_NOT_CAPTURED,INPUT_DIGEST_NOT_CAPTURED,PORTFOLIO_STATE_NOT_CAPTURED";

    @Id
    @Column(name = "guide_snapshot_id")
    private Long guideSnapshotId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "guide_snapshot_id")
    private PremarketGuideSnapshot snapshot;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "evidence_status", nullable = false, length = 20)
    private GuideInputEvidenceStatus evidenceStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "candle_provider", length = 50)
    private MarketDataProvider candleProvider;

    @Column(name = "response_received_at")
    private Instant responseReceivedAt;

    @Column(name = "adjustment_mode", length = 40)
    private String adjustmentMode;

    @Column(name = "input_sha256", length = 64)
    private String inputSha256;

    @Column(name = "portfolio_state_ref", length = 100)
    private String portfolioStateRef;

    @Column(name = "missing_reasons", nullable = false, length = 1000)
    private String missingReasons;

    protected PremarketGuideInputAudit() {
    }

    PremarketGuideInputAudit(PremarketGuideSnapshot snapshot, Instant recordedAt,
                            MarketDataProvider candleProvider) {
        this.snapshot = snapshot;
        markUnverified(recordedAt, candleProvider);
    }

    void markUnverified(Instant recordedAt, MarketDataProvider candleProvider) {
        if (recordedAt == null) {
            throw new IllegalArgumentException("감사 기록 시각이 필요합니다.");
        }
        this.recordedAt = recordedAt;
        this.candleProvider = candleProvider;
        this.evidenceStatus = GuideInputEvidenceStatus.UNVERIFIED;
        this.responseReceivedAt = null;
        this.adjustmentMode = null;
        this.inputSha256 = null;
        this.portfolioStateRef = null;
        this.missingReasons = MISSING_REASONS;
    }

    public GuideInputEvidenceStatus getEvidenceStatus() {
        return evidenceStatus;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }

    public String getMissingReasons() {
        return missingReasons;
    }

    public Instant getResponseReceivedAt() {
        return responseReceivedAt;
    }

    public String getInputSha256() {
        return inputSha256;
    }
}
