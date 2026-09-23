package com.tradeguide.domain.strategy;

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

/**
 * 장전 가이드 한 건이 계산에 사용한 포트폴리오 결정 입력의 다이제스트. 가이드 생성 시작 시 한 번 읽은
 * 상태이며, 저장 시점 상태와의 일치 여부는 {@link PremarketGuideInputAudit}의 누락 사유로 구분한다.
 */
@Entity
@Table(name = "premarket_guide_portfolio_states")
public class PremarketGuidePortfolioState {

    @Id
    @Column(name = "guide_snapshot_id")
    private Long guideSnapshotId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "guide_snapshot_id")
    private PremarketGuideSnapshot snapshot;

    @Column(name = "state_schema_version", nullable = false)
    private int stateSchemaVersion;

    @Column(name = "read_at", nullable = false)
    private Instant readAt;

    @Column(name = "ledger_sha256", nullable = false, length = 64)
    private String ledgerSha256;

    @Column(name = "ledger_transaction_count", nullable = false)
    private int ledgerTransactionCount;

    @Column(name = "holdings_sha256", nullable = false, length = 64)
    private String holdingsSha256;

    @Column(name = "strategy_overrides_sha256", nullable = false, length = 64)
    private String strategyOverridesSha256;

    @Column(name = "risk_settings_sha256", nullable = false, length = 64)
    private String riskSettingsSha256;

    @Enumerated(EnumType.STRING)
    @Column(name = "candidate_source", nullable = false, length = 20)
    private PremarketGuideCandidateSource candidateSource;

    @Column(name = "candidate_set_sha256", nullable = false, length = 64)
    private String candidateSetSha256;

    @Column(name = "asset_catalog_sha256", nullable = false, length = 64)
    private String assetCatalogSha256;

    @Column(name = "broker_snapshot_id")
    private Long brokerSnapshotId;

    @Column(name = "state_sha256", nullable = false, length = 64)
    private String stateSha256;

    protected PremarketGuidePortfolioState() {
    }

    PremarketGuidePortfolioState(PremarketGuideSnapshot snapshot, PremarketGuidePortfolioStateDigests digests) {
        this.snapshot = snapshot;
        refreshFrom(digests);
    }

    void refreshFrom(PremarketGuidePortfolioStateDigests digests) {
        this.stateSchemaVersion = digests.schemaVersion();
        this.readAt = digests.readAt();
        this.ledgerSha256 = digests.ledgerSha256();
        this.ledgerTransactionCount = digests.ledgerTransactionCount();
        this.holdingsSha256 = digests.holdingsSha256();
        this.strategyOverridesSha256 = digests.strategyOverridesSha256();
        this.riskSettingsSha256 = digests.riskSettingsSha256();
        this.candidateSource = digests.candidateSource();
        this.candidateSetSha256 = digests.candidateSetSha256();
        this.assetCatalogSha256 = digests.assetCatalogSha256();
        this.brokerSnapshotId = digests.brokerSnapshotId();
        this.stateSha256 = digests.stateSha256();
    }

    public int getStateSchemaVersion() { return stateSchemaVersion; }
    public Instant getReadAt() { return readAt; }
    public String getLedgerSha256() { return ledgerSha256; }
    public int getLedgerTransactionCount() { return ledgerTransactionCount; }
    public String getHoldingsSha256() { return holdingsSha256; }
    public String getStrategyOverridesSha256() { return strategyOverridesSha256; }
    public String getRiskSettingsSha256() { return riskSettingsSha256; }
    public PremarketGuideCandidateSource getCandidateSource() { return candidateSource; }
    public String getCandidateSetSha256() { return candidateSetSha256; }
    public String getAssetCatalogSha256() { return assetCatalogSha256; }
    public Long getBrokerSnapshotId() { return brokerSnapshotId; }
    public String getStateSha256() { return stateSha256; }
}
