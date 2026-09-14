package com.tradeguide.domain.broker;

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

/**
 * 증권사 보유 종목 스냅샷에 속한 종목 한 건이다. 조회 시점에 증권사가 보고한
 * 수량과 평균 매입가를 그대로 보존하는 읽기 전용 기록이다.
 */
@Entity
@Table(name = "broker_holding_snapshot_items")
public class PortfolioBrokerHoldingSnapshotItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "snapshot_id", nullable = false)
    private PortfolioBrokerHoldingSnapshot snapshot;

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

    protected PortfolioBrokerHoldingSnapshotItem() {
    }

    PortfolioBrokerHoldingSnapshotItem(PortfolioBrokerHoldingSnapshot snapshot, BrokerHolding holding) {
        this.snapshot = snapshot;
        this.market = holding.market();
        this.ticker = holding.ticker();
        this.displayName = holding.displayName();
        this.quantity = holding.quantity();
        this.averagePurchasePrice = holding.averagePurchasePrice();
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

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getAveragePurchasePrice() {
        return averagePurchasePrice;
    }
}
