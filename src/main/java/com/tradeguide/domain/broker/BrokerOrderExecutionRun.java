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

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 자동 주문 실행 시도 한 건의 감사 기록이다({@link BrokerOrderExecutionGrant}과 같은
 * 감사 원칙 - `BrokerKeyRotationRun`/`BrokerReconciliationRun`과 동일하게 민감정보
 * 없는 사실만 담는다: 제공자 자격 증명, 액세스 토큰, 브로커 응답 원문은 여기 저장하지
 * 않는다).
 *
 * <p>{@code strategyId}는 실행 시점의 {@link BrokerOrderExecutionGrant#getStrategyId()}
 * 값을 복사해 둔다 - 동의의 전략이 나중에 바뀌어도 이 행은 실행 당시 어떤 전략이
 * 의사결정했는지 그대로 보존해야 하는 감사 기록이기 때문이다.
 *
 * <p>이 슬라이스는 실제 브로커 호출 로직을 구현하지 않는다 - 이 엔티티는 다음
 * 슬라이스가 채우기 시작할 스키마만 미리 준비한다.
 */
@Entity
@Table(name = "broker_order_execution_runs")
public class BrokerOrderExecutionRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "grant_id", nullable = false)
    private BrokerOrderExecutionGrant grant;

    @Column(name = "strategy_id", nullable = false, length = 100)
    private String strategyId;

    @Column(nullable = false, length = 20)
    private String ticker;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private BrokerOrderSide side;

    @Column(name = "requested_quantity", nullable = false, precision = 19, scale = 6)
    private BigDecimal requestedQuantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private BrokerOrderExecutionRunStatus status;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "failure_reason_code", length = 100)
    private String failureReasonCode;

    /**
     * true면 {@code tradeguide.broker.order-execution.live-enabled}가 꺼져 있어
     * 실제 브로커 호출 없이 "제출했다면 이랬을 것"만 기록한 행이다. 실거래와 절대
     * 섞여서는 안 되는 구분이라 감사 화면·집계는 항상 이 값으로 먼저 나눠야 한다.
     */
    @Column(name = "dry_run", nullable = false)
    private boolean dryRun;

    protected BrokerOrderExecutionRun() {
    }

    private BrokerOrderExecutionRun(
            BrokerOrderExecutionGrant grant,
            String strategyId,
            String ticker,
            BrokerOrderSide side,
            BigDecimal requestedQuantity,
            LocalDateTime startedAt
    ) {
        this.grant = grant;
        this.strategyId = strategyId;
        this.ticker = ticker;
        this.side = side;
        this.requestedQuantity = requestedQuantity;
        this.status = BrokerOrderExecutionRunStatus.PENDING;
        this.startedAt = startedAt;
    }

    /**
     * 실행 시도를 기록으로 남긴다. 저장 즉시 커밋해 "이 시도가 시작됐다"는 사실을
     * 브로커 호출 성공 여부와 무관하게 남긴다 - {@code BrokerKeyRotationRun.start}와
     * 같은 이유다.
     */
    public static BrokerOrderExecutionRun start(
            BrokerOrderExecutionGrant grant,
            String ticker,
            BrokerOrderSide side,
            BigDecimal requestedQuantity,
            LocalDateTime startedAt
    ) {
        if (grant == null) {
            throw new IllegalArgumentException("실행 대상 동의가 필요합니다.");
        }
        if (ticker == null || ticker.isBlank()) {
            throw new IllegalArgumentException("티커가 필요합니다.");
        }
        if (side == null) {
            throw new IllegalArgumentException("매수/매도 방향이 필요합니다.");
        }
        if (requestedQuantity == null || requestedQuantity.signum() <= 0) {
            throw new IllegalArgumentException("주문 수량은 0보다 커야 합니다.");
        }
        if (startedAt == null) {
            throw new IllegalArgumentException("시작 시각이 필요합니다.");
        }
        return new BrokerOrderExecutionRun(
                grant, grant.getStrategyId(), ticker, side, requestedQuantity, startedAt);
    }

    public void markSubmitted(LocalDateTime completedAt, boolean dryRun) {
        this.status = BrokerOrderExecutionRunStatus.SUBMITTED;
        this.completedAt = completedAt;
        this.dryRun = dryRun;
    }

    public void markFailed(LocalDateTime completedAt, String failureReasonCode) {
        this.status = BrokerOrderExecutionRunStatus.FAILED;
        this.completedAt = completedAt;
        this.failureReasonCode = failureReasonCode;
    }

    /** 서킷브레이커(포지션 한도·일일 한도)가 이 시도를 막았을 때 호출한다. */
    public void markRejectedBySafeguard(LocalDateTime completedAt, String failureReasonCode) {
        this.status = BrokerOrderExecutionRunStatus.REJECTED_BY_SAFEGUARD;
        this.completedAt = completedAt;
        this.failureReasonCode = failureReasonCode;
    }

    public Long getId() {
        return id;
    }

    public BrokerOrderExecutionGrant getGrant() {
        return grant;
    }

    public String getStrategyId() {
        return strategyId;
    }

    public String getTicker() {
        return ticker;
    }

    public BrokerOrderSide getSide() {
        return side;
    }

    public BigDecimal getRequestedQuantity() {
        return requestedQuantity;
    }

    public BrokerOrderExecutionRunStatus getStatus() {
        return status;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public String getFailureReasonCode() {
        return failureReasonCode;
    }

    public boolean isDryRun() {
        return dryRun;
    }
}
