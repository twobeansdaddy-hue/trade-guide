package com.tradeguide.domain.broker;

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

/**
 * 정합성 점검 줄 한 건에 붙은 차이 사유 후보 하나다. 한 줄에 여러 사유가 함께 붙을 수 있어
 * 컬럼이 아니라 별도 행으로 둔다.
 */
@Entity
@Table(
        name = "broker_reconciliation_line_reasons",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_broker_reconciliation_line_reasons_unique",
                columnNames = {"line_id", "reason_code"}
        )
)
public class BrokerReconciliationLineReason {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "line_id", nullable = false)
    private BrokerReconciliationLine line;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason_code", nullable = false, length = 40)
    private BrokerReconciliationReasonCode reasonCode;

    protected BrokerReconciliationLineReason() {
    }

    BrokerReconciliationLineReason(BrokerReconciliationLine line, BrokerReconciliationReasonCode reasonCode) {
        this.line = line;
        this.reasonCode = reasonCode;
    }

    public Long getId() {
        return id;
    }

    public BrokerReconciliationReasonCode getReasonCode() {
        return reasonCode;
    }
}
