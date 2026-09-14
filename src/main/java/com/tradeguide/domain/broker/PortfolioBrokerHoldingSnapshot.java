package com.tradeguide.domain.broker;

import com.tradeguide.domain.portfolio.Portfolio;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 포트폴리오에 연결된 증권사 계좌에서 특정 시점에 가져온 읽기 전용 보유 종목 스냅샷이다.
 * {@code TradeTransaction}이나 파생 {@code Holding}과는 별도로 저장되며,
 * 이 스냅샷만으로 매매 기록을 생성·수정하지 않는다.
 */
@Entity
@Table(name = "broker_holding_snapshots")
public class PortfolioBrokerHoldingSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "portfolio_id", nullable = false)
    private Portfolio portfolio;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "broker_connection_id", nullable = false)
    private BrokerConnection brokerConnection;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "broker_account_id", nullable = false)
    private BrokerAccount brokerAccount;

    @Column(name = "synced_at", nullable = false)
    private LocalDateTime syncedAt;

    @Column(name = "unsupported_market_count", nullable = false)
    private int unsupportedMarketCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "snapshot", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PortfolioBrokerHoldingSnapshotItem> items = new ArrayList<>();

    protected PortfolioBrokerHoldingSnapshot() {
    }

    public PortfolioBrokerHoldingSnapshot(
            Portfolio portfolio,
            BrokerConnection brokerConnection,
            BrokerAccount brokerAccount,
            LocalDateTime syncedAt,
            int unsupportedMarketCount,
            List<BrokerHolding> holdings
    ) {
        if (portfolio == null || brokerConnection == null || brokerAccount == null
                || syncedAt == null || holdings == null) {
            throw new IllegalArgumentException("증권사 보유 종목 스냅샷 정보가 올바르지 않습니다.");
        }
        if (unsupportedMarketCount < 0) {
            throw new IllegalArgumentException("미지원 시장 항목 수는 0 이상이어야 합니다.");
        }

        this.portfolio = portfolio;
        this.brokerConnection = brokerConnection;
        this.brokerAccount = brokerAccount;
        this.syncedAt = syncedAt;
        this.unsupportedMarketCount = unsupportedMarketCount;
        this.createdAt = syncedAt;
        holdings.forEach(holding -> this.items.add(new PortfolioBrokerHoldingSnapshotItem(this, holding)));
    }

    public Long getId() {
        return id;
    }

    public Portfolio getPortfolio() {
        return portfolio;
    }

    public BrokerConnection getBrokerConnection() {
        return brokerConnection;
    }

    public BrokerAccount getBrokerAccount() {
        return brokerAccount;
    }

    public LocalDateTime getSyncedAt() {
        return syncedAt;
    }

    public int getUnsupportedMarketCount() {
        return unsupportedMarketCount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public List<PortfolioBrokerHoldingSnapshotItem> getItems() {
        return List.copyOf(items);
    }
}
