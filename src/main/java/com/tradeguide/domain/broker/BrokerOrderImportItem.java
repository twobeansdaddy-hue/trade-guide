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
import java.time.Instant;
import java.time.LocalDate;

/**
 * 실행 시점에 증권사가 보고한 주문 한 건을 그대로 얼려 둔 행이다.
 *
 * <p>이 행은 <b>만든 뒤에 바꾸지 않는다.</b> 같은 주문을 다시 조회해 값이 달라졌다면 이 행을
 * 갱신하는 대신 새 실행의 새 행으로 남긴다. 그래야 "언제 조회했을 때 무엇이 어떻게 달랐는지"를
 * 두 실행을 나란히 놓고 볼 수 있고, 제공자가 값을 정정했다는 사실 자체가 증거로 남는다.
 * 그래서 이 클래스에는 setter가 없다.
 *
 * <p>{@code averageFilledPrice}는 개별 체결가가 아니라 주문 전체의 평균 체결가이고,
 * {@code filledAt}은 마지막 체결 시각이다. 개별 체결 시각·체결가는 제공자가 주지 않으므로
 * 어떤 방법으로도 복원할 수 없다.
 */
@Entity
// 커서 순회가 같은 주문을 두 번 담는 일을 DB가 막는다. 마이그레이션과 같은 제약을 여기에도
// 선언해 두어야 스키마를 엔티티에서 만드는 환경에서도 같은 규칙이 걸린다.
@Table(
        name = "broker_order_import_items",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_broker_order_import_items_run_order",
                columnNames = {"run_id", "external_order_id"}
        )
)
public class BrokerOrderImportItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "run_id", nullable = false)
    private BrokerOrderImportRun run;

    @Column(name = "external_order_id", nullable = false, length = 255)
    private String externalOrderId;

    /** 주문 식별자가 재조회마다 바뀌는지 감지하기 위한 교차 검증 값이다. 대체 키가 아니다. */
    @Column(name = "content_fingerprint", nullable = false, length = 64)
    private String contentFingerprint;

    @Enumerated(EnumType.STRING)
    @Column(name = "market", nullable = false)
    private Market market;

    @Column(name = "ticker", nullable = false)
    private String ticker;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_side", nullable = false, length = 10)
    private BrokerOrderSide orderSide;

    @Column(name = "provider_status_code", nullable = false, length = 50)
    private String providerStatusCode;

    @Column(name = "provider_order_type", length = 50)
    private String providerOrderType;

    @Column(name = "provider_time_in_force", length = 50)
    private String providerTimeInForce;

    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle", nullable = false, length = 30)
    private BrokerOrderLifecycle lifecycle;

    @Column(name = "ordered_quantity", nullable = false, precision = 19, scale = 6)
    private BigDecimal orderedQuantity;

    @Column(name = "filled_quantity", nullable = false, precision = 19, scale = 6)
    private BigDecimal filledQuantity;

    @Column(name = "average_filled_price", precision = 19, scale = 4)
    private BigDecimal averageFilledPrice;

    @Column(name = "filled_amount", precision = 19, scale = 4)
    private BigDecimal filledAmount;

    @Column(name = "commission", precision = 19, scale = 4)
    private BigDecimal commission;

    /** 보존만 한다. 취득원가에 합산하면 원가의 의미가 조용히 바뀐다. */
    @Column(name = "tax", precision = 19, scale = 4)
    private BigDecimal tax;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "ordered_at", nullable = false)
    private Instant orderedAt;

    @Column(name = "filled_at")
    private Instant filledAt;

    @Column(name = "settlement_date")
    private LocalDate settlementDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "staging_status", nullable = false, length = 40)
    private BrokerOrderStagingStatus stagingStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "skip_reason_code", length = 40)
    private BrokerOrderSkipReason skipReasonCode;

    /** 평균가 × 수량과 체결 금액의 괴리가 평균가 반올림으로 설명되지 않는다. 값은 보정하지 않는다. */
    @Column(name = "amount_mismatch", nullable = false)
    private boolean amountMismatch;

    /** 수수료가 비어 있어 취득원가가 그만큼 낮게 잡힌다. */
    @Column(name = "fee_unknown", nullable = false)
    private boolean feeUnknown;

    /** 매수인데 세금이 붙어 있다. 세금은 취득원가 계산에 들어가지 않는다. */
    @Column(name = "buy_tax", nullable = false)
    private boolean buyTax;

    protected BrokerOrderImportItem() {
    }

    BrokerOrderImportItem(BrokerOrderImportRun run, BrokerOrderStagedOrder stagedOrder) {
        BrokerOrderRecord record = stagedOrder.record();

        this.run = run;
        this.externalOrderId = record.externalOrderId();
        this.contentFingerprint = stagedOrder.contentFingerprint();
        this.market = record.market();
        this.ticker = record.ticker();
        this.displayName = stagedOrder.displayName();
        this.orderSide = record.side();
        this.providerStatusCode = record.providerStatusCode();
        this.providerOrderType = record.providerOrderType();
        this.providerTimeInForce = record.providerTimeInForce();
        this.lifecycle = record.lifecycle();
        this.orderedQuantity = record.orderedQuantity();
        this.filledQuantity = record.filledQuantity();
        this.averageFilledPrice = record.averageFilledPrice();
        this.filledAmount = record.filledAmount();
        this.commission = record.commission();
        this.tax = record.tax();
        this.currencyCode = record.currencyCode();
        this.orderedAt = record.orderedAt();
        this.filledAt = record.filledAt();
        this.settlementDate = record.settlementDate();
        this.stagingStatus = stagedOrder.stagingStatus();
        this.skipReasonCode = stagedOrder.skipReason();
        this.amountMismatch = stagedOrder.amountMismatch();
        this.feeUnknown = stagedOrder.feeUnknown();
        this.buyTax = stagedOrder.buyTax();
    }

    public Long getId() {
        return id;
    }

    public BrokerOrderImportRun getRun() {
        return run;
    }

    public String getExternalOrderId() {
        return externalOrderId;
    }

    public String getContentFingerprint() {
        return contentFingerprint;
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

    public BrokerOrderSide getOrderSide() {
        return orderSide;
    }

    public String getProviderStatusCode() {
        return providerStatusCode;
    }

    public String getProviderOrderType() {
        return providerOrderType;
    }

    public String getProviderTimeInForce() {
        return providerTimeInForce;
    }

    public BrokerOrderLifecycle getLifecycle() {
        return lifecycle;
    }

    public BigDecimal getOrderedQuantity() {
        return orderedQuantity;
    }

    public BigDecimal getFilledQuantity() {
        return filledQuantity;
    }

    public BigDecimal getAverageFilledPrice() {
        return averageFilledPrice;
    }

    public BigDecimal getFilledAmount() {
        return filledAmount;
    }

    public BigDecimal getCommission() {
        return commission;
    }

    public BigDecimal getTax() {
        return tax;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public Instant getOrderedAt() {
        return orderedAt;
    }

    public Instant getFilledAt() {
        return filledAt;
    }

    public LocalDate getSettlementDate() {
        return settlementDate;
    }

    public BrokerOrderStagingStatus getStagingStatus() {
        return stagingStatus;
    }

    public BrokerOrderSkipReason getSkipReasonCode() {
        return skipReasonCode;
    }

    public boolean isAmountMismatch() {
        return amountMismatch;
    }

    public boolean isFeeUnknown() {
        return feeUnknown;
    }

    public boolean isBuyTax() {
        return buyTax;
    }
}
