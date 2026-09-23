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

    private static final String INPUT_DIGEST_NOT_CAPTURED = "INPUT_DIGEST_NOT_CAPTURED";
    private static final String PORTFOLIO_STATE_REF_PREFIX = "portfolio-state:v1:";

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
                            MarketDataProvider candleProvider, boolean candleEvidenceIncomplete,
                            boolean candleReceiptIncomplete, PortfolioStateCapture stateCapture,
                            String stateSha256) {
        this.snapshot = snapshot;
        markUnverified(recordedAt, candleProvider, candleEvidenceIncomplete, candleReceiptIncomplete,
                stateCapture, stateSha256);
    }

    /**
     * 전체 입력이 검증되기 전까지는 항상 미검증으로 기록한다. 포트폴리오 상태가 일관되게 캡처돼도
     * 전체 입력 해시와 가격 조정 계보가 없으므로 상태를 올리지 않는다.
     */
    void markUnverified(Instant recordedAt, MarketDataProvider candleProvider,
                        boolean candleEvidenceIncomplete, boolean candleReceiptIncomplete,
                        PortfolioStateCapture stateCapture, String stateSha256) {
        if (recordedAt == null || stateCapture == null
                || (stateCapture != PortfolioStateCapture.NOT_CAPTURED && stateSha256 == null)) {
            throw new IllegalArgumentException("감사 기록 시각과 포트폴리오 상태 캡처 정보가 필요합니다.");
        }
        this.recordedAt = recordedAt;
        this.candleProvider = candleProvider;
        this.evidenceStatus = GuideInputEvidenceStatus.UNVERIFIED;
        this.responseReceivedAt = null;
        this.adjustmentMode = null;
        this.inputSha256 = null;
        this.portfolioStateRef = stateCapture == PortfolioStateCapture.CAPTURED
                ? PORTFOLIO_STATE_REF_PREFIX + stateSha256
                : null;
        this.missingReasons = (candleReceiptIncomplete ? "CANDLE_RECEIPT_NOT_CAPTURED," : "")
                + INPUT_DIGEST_NOT_CAPTURED
                + switch (stateCapture) {
                    case NOT_CAPTURED -> ",PORTFOLIO_STATE_NOT_CAPTURED";
                    case CHANGED_DURING_GENERATION -> ",PORTFOLIO_STATE_CHANGED_DURING_GENERATION";
                    case CAPTURED -> "";
                }
                + (candleEvidenceIncomplete ? ",CANDLE_EVIDENCE_INCOMPLETE" : "");
    }

    public String getPortfolioStateRef() {
        return portfolioStateRef;
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
