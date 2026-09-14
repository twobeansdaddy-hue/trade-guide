package com.tradeguide.domain.broker;

import com.tradeguide.domain.portfolio.Portfolio;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 증권사 개시 잔고를 Trade Guide 매매 기록으로 승인한 감사 이력이다.
 * 승인 당시 수량·평단가·표시명·스냅샷 기준 시각과 생성된 매매 기록 ID를 그대로
 * 보존해, 취소로 원장 행이 삭제된 뒤에도 승인·취소 이력을 추적할 수 있게 한다.
 *
 * <p>{@code tradeTransactionId}는 의도적으로 외래키 제약을 걸지 않는다. 취소 시
 * 참조하던 {@code TradeTransaction} 행이 삭제되어도 이 감사 이력의 값은 그대로
 * 남아 있어야 하기 때문이다. 같은 이유로 {@code snapshotItem}은 증권사 연결이
 * 삭제될 때 {@code REVOKED} 이력에 한해 {@code null}로 끊을 수 있다.
 */
@Entity
@Table(
        name = "portfolio_broker_holding_imports",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_broker_holding_imports_snapshot_item",
                columnNames = {"snapshot_item_id"}
        )
)
public class PortfolioBrokerHoldingImport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "portfolio_id", nullable = false)
    private Portfolio portfolio;

    /**
     * 승인 대상이 된 스냅샷 항목이다. 증권사 연결이 삭제되면 파생 스냅샷도 함께
     * 사라지므로, 이미 취소된 이력에 한해 이 참조만 {@code null}로 끊는다.
     * 감사에 필요한 값은 아래 필드에 승인 시점 그대로 복사되어 있다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "snapshot_item_id")
    private PortfolioBrokerHoldingSnapshotItem snapshotItem;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Market market;

    @Column(nullable = false)
    private String ticker;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal quantity;

    @Column(name = "average_purchase_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal averagePurchasePrice;

    @Column(name = "snapshot_synced_at", nullable = false)
    private LocalDateTime snapshotSyncedAt;

    @Column(name = "trade_transaction_id", nullable = false)
    private Long tradeTransactionId;

    @Column(name = "approved_by_member_id", nullable = false)
    private Long approvedByMemberId;

    @Column(name = "approved_at", nullable = false)
    private LocalDateTime approvedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PortfolioBrokerHoldingImportStatus status;

    protected PortfolioBrokerHoldingImport() {
    }

    public PortfolioBrokerHoldingImport(
            Portfolio portfolio,
            PortfolioBrokerHoldingSnapshotItem snapshotItem,
            LocalDateTime snapshotSyncedAt,
            Long tradeTransactionId,
            Long approvedByMemberId,
            LocalDateTime approvedAt
    ) {
        if (portfolio == null || snapshotItem == null || snapshotSyncedAt == null
                || tradeTransactionId == null || approvedByMemberId == null || approvedAt == null) {
            throw new IllegalArgumentException("증권사 개시 잔고 승인 정보가 올바르지 않습니다.");
        }

        this.portfolio = portfolio;
        this.snapshotItem = snapshotItem;
        this.market = snapshotItem.getMarket();
        this.ticker = snapshotItem.getTicker();
        this.displayName = snapshotItem.getDisplayName();
        this.quantity = snapshotItem.getQuantity();
        this.averagePurchasePrice = snapshotItem.getAveragePurchasePrice();
        this.snapshotSyncedAt = snapshotSyncedAt;
        this.tradeTransactionId = tradeTransactionId;
        this.approvedByMemberId = approvedByMemberId;
        this.approvedAt = approvedAt;
        this.status = PortfolioBrokerHoldingImportStatus.ACTIVE;
    }

    /**
     * 증권사 연결 삭제로 원본 스냅샷이 사라질 때, 이미 취소된 승인 이력의 스냅샷
     * 참조만 끊는다. 아직 유효한 이력은 원장 행이 살아 있으므로 끊을 수 없다.
     */
    public void detachSnapshotItem() {
        if (status != PortfolioBrokerHoldingImportStatus.REVOKED) {
            throw new IllegalStateException("취소되지 않은 개시 잔고 승인 이력의 스냅샷 참조는 끊을 수 없습니다.");
        }
        this.snapshotItem = null;
    }

    public void revoke() {
        if (status != PortfolioBrokerHoldingImportStatus.ACTIVE) {
            throw new IllegalStateException("이미 취소된 개시 잔고입니다.");
        }
        this.status = PortfolioBrokerHoldingImportStatus.REVOKED;
    }

    public Long getId() {
        return id;
    }

    public Portfolio getPortfolio() {
        return portfolio;
    }

    public PortfolioBrokerHoldingSnapshotItem getSnapshotItem() {
        return snapshotItem;
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

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getAveragePurchasePrice() {
        return averagePurchasePrice;
    }

    public LocalDateTime getSnapshotSyncedAt() {
        return snapshotSyncedAt;
    }

    public Long getTradeTransactionId() {
        return tradeTransactionId;
    }

    public Long getApprovedByMemberId() {
        return approvedByMemberId;
    }

    public LocalDateTime getApprovedAt() {
        return approvedAt;
    }

    public PortfolioBrokerHoldingImportStatus getStatus() {
        return status;
    }
}
