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
 * 이미 Trade Guide 원장에 있는 종목의 증권사 잔고 조정(수량 불일치 해소) 승인 감사
 * 이력이다. 승인 당시 증권사 스냅샷 수량·평단가, 조정 직전 원장 수량·평단가, 산출된
 * 조정 수량(delta)·단가와 생성된 매매 기록 ID를 그대로 보존해, 취소로 원장 행이
 * 삭제된 뒤에도 승인·취소 이력을 추적할 수 있게 한다.
 *
 * <p>{@code tradeTransactionId}는 의도적으로 외래키 제약을 걸지 않는다. 취소 시
 * 참조하던 {@code TradeTransaction} 행이 삭제되어도 이 감사 이력의 값은 그대로
 * 남아 있어야 하기 때문이다. 같은 이유로 {@code snapshotItem}은 증권사 연결이
 * 삭제될 때 {@code REVOKED} 이력에 한해 {@code null}로 끊을 수 있다.
 */
@Entity
@Table(
        name = "portfolio_broker_holding_adjustments",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_broker_holding_adjustments_snapshot_item",
                columnNames = {"snapshot_item_id"}
        )
)
public class PortfolioBrokerHoldingAdjustment {

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

    @Column(name = "delta_quantity", nullable = false, precision = 19, scale = 6)
    private BigDecimal deltaQuantity;

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitPrice;

    @Column(name = "broker_quantity", nullable = false, precision = 19, scale = 6)
    private BigDecimal brokerQuantity;

    @Column(name = "broker_average_purchase_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal brokerAveragePurchasePrice;

    @Column(name = "ledger_quantity_before", nullable = false, precision = 19, scale = 6)
    private BigDecimal ledgerQuantityBefore;

    @Column(name = "ledger_average_purchase_price_before", nullable = false, precision = 19, scale = 4)
    private BigDecimal ledgerAveragePurchasePriceBefore;

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
    private PortfolioBrokerHoldingAdjustmentStatus status;

    protected PortfolioBrokerHoldingAdjustment() {
    }

    public PortfolioBrokerHoldingAdjustment(
            Portfolio portfolio,
            PortfolioBrokerHoldingSnapshotItem snapshotItem,
            BigDecimal deltaQuantity,
            BigDecimal unitPrice,
            BigDecimal brokerQuantity,
            BigDecimal brokerAveragePurchasePrice,
            BigDecimal ledgerQuantityBefore,
            BigDecimal ledgerAveragePurchasePriceBefore,
            LocalDateTime snapshotSyncedAt,
            Long tradeTransactionId,
            Long approvedByMemberId,
            LocalDateTime approvedAt
    ) {
        if (portfolio == null || snapshotItem == null || deltaQuantity == null || unitPrice == null
                || brokerQuantity == null || brokerAveragePurchasePrice == null
                || ledgerQuantityBefore == null || ledgerAveragePurchasePriceBefore == null
                || snapshotSyncedAt == null || tradeTransactionId == null
                || approvedByMemberId == null || approvedAt == null) {
            throw new IllegalArgumentException("증권사 잔고 조정 승인 정보가 올바르지 않습니다.");
        }
        if (deltaQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("조정 수량은 0보다 커야 합니다.");
        }
        if (unitPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("조정 단가는 0보다 커야 합니다.");
        }

        this.portfolio = portfolio;
        this.snapshotItem = snapshotItem;
        this.market = snapshotItem.getMarket();
        this.ticker = snapshotItem.getTicker();
        this.displayName = snapshotItem.getDisplayName();
        this.deltaQuantity = deltaQuantity;
        this.unitPrice = unitPrice;
        this.brokerQuantity = brokerQuantity;
        this.brokerAveragePurchasePrice = brokerAveragePurchasePrice;
        this.ledgerQuantityBefore = ledgerQuantityBefore;
        this.ledgerAveragePurchasePriceBefore = ledgerAveragePurchasePriceBefore;
        this.snapshotSyncedAt = snapshotSyncedAt;
        this.tradeTransactionId = tradeTransactionId;
        this.approvedByMemberId = approvedByMemberId;
        this.approvedAt = approvedAt;
        this.status = PortfolioBrokerHoldingAdjustmentStatus.ACTIVE;
    }

    /**
     * 증권사 연결 삭제로 원본 스냅샷이 사라질 때, 이미 취소된 승인 이력의 스냅샷
     * 참조만 끊는다. 아직 유효한 이력은 원장 행이 살아 있으므로 끊을 수 없다.
     */
    public void detachSnapshotItem() {
        if (status != PortfolioBrokerHoldingAdjustmentStatus.REVOKED) {
            throw new IllegalStateException("취소되지 않은 잔고 조정 승인 이력의 스냅샷 참조는 끊을 수 없습니다.");
        }
        this.snapshotItem = null;
    }

    public void revoke() {
        if (status != PortfolioBrokerHoldingAdjustmentStatus.ACTIVE) {
            throw new IllegalStateException("이미 취소된 잔고 조정입니다.");
        }
        this.status = PortfolioBrokerHoldingAdjustmentStatus.REVOKED;
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

    public BigDecimal getDeltaQuantity() {
        return deltaQuantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public BigDecimal getBrokerQuantity() {
        return brokerQuantity;
    }

    public BigDecimal getBrokerAveragePurchasePrice() {
        return brokerAveragePurchasePrice;
    }

    public BigDecimal getLedgerQuantityBefore() {
        return ledgerQuantityBefore;
    }

    public BigDecimal getLedgerAveragePurchasePriceBefore() {
        return ledgerAveragePurchasePriceBefore;
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

    public PortfolioBrokerHoldingAdjustmentStatus getStatus() {
        return status;
    }
}
