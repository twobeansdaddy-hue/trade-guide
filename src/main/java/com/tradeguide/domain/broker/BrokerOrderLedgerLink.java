package com.tradeguide.domain.broker;

import com.tradeguide.domain.member.Member;
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
import java.time.Instant;
import java.time.LocalDateTime;

/**
 * 주문 한 건이 매매 원장에 반영됐다는 사실이다. 중복 반영을 막는 <b>유일한 지점</b>이며,
 * {@code (broker_account_id, external_order_id)}에 걸린 부분 유니크 인덱스가 그 역할을 한다.
 *
 * <p>계좌 기준인 이유는 포트폴리오-계좌 링크가 나중에 바뀔 수 있기 때문이다. 포트폴리오 기준으로
 * 두면 같은 주문이 두 포트폴리오에 각각 들어가 진짜 중복이 된다.
 *
 * <p><b>이 단계에서는 어떤 코드도 이 표에 쓰지 않는다.</b> 읽기만 하며, 읽는 목적은 하나다.
 * 이미 반영된 주문을 다시 반영 후보로 올리지 않는 것. 쓰기는 사용자 승인이 있는 별도 단계의 일이다.
 *
 * <p>승인 시점 값을 동결해 두는 이유는 증권사가 나중에 값을 정정할 수 있어서다. 정정이 오면
 * 원장을 조용히 고치는 대신 "무엇을 근거로 반영했는지"와 "지금 증권사는 뭐라고 하는지"를
 * 나란히 보여 주고 사용자가 판단하게 한다.
 */
@Entity
@Table(name = "broker_order_ledger_links")
public class BrokerOrderLedgerLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "broker_account_id", nullable = false)
    private BrokerAccount brokerAccount;

    @Column(name = "external_order_id", nullable = false, length = 255)
    private String externalOrderId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "run_id", nullable = false)
    private BrokerOrderImportRun run;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private BrokerOrderImportItem item;

    /**
     * 취소로 매매 기록이 삭제된 뒤에도 반영 당시 거래 ID를 감사 목적으로 보존해야 하므로
     * 외래키를 걸지 않는다. 개시 잔고 승인 이력과 같은 선례를 따른다.
     */
    @Column(name = "trade_transaction_id")
    private Long tradeTransactionId;

    @Column(name = "approved_quantity", nullable = false, precision = 19, scale = 6)
    private BigDecimal approvedQuantity;

    @Column(name = "approved_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal approvedPrice;

    @Column(name = "approved_fee", nullable = false, precision = 19, scale = 4)
    private BigDecimal approvedFee;

    @Column(name = "approved_traded_at", nullable = false)
    private Instant approvedTradedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "approved_by_member_id", nullable = false)
    private Member approvedByMember;

    @Column(name = "approved_at", nullable = false)
    private LocalDateTime approvedAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BrokerOrderLedgerLinkStatus status;

    /**
     * 의심 항목을 반영 허용으로 재판정해 반영했다면 그 재판정이다. 분류기가 처음부터 반영 후보로
     * 본 주문은 비어 있다. 승인을 취소해도 끊지 않는다. 무엇을 근거로 반영했는지가 감사 이력이다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "override_id")
    private BrokerOrderImportItemOverride override;

    protected BrokerOrderLedgerLink() {
    }

    /** 분류기가 처음부터 반영 후보로 본 주문의 원장 반영 승인을 기록한다. */
    public BrokerOrderLedgerLink(
            BrokerAccount brokerAccount,
            BrokerOrderImportRun run,
            BrokerOrderImportItem item,
            Long tradeTransactionId,
            Member approvedByMember,
            LocalDateTime approvedAt
    ) {
        this(brokerAccount, run, item, tradeTransactionId, approvedByMember, approvedAt, null);
    }

    /**
     * 주문 한 건의 원장 반영 승인을 기록한다. 승인 시점 값을 그대로 동결해 두는 이유는
     * 클래스 상단 문서에 적었다: 증권사가 나중에 값을 정정해도 무엇을 근거로 반영했는지
     * 남겨야 하기 때문이다.
     *
     * <p>{@code override}는 의심 항목을 반영 허용으로 재판정해 반영하는 경우에만 넘긴다.
     */
    public BrokerOrderLedgerLink(
            BrokerAccount brokerAccount,
            BrokerOrderImportRun run,
            BrokerOrderImportItem item,
            Long tradeTransactionId,
            Member approvedByMember,
            LocalDateTime approvedAt,
            BrokerOrderImportItemOverride override
    ) {
        if (brokerAccount == null || run == null || item == null
                || tradeTransactionId == null || approvedByMember == null || approvedAt == null) {
            throw new IllegalArgumentException("주문 반영 승인 정보가 올바르지 않습니다.");
        }
        if (override != null && override.getDecision() != BrokerOrderOverrideDecision.ALLOW_LEDGER_WRITE) {
            throw new IllegalArgumentException("반영 허용 재판정만 원장 반영의 근거가 될 수 있습니다.");
        }
        this.override = override;

        this.brokerAccount = brokerAccount;
        this.externalOrderId = item.getExternalOrderId();
        this.run = run;
        this.item = item;
        this.tradeTransactionId = tradeTransactionId;
        this.approvedQuantity = item.getFilledQuantity();
        this.approvedPrice = item.getAverageFilledPrice();
        this.approvedFee = item.getCommission() == null ? BigDecimal.ZERO : item.getCommission();
        this.approvedTradedAt = item.getFilledAt();
        this.approvedByMember = approvedByMember;
        this.approvedAt = approvedAt;
        this.status = BrokerOrderLedgerLinkStatus.ACTIVE;
    }

    /**
     * 승인 취소는 행을 지우지 않고 상태로만 남긴다. {@code tradeTransactionId}는 취소로
     * 매매 기록이 삭제된 뒤에도 감사 목적으로 그대로 보존한다.
     */
    public void revoke(LocalDateTime revokedAt) {
        if (status != BrokerOrderLedgerLinkStatus.ACTIVE) {
            throw new IllegalStateException("이미 취소된 반영 승인입니다.");
        }
        if (revokedAt == null) {
            throw new IllegalArgumentException("취소 시각은 필수입니다.");
        }

        this.status = BrokerOrderLedgerLinkStatus.REVOKED;
        this.revokedAt = revokedAt;
    }

    public Long getId() {
        return id;
    }

    public String getExternalOrderId() {
        return externalOrderId;
    }

    public BrokerOrderImportItem getItem() {
        return item;
    }

    public Long getTradeTransactionId() {
        return tradeTransactionId;
    }

    public BigDecimal getApprovedQuantity() {
        return approvedQuantity;
    }

    public BigDecimal getApprovedPrice() {
        return approvedPrice;
    }

    public BigDecimal getApprovedFee() {
        return approvedFee;
    }

    public Instant getApprovedTradedAt() {
        return approvedTradedAt;
    }

    public LocalDateTime getApprovedAt() {
        return approvedAt;
    }

    public LocalDateTime getRevokedAt() {
        return revokedAt;
    }

    public BrokerOrderLedgerLinkStatus getStatus() {
        return status;
    }

    public BrokerOrderImportItemOverride getOverride() {
        return override;
    }
}
