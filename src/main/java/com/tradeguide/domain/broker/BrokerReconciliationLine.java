package com.tradeguide.domain.broker;

import com.tradeguide.domain.trade.Market;
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
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 정합성 점검 실행 한 건에 속한 종목 한 줄이다. 실행 시점에 계산한 값을 그대로 얼려 보존한다.
 * 이 줄은 어떤 경우에도 {@code TradeTransaction}이나 {@code Holding}을 바꾸지 않는다.
 */
@Entity
@Table(name = "broker_reconciliation_lines")
public class BrokerReconciliationLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "run_id", nullable = false)
    private BrokerReconciliationRun run;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Market market;

    @Column(nullable = false)
    private String ticker;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "broker_quantity", precision = 19, scale = 6)
    private BigDecimal brokerQuantity;

    @Column(name = "trade_guide_quantity", precision = 19, scale = 6)
    private BigDecimal tradeGuideQuantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private BrokerHoldingComparison comparison;

    // Set이어야 한다. run.lines와 함께 같은 조회에서 즉시 로딩하면(EntityGraph), 두 컬렉션이
    // 전부 List(bag)일 때 Hibernate가 MultipleBagFetchException을 던진다.
    @OneToMany(mappedBy = "line", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private Set<BrokerReconciliationLineReason> reasons = new LinkedHashSet<>();

    protected BrokerReconciliationLine() {
    }

    BrokerReconciliationLine(BrokerReconciliationRun run, BrokerReconciliationLineValue value) {
        this.run = run;
        this.market = value.market();
        this.ticker = value.ticker();
        this.displayName = value.displayName();
        this.brokerQuantity = value.brokerQuantity();
        this.tradeGuideQuantity = value.tradeGuideQuantity();
        this.comparison = value.comparison();
        value.reasonCandidates().forEach(reasonCode -> this.reasons.add(
                new BrokerReconciliationLineReason(this, reasonCode)));
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

    public BigDecimal getBrokerQuantity() {
        return brokerQuantity;
    }

    public BigDecimal getTradeGuideQuantity() {
        return tradeGuideQuantity;
    }

    public BrokerHoldingComparison getComparison() {
        return comparison;
    }

    public Set<BrokerReconciliationReasonCode> getReasonCandidates() {
        return reasons.stream().map(BrokerReconciliationLineReason::getReasonCode)
                .collect(Collectors.toUnmodifiableSet());
    }
}
