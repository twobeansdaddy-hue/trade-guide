package com.tradeguide.domain.broker;

import com.tradeguide.domain.member.Member;
import com.tradeguide.domain.portfolio.Portfolio;
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
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 사용자가 직접 요청한 원장 정합성 점검 한 건의 감사 기록이다.
 *
 * <p>이 실행은 저장된 최신 보유 종목 스냅샷과 현재 매매 원장만 비교한다. 증권사를 호출하지
 * 않으며, 어떤 경우에도 {@code TradeTransaction}이나 파생 {@code Holding}을 만들거나
 * 고치지 않는다. 이 행이 남긴다는 사실은 "그 시점에 무엇을 비교했더니 이런 답이 나왔다"는
 * 사실 하나뿐이다.
 *
 * <p>{@code snapshotId}는 의도적으로 외래키를 걸지 않는다. 증권사 연결이 삭제되면 스냅샷도
 * 함께 지워지는데({@code BrokerConnectionService.deleteBrokerConnection}), 이 실행 기록은
 * 그 뒤에도 "그때 무엇을 비교했는지"를 감사 목적으로 남겨야 한다. 비교에 쓴 사실
 * ({@code snapshotSyncedAt})과 종목별 값은 이미 줄에 그대로 복사돼 있으므로 스냅샷 원본이
 * 사라져도 이 기록의 감사 가치는 줄지 않는다.
 */
@Entity
@Table(name = "broker_reconciliation_runs")
public class BrokerReconciliationRun {

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

    @Column(name = "snapshot_id", nullable = false)
    private Long snapshotId;

    @Column(name = "snapshot_synced_at", nullable = false)
    private LocalDateTime snapshotSyncedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "executed_by_member_id", nullable = false)
    private Member executedByMember;

    @Column(name = "executed_at", nullable = false)
    private LocalDateTime executedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "overall_status", nullable = false, length = 20)
    private BrokerReconciliationOverallStatus overallStatus;

    @Column(name = "matched_count", nullable = false)
    private int matchedCount;

    @Column(name = "quantity_mismatch_count", nullable = false)
    private int quantityMismatchCount;

    @Column(name = "only_in_broker_count", nullable = false)
    private int onlyInBrokerCount;

    @Column(name = "only_in_trade_guide_count", nullable = false)
    private int onlyInTradeGuideCount;

    // Set이어야 한다. lines.reasons를 함께 EntityGraph로 즉시 로딩할 때, 한 줄에 사유가
    // 둘 이상 붙으면 join fetch가 그 줄을 여러 번 반환한다. List(bag)는 중복을 그대로
    // 쌓지만 Set은 같은 영속성 컨텍스트 식별자를 재사용해 중복을 걸러낸다. @OrderBy로
    // 시장·티커 순서를 SQL에서 고정해 조회 순서를 결정적으로 유지한다.
    @OneToMany(mappedBy = "run", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("market ASC, ticker ASC")
    private Set<BrokerReconciliationLine> lines = new LinkedHashSet<>();

    protected BrokerReconciliationRun() {
    }

    private BrokerReconciliationRun(
            Portfolio portfolio,
            BrokerConnection brokerConnection,
            BrokerAccount brokerAccount,
            Long snapshotId,
            LocalDateTime snapshotSyncedAt,
            Member executedByMember,
            LocalDateTime executedAt
    ) {
        if (portfolio == null || brokerConnection == null || brokerAccount == null
                || snapshotId == null || snapshotSyncedAt == null
                || executedByMember == null || executedAt == null) {
            throw new IllegalArgumentException("정합성 점검 실행 정보가 올바르지 않습니다.");
        }

        this.portfolio = portfolio;
        this.brokerConnection = brokerConnection;
        this.brokerAccount = brokerAccount;
        this.snapshotId = snapshotId;
        this.snapshotSyncedAt = snapshotSyncedAt;
        this.executedByMember = executedByMember;
        this.executedAt = executedAt;
    }

    /**
     * 종목별 비교 결과로 실행 한 건을 만든다. 집계는 줄 값에서 다시 계산하며, 호출자가
     * 별도로 건네지 않는다. 집계와 줄이 서로 다른 계산에서 나오면 언젠가 어긋난다.
     */
    public static BrokerReconciliationRun of(
            Portfolio portfolio,
            BrokerConnection brokerConnection,
            BrokerAccount brokerAccount,
            Long snapshotId,
            LocalDateTime snapshotSyncedAt,
            Member executedByMember,
            LocalDateTime executedAt,
            List<BrokerReconciliationLineValue> lineValues
    ) {
        if (lineValues == null) {
            throw new IllegalArgumentException("정합성 점검 결과 줄은 필수입니다.");
        }

        BrokerReconciliationRun run = new BrokerReconciliationRun(
                portfolio, brokerConnection, brokerAccount, snapshotId, snapshotSyncedAt,
                executedByMember, executedAt);

        int matched = 0;
        int quantityMismatch = 0;
        int onlyInBroker = 0;
        int onlyInTradeGuide = 0;
        for (BrokerReconciliationLineValue value : lineValues) {
            run.lines.add(new BrokerReconciliationLine(run, value));
            switch (value.comparison()) {
                case MATCHED -> matched++;
                case QUANTITY_MISMATCH -> quantityMismatch++;
                case ONLY_IN_BROKER -> onlyInBroker++;
                case ONLY_IN_TRADE_GUIDE -> onlyInTradeGuide++;
            }
        }

        run.matchedCount = matched;
        run.quantityMismatchCount = quantityMismatch;
        run.onlyInBrokerCount = onlyInBroker;
        run.onlyInTradeGuideCount = onlyInTradeGuide;
        run.overallStatus = (quantityMismatch + onlyInBroker + onlyInTradeGuide) == 0
                ? BrokerReconciliationOverallStatus.MATCHED
                : BrokerReconciliationOverallStatus.DIFFERENCES_FOUND;

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

    public Long getSnapshotId() {
        return snapshotId;
    }

    public LocalDateTime getSnapshotSyncedAt() {
        return snapshotSyncedAt;
    }

    public Long getExecutedByMemberId() {
        return executedByMember.getId();
    }

    public LocalDateTime getExecutedAt() {
        return executedAt;
    }

    public BrokerReconciliationOverallStatus getOverallStatus() {
        return overallStatus;
    }

    public int getMatchedCount() {
        return matchedCount;
    }

    public int getQuantityMismatchCount() {
        return quantityMismatchCount;
    }

    public int getOnlyInBrokerCount() {
        return onlyInBrokerCount;
    }

    public int getOnlyInTradeGuideCount() {
        return onlyInTradeGuideCount;
    }

    public List<BrokerReconciliationLine> getLines() {
        return lines.stream()
                .sorted(Comparator.comparing(BrokerReconciliationLine::getMarket)
                        .thenComparing(BrokerReconciliationLine::getTicker))
                .toList();
    }
}
