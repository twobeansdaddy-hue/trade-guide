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
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;

/**
 * 실행 시점에 계산한 종목별 대조 결과다.
 *
 * <p>결과를 저장하는 이유는 원장이 계속 바뀌기 때문이다. 나중에 다시 계산하면 그때의 답이
 * 나오지, 이 실행이 무엇을 보고 판단했는지는 알 수 없다. 실행 감사는 그 시점의 답을 남겨야 한다.
 */
@Entity
@Table(
        name = "broker_order_import_reconciliation_lines",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_broker_order_import_reconciliation_lines_asset",
                columnNames = {"run_id", "market", "ticker"}
        )
)
public class BrokerOrderImportReconciliationLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "run_id", nullable = false)
    private BrokerOrderImportRun run;

    @Enumerated(EnumType.STRING)
    @Column(name = "market", nullable = false)
    private Market market;

    @Column(name = "ticker", nullable = false)
    private String ticker;

    @Column(name = "reconstructed_quantity", nullable = false, precision = 19, scale = 6)
    private BigDecimal reconstructedQuantity;

    @Column(name = "snapshot_quantity", nullable = false, precision = 19, scale = 6)
    private BigDecimal snapshotQuantity;

    @Column(name = "quantity_difference", nullable = false, precision = 19, scale = 6)
    private BigDecimal quantityDifference;

    protected BrokerOrderImportReconciliationLine() {
    }

    BrokerOrderImportReconciliationLine(BrokerOrderImportRun run, BrokerOrderReconciliationLineValue value) {
        this.run = run;
        this.market = value.market();
        this.ticker = value.ticker();
        this.reconstructedQuantity = value.reconstructedQuantity();
        this.snapshotQuantity = value.snapshotQuantity();
        this.quantityDifference = value.quantityDifference();
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

    public BigDecimal getReconstructedQuantity() {
        return reconstructedQuantity;
    }

    public BigDecimal getSnapshotQuantity() {
        return snapshotQuantity;
    }

    public BigDecimal getQuantityDifference() {
        return quantityDifference;
    }
}
