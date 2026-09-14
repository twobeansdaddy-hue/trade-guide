package com.tradeguide.domain.broker;

import com.tradeguide.domain.member.Member;
import com.tradeguide.domain.portfolio.Portfolio;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 주문 이력 가져오기 실행 한 건의 감사 기록이다.
 *
 * <p>이 행이 생겼다는 것은 "증권사에 이렇게 물어봤고 이런 답을 받았다"는 사실만 뜻한다.
 * 매매 원장은 이 실행으로 한 행도 바뀌지 않는다.
 *
 * <p>요청 구간과 실제 조회 구간을 따로 남기는 이유는 증권사가 체결일이 아니라 주문일 기준으로
 * 조회하기 때문이다. 미국 시장은 KST로 보면 주문일과 체결일이 하루 어긋나는 일이 흔해서
 * 요청 구간을 앞뒤로 넓혀 조회한다. 나중에 "왜 요청하지 않은 날짜 건이 보이냐"는 질문에
 * 이 두 구간이 그대로 답이 된다.
 */
@Entity
@Table(name = "broker_order_import_runs")
public class BrokerOrderImportRun {

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

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 50)
    private BrokerProvider provider;

    @Column(name = "requested_ordered_from", nullable = false)
    private LocalDate requestedOrderedFrom;

    @Column(name = "requested_ordered_to", nullable = false)
    private LocalDate requestedOrderedTo;

    @Column(name = "queried_ordered_from", nullable = false)
    private LocalDate queriedOrderedFrom;

    @Column(name = "queried_ordered_to", nullable = false)
    private LocalDate queriedOrderedTo;

    /**
     * 서버 구간 분할이 예산 부족으로 요청 구간을 끝까지 커버하지 못했을 때, 어디까지
     * 완전히 끝난 창의 끝 날짜를 남긴다. {@code null}이면 요청 구간을 끝까지 커버했다는 뜻이다.
     */
    @Column(name = "covered_ordered_to")
    private LocalDate coveredOrderedTo;

    /**
     * 불완전 이력(부분 커버)이면서 보유 수량 대조가 {@code MATCHED}가 아닌 실행을 사용자가 그
     * 사실을 알고 승인했다는 기록이다. {@code null}이면 아직 확인하지 않았거나 애초에 확인이
     * 필요하지 않았던 실행이다(완전 커버 또는 대조 {@code MATCHED}).
     */
    @Column(name = "coverage_acknowledged_at")
    private LocalDateTime coverageAcknowledgedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coverage_acknowledged_by_member_id")
    private Member coverageAcknowledgedByMember;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BrokerOrderImportRunStatus status;

    @Embedded
    private BrokerOrderImportRunCounts counts;

    @Enumerated(EnumType.STRING)
    @Column(name = "reconciliation_status", nullable = false, length = 20)
    private BrokerOrderReconciliationStatus reconciliationStatus;

    @Column(name = "reconciliation_snapshot_synced_at")
    private LocalDateTime reconciliationSnapshotSyncedAt;

    /** 증권사 원문 오류 메시지는 담지 않는다. 우리가 정의한 코드만 남긴다. */
    @Column(name = "failure_code", length = 50)
    private String failureCode;

    /** 비밀값도 개인정보도 아닌 불투명한 요청 상관 id다. 값이 오지 않으면 비어 있다. */
    @Column(name = "provider_request_id", length = 255)
    private String providerRequestId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "executed_by_member_id", nullable = false)
    private Member executedByMember;

    @OneToMany(mappedBy = "run", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BrokerOrderImportItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "run", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BrokerOrderImportReconciliationLine> reconciliationLines = new ArrayList<>();

    protected BrokerOrderImportRun() {
    }

    private BrokerOrderImportRun(
            Portfolio portfolio,
            BrokerConnection brokerConnection,
            BrokerAccount brokerAccount,
            Member executedByMember,
            LocalDate requestedOrderedFrom,
            LocalDate requestedOrderedTo,
            LocalDate queriedOrderedFrom,
            LocalDate queriedOrderedTo,
            LocalDateTime startedAt,
            LocalDateTime finishedAt,
            BrokerOrderImportRunStatus status
    ) {
        if (portfolio == null || brokerConnection == null || brokerAccount == null || executedByMember == null) {
            throw new IllegalArgumentException("주문 이력 가져오기 실행 정보가 올바르지 않습니다.");
        }
        if (requestedOrderedFrom == null || requestedOrderedTo == null
                || queriedOrderedFrom == null || queriedOrderedTo == null) {
            throw new IllegalArgumentException("주문 이력 조회 구간은 필수입니다.");
        }
        if (requestedOrderedFrom.isAfter(requestedOrderedTo) || queriedOrderedFrom.isAfter(queriedOrderedTo)) {
            throw new IllegalArgumentException("주문 이력 조회 시작일은 종료일보다 늦을 수 없습니다.");
        }
        if (startedAt == null || status == null) {
            throw new IllegalArgumentException("주문 이력 가져오기 실행 상태가 올바르지 않습니다.");
        }

        this.portfolio = portfolio;
        this.brokerConnection = brokerConnection;
        this.brokerAccount = brokerAccount;
        this.executedByMember = executedByMember;
        this.provider = brokerConnection.getProvider();
        this.requestedOrderedFrom = requestedOrderedFrom;
        this.requestedOrderedTo = requestedOrderedTo;
        this.queriedOrderedFrom = queriedOrderedFrom;
        this.queriedOrderedTo = queriedOrderedTo;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.status = status;
        this.counts = BrokerOrderImportRunCounts.of(BrokerOrderImportCounts.empty());
        this.reconciliationStatus = BrokerOrderReconciliationStatus.NOT_AVAILABLE;
    }

    /**
     * 조회와 분류가 끝난 실행을 만든다. 항목은 실행 시점 값을 그대로 복사해 보존하며
     * 이후 재조회에서 값이 달라져도 이 행들은 갱신하지 않는다.
     */
    public static BrokerOrderImportRun staged(
            Portfolio portfolio,
            BrokerConnection brokerConnection,
            BrokerAccount brokerAccount,
            Member executedByMember,
            LocalDate requestedOrderedFrom,
            LocalDate requestedOrderedTo,
            LocalDate queriedOrderedFrom,
            LocalDate queriedOrderedTo,
            LocalDateTime startedAt,
            LocalDateTime finishedAt,
            BrokerOrderImportCounts counts,
            BrokerOrderReconciliationStatus reconciliationStatus,
            LocalDateTime reconciliationSnapshotSyncedAt,
            List<BrokerOrderStagedOrder> stagedOrders,
            List<BrokerOrderReconciliationLineValue> reconciliationLines,
            LocalDate coveredOrderedTo
    ) {
        if (counts == null || reconciliationStatus == null || stagedOrders == null || reconciliationLines == null) {
            throw new IllegalArgumentException("주문 이력 가져오기 결과가 올바르지 않습니다.");
        }

        BrokerOrderImportRun run = new BrokerOrderImportRun(
                portfolio, brokerConnection, brokerAccount, executedByMember,
                requestedOrderedFrom, requestedOrderedTo, queriedOrderedFrom, queriedOrderedTo,
                startedAt, finishedAt, BrokerOrderImportRunStatus.STAGED);

        run.counts = BrokerOrderImportRunCounts.of(counts);
        run.reconciliationStatus = reconciliationStatus;
        run.reconciliationSnapshotSyncedAt = reconciliationSnapshotSyncedAt;
        run.coveredOrderedTo = coveredOrderedTo;
        stagedOrders.forEach(order -> run.items.add(new BrokerOrderImportItem(run, order)));
        reconciliationLines.forEach(line -> run.reconciliationLines
                .add(new BrokerOrderImportReconciliationLine(run, line)));

        return run;
    }

    /**
     * 실패한 실행을 남긴다. 실패를 저장하지 않으면 재현되지 않는 장애를 영원히 추적할 수 없다.
     * 담기는 것은 정제된 코드와 불투명한 요청 상관 id뿐이며, 증권사 원문 메시지는 담지 않는다.
     */
    public static BrokerOrderImportRun failed(
            Portfolio portfolio,
            BrokerConnection brokerConnection,
            BrokerAccount brokerAccount,
            Member executedByMember,
            LocalDate requestedOrderedFrom,
            LocalDate requestedOrderedTo,
            LocalDate queriedOrderedFrom,
            LocalDate queriedOrderedTo,
            LocalDateTime startedAt,
            LocalDateTime finishedAt,
            String failureCode,
            String providerRequestId
    ) {
        BrokerOrderImportRun run = new BrokerOrderImportRun(
                portfolio, brokerConnection, brokerAccount, executedByMember,
                requestedOrderedFrom, requestedOrderedTo, queriedOrderedFrom, queriedOrderedTo,
                startedAt, finishedAt, BrokerOrderImportRunStatus.FAILED);

        run.failureCode = failureCode;
        run.providerRequestId = providerRequestId;

        return run;
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

    public BrokerProvider getProvider() {
        return provider;
    }

    public LocalDate getRequestedOrderedFrom() {
        return requestedOrderedFrom;
    }

    public LocalDate getRequestedOrderedTo() {
        return requestedOrderedTo;
    }

    public LocalDate getQueriedOrderedFrom() {
        return queriedOrderedFrom;
    }

    public LocalDate getQueriedOrderedTo() {
        return queriedOrderedTo;
    }

    public LocalDate getCoveredOrderedTo() {
        return coveredOrderedTo;
    }

    /** 요청 구간을 끝까지 커버했는지 여부다. {@link #getCoveredOrderedTo()}가 {@code null}이면 참이다. */
    public boolean isFullyCovered() {
        return coveredOrderedTo == null;
    }

    public LocalDateTime getCoverageAcknowledgedAt() {
        return coverageAcknowledgedAt;
    }

    public Long getCoverageAcknowledgedByMemberId() {
        return coverageAcknowledgedByMember == null ? null : coverageAcknowledgedByMember.getId();
    }

    /**
     * 불완전 이력을 사용자가 확인했다는 사실을 처음 성립한 시점에만 남긴다. 이미 확인된
     * 실행에 다시 호출해도 기존 기록을 덮어쓰지 않는다 — 확인 시각·주체는 최초 승인 시점의
     * 사실이며, 재승인·재시도로 바뀌지 않는다.
     */
    public void acknowledgeCoverageIfNeeded(LocalDateTime acknowledgedAt, Member acknowledgingMember) {
        if (coverageAcknowledgedAt != null) {
            return;
        }
        this.coverageAcknowledgedAt = acknowledgedAt;
        this.coverageAcknowledgedByMember = acknowledgingMember;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public BrokerOrderImportRunStatus getStatus() {
        return status;
    }

    public BrokerOrderImportCounts getCounts() {
        return counts.toCounts();
    }

    public BrokerOrderReconciliationStatus getReconciliationStatus() {
        return reconciliationStatus;
    }

    public LocalDateTime getReconciliationSnapshotSyncedAt() {
        return reconciliationSnapshotSyncedAt;
    }

    public String getFailureCode() {
        return failureCode;
    }

    public String getProviderRequestId() {
        return providerRequestId;
    }

    public Long getExecutedByMemberId() {
        return executedByMember.getId();
    }

    public List<BrokerOrderImportItem> getItems() {
        return List.copyOf(items);
    }

    public List<BrokerOrderImportReconciliationLine> getReconciliationLines() {
        return List.copyOf(reconciliationLines);
    }
}
