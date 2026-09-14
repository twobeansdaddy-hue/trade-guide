package com.tradeguide.service.broker;

import com.tradeguide.domain.asset.AssetListing;
import com.tradeguide.domain.asset.ListingStatus;
import com.tradeguide.domain.broker.BrokerAccount;
import com.tradeguide.domain.broker.BrokerConnection;
import com.tradeguide.domain.broker.BrokerConnectionSecretValue;
import com.tradeguide.domain.broker.BrokerHolding;
import com.tradeguide.domain.broker.BrokerOrderImportItem;
import com.tradeguide.domain.broker.BrokerOrderImportRun;
import com.tradeguide.domain.broker.BrokerOrderImportRunStatus;
import com.tradeguide.domain.broker.BrokerOrderLifecycle;
import com.tradeguide.domain.broker.BrokerOrderReconciliationStatus;
import com.tradeguide.domain.broker.BrokerOrderRecord;
import com.tradeguide.domain.broker.BrokerOrderSide;
import com.tradeguide.domain.broker.BrokerOrderStagingStatus;
import com.tradeguide.domain.broker.BrokerProvider;
import com.tradeguide.domain.broker.PortfolioBrokerHoldingSnapshot;
import com.tradeguide.domain.member.Member;
import com.tradeguide.domain.portfolio.Portfolio;
import com.tradeguide.domain.portfolio.PortfolioBrokerLink;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.domain.trade.TradeTransaction;
import com.tradeguide.domain.trade.TradeType;
import com.tradeguide.exception.PortfolioBrokerLinkNotFoundException;
import com.tradeguide.exception.PortfolioNotFoundException;
import com.tradeguide.repository.asset.AssetListingRepository;
import com.tradeguide.repository.broker.BrokerConnectionRepository;
import com.tradeguide.repository.broker.BrokerOrderImportRunRepository;
import com.tradeguide.repository.broker.BrokerOrderLedgerLinkRepository;
import com.tradeguide.repository.broker.PortfolioBrokerHoldingSnapshotRepository;
import com.tradeguide.repository.broker.PortfolioBrokerLinkRepository;
import com.tradeguide.repository.member.MemberRepository;
import com.tradeguide.repository.portfolio.PortfolioRepository;
import com.tradeguide.repository.trade.TradeTransactionRepository;
import com.tradeguide.service.holding.HoldingCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 스테이징을 진짜 저장소 위에서 끝까지 돌린다. 이 단계의 가장 중요한 약속은
 * "매매 원장을 한 행도 바꾸지 않는다"인데, 그 약속은 목으로는 증명되지 않는다.
 */
@DataJpaTest
class BrokerOrderImportRunWriterTest {

    private static final Instant ORDERED_AT = Instant.parse("2026-09-01T00:30:00Z");
    private static final Instant FILLED_AT = Instant.parse("2026-09-01T13:30:00Z");
    private static final LocalDateTime STARTED_AT = LocalDateTime.of(2026, 9, 8, 9, 0);

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PortfolioRepository portfolioRepository;

    @Autowired
    private BrokerConnectionRepository brokerConnectionRepository;

    @Autowired
    private PortfolioBrokerLinkRepository portfolioBrokerLinkRepository;

    @Autowired
    private BrokerOrderImportRunRepository brokerOrderImportRunRepository;

    @Autowired
    private BrokerOrderLedgerLinkRepository brokerOrderLedgerLinkRepository;

    @Autowired
    private PortfolioBrokerHoldingSnapshotRepository portfolioBrokerHoldingSnapshotRepository;

    @Autowired
    private TradeTransactionRepository tradeTransactionRepository;

    @Autowired
    private AssetListingRepository assetListingRepository;

    @Autowired
    private TestEntityManager entityManager;

    private BrokerOrderImportRunWriter writer;
    private Member member;
    private Portfolio portfolio;
    private BrokerConnection connection;
    private BrokerAccount account;

    @BeforeEach
    void setUp() {
        writer = new BrokerOrderImportRunWriter(
                portfolioRepository,
                portfolioBrokerLinkRepository,
                brokerOrderImportRunRepository,
                brokerOrderLedgerLinkRepository,
                portfolioBrokerHoldingSnapshotRepository,
                tradeTransactionRepository,
                assetListingRepository,
                new BrokerOrderStagingClassifier(),
                new BrokerOrderReconciler(new HoldingCalculator())
        );

        member = memberRepository.save(new Member("broker@example.com", "broker-user"));
        portfolio = portfolioRepository.save(new Portfolio(member, "성장 포트폴리오"));

        BrokerConnection newConnection = new BrokerConnection(member, BrokerProvider.TOSS_SECURITIES, "개인 토스증권");
        newConnection.replaceSecretValues(List.of(
                new BrokerConnectionSecretValue("clientId", "encrypted-client-id", "client-id-iv", 1),
                new BrokerConnectionSecretValue("clientSecret", "encrypted-client-secret", "client-secret-iv", 1)
        ));
        newConnection.reconcileVerifiedAccounts(List.of(
                new BrokerAccount("encrypted-sequence", "sequence-iv", "*****1234", "위탁", 1)
        ));
        newConnection.markConnected("*****1234");
        connection = brokerConnectionRepository.saveAndFlush(newConnection);
        account = connection.getAccounts().getFirst();

        portfolioBrokerLinkRepository.saveAndFlush(
                new PortfolioBrokerLink(portfolio, connection, account, LocalDateTime.of(2026, 9, 4, 9, 30)));
    }

    /**
     * 이 단계 전체가 서 있는 약속이다. 조회와 분류가 아무리 복잡해져도 매매 원장은 그대로여야 한다.
     */
    @Test
    void stagingDoesNotChangeASingleRowInTheTradeLedger() {
        TradeTransaction existing = tradeTransactionRepository.saveAndFlush(new TradeTransaction(
                portfolio, Market.US, "AAPL", TradeType.BUY,
                new BigDecimal("4"), new BigDecimal("90.0000"), BigDecimal.ZERO,
                Instant.parse("2026-08-01T13:30:00Z")));
        entityManager.flush();
        entityManager.clear();

        writer.stage(stagingRequest(List.of(filled("order-1", "10"))));
        entityManager.flush();
        entityManager.clear();

        List<TradeTransaction> ledger =
                tradeTransactionRepository.findAllByPortfolio_IdOrderByTradedAtAsc(portfolio.getId());

        assertThat(ledger).singleElement().satisfies(transaction -> {
            assertThat(transaction.getId()).isEqualTo(existing.getId());
            assertThat(transaction.getQuantity()).isEqualByComparingTo("4");
        });
    }

    @Test
    void persistsEveryFetchedOrderAsAnItemEvenWhenItCannotBeStaged() {
        BrokerOrderRecord controlRecord = new BrokerOrderRecord(
                "order-2", Market.US, "AAPL", BrokerOrderSide.BUY, BrokerOrderLifecycle.CONTROL_RECORD,
                "CANCEL_REJECTED", "LIMIT", "DAY", "USD",
                new BigDecimal("10"), new BigDecimal("10"), new BigDecimal("100.25"), new BigDecimal("1002.50"),
                BigDecimal.ZERO, BigDecimal.ZERO, ORDERED_AT, FILLED_AT, null);

        BrokerOrderImportRun run = writer.stage(stagingRequest(List.of(filled("order-1", "10"), controlRecord)));
        entityManager.flush();
        entityManager.clear();

        BrokerOrderImportRun found = brokerOrderImportRunRepository.findById(run.getId()).orElseThrow();

        assertThat(found.getStatus()).isEqualTo(BrokerOrderImportRunStatus.STAGED);
        assertThat(found.getItems()).extracting(BrokerOrderImportItem::getStagingStatus)
                .containsExactlyInAnyOrder(
                        BrokerOrderStagingStatus.STAGED, BrokerOrderStagingStatus.SKIPPED_CONTROL_RECORD);
        assertThat(found.getCounts().fetchedCount()).isEqualTo(2);
        assertThat(found.getCounts().classifiedCount()).isEqualTo(found.getCounts().fetchedCount());
    }

    @Test
    void fillsTheDisplayNameFromTheAssetCatalogueWhenTheTickerIsKnown() {
        assetListingRepository.saveAndFlush(new AssetListing(Market.US, "AAPL", "Apple Inc.", ListingStatus.ACTIVE));

        BrokerOrderImportRun run = writer.stage(stagingRequest(List.of(filled("order-1", "10"))));
        entityManager.flush();
        entityManager.clear();

        assertThat(brokerOrderImportRunRepository.findById(run.getId()).orElseThrow().getItems())
                .singleElement()
                .satisfies(item -> assertThat(item.getDisplayName()).isEqualTo("Apple Inc."));
    }

    @Test
    void reportsMatchedReconciliationWhenTheStagedOrdersReproduceTheBrokerSnapshot() {
        saveSnapshot(new BigDecimal("10"));

        BrokerOrderImportRun run = writer.stage(stagingRequest(List.of(filled("order-1", "10"))));
        entityManager.flush();
        entityManager.clear();

        BrokerOrderImportRun found = brokerOrderImportRunRepository.findById(run.getId()).orElseThrow();

        assertThat(found.getReconciliationStatus()).isEqualTo(BrokerOrderReconciliationStatus.MATCHED);
        assertThat(found.getReconciliationSnapshotSyncedAt()).isEqualTo(LocalDateTime.of(2026, 9, 7, 9, 0));
        assertThat(found.getReconciliationLines()).singleElement()
                .satisfies(line -> assertThat(line.getQuantityDifference()).isEqualByComparingTo("0"));
    }

    /** 이력이 앞에서 잘리면 재구성 수량이 모자란다. 차이를 메우지 않고 그대로 보고한다. */
    @Test
    void reportsMismatchAndTheGapWhenTheFetchedHistoryIsShortOfTheBrokerSnapshot() {
        saveSnapshot(new BigDecimal("10"));

        BrokerOrderImportRun run = writer.stage(stagingRequest(List.of(filled("order-1", "4"))));
        entityManager.flush();
        entityManager.clear();

        BrokerOrderImportRun found = brokerOrderImportRunRepository.findById(run.getId()).orElseThrow();

        assertThat(found.getReconciliationStatus()).isEqualTo(BrokerOrderReconciliationStatus.MISMATCHED);
        assertThat(found.getReconciliationLines()).singleElement()
                .satisfies(line -> assertThat(line.getQuantityDifference()).isEqualByComparingTo("-6"));
    }

    /** 대조할 기준이 없으면 "이상 없음"이 아니라 "대조 불가"다. 둘을 같게 보이면 안 된다. */
    @Test
    void reportsNotAvailableWhenNoBrokerHoldingSnapshotExists() {
        BrokerOrderImportRun run = writer.stage(stagingRequest(List.of(filled("order-1", "10"))));
        entityManager.flush();
        entityManager.clear();

        BrokerOrderImportRun found = brokerOrderImportRunRepository.findById(run.getId()).orElseThrow();

        assertThat(found.getReconciliationStatus()).isEqualTo(BrokerOrderReconciliationStatus.NOT_AVAILABLE);
        assertThat(found.getReconciliationLines()).isEmpty();
    }

    /**
     * 사용자가 손으로 적은 매매와 겹쳐 보이는 주문은 자동으로 고르지 않는다.
     * 자동 반영은 이중 계상을 만들고, 자동 무시는 진짜 거래를 잃는다.
     */
    @Test
    void flagsOrdersThatOverlapAManualTradeInsteadOfDecidingForTheUser() {
        tradeTransactionRepository.saveAndFlush(new TradeTransaction(
                portfolio, Market.US, "AAPL", TradeType.BUY,
                new BigDecimal("10"), new BigDecimal("100.2500"), BigDecimal.ZERO, FILLED_AT));
        entityManager.flush();
        entityManager.clear();

        BrokerOrderImportRun run = writer.stage(stagingRequest(List.of(filled("order-1", "10"))));
        entityManager.flush();
        entityManager.clear();

        BrokerOrderImportRun found = brokerOrderImportRunRepository.findById(run.getId()).orElseThrow();

        assertThat(found.getItems()).singleElement().satisfies(item ->
                assertThat(item.getStagingStatus())
                        .isEqualTo(BrokerOrderStagingStatus.MANUAL_OVERLAP_SUSPECTED));
        assertThat(found.getCounts().manualOverlapSuspectedCount()).isEqualTo(1);
    }

    /**
     * 같은 구간을 두 번 실행했을 때 제공자가 같은 주문에 다른 식별자를 주면, 승인 단계에서
     * 원장이 중복된다. 원장을 건드리지 않는 이 단계에서 그 사실을 관측하는 것이 목적이다.
     */
    @Test
    void detectsThatTheProviderRenamedAnOrderBetweenTwoRunsOfTheSameRange() {
        writer.stage(stagingRequest(List.of(filled("order-1", "10"))));
        entityManager.flush();
        entityManager.clear();

        BrokerOrderImportRun secondRun = writer.stage(stagingRequest(List.of(filled("order-9", "10"))));
        entityManager.flush();
        entityManager.clear();

        BrokerOrderImportRun found = brokerOrderImportRunRepository.findById(secondRun.getId()).orElseThrow();

        assertThat(found.getItems()).singleElement().satisfies(item ->
                assertThat(item.getStagingStatus()).isEqualTo(BrokerOrderStagingStatus.DUPLICATE_SUSPECTED));
        assertThat(found.getCounts().duplicateSuspectedCount()).isEqualTo(1);
    }

    /** 같은 식별자로 다시 오는 것은 정상 재조회다. 자기 자신을 중복으로 의심하면 기능을 못 쓴다. */
    @Test
    void treatsAnUnchangedOrderIdentifierOnReRunAsANormalRefetch() {
        writer.stage(stagingRequest(List.of(filled("order-1", "10"))));
        entityManager.flush();
        entityManager.clear();

        BrokerOrderImportRun secondRun = writer.stage(stagingRequest(List.of(filled("order-1", "10"))));
        entityManager.flush();
        entityManager.clear();

        assertThat(brokerOrderImportRunRepository.findById(secondRun.getId()).orElseThrow().getItems())
                .singleElement()
                .satisfies(item -> assertThat(item.getStagingStatus()).isEqualTo(BrokerOrderStagingStatus.STAGED));
    }

    /** 이미 원장에 반영된 주문은 반영 후보로 다시 올라오지 않는다. */
    @Test
    void marksOrdersThatAlreadyHaveAnActiveLedgerLinkAsAlreadyImported() {
        BrokerOrderImportRun firstRun = writer.stage(stagingRequest(List.of(filled("order-1", "10"))));
        entityManager.flush();
        Long itemId = firstRun.getItems().getFirst().getId();
        insertActiveLedgerLink(firstRun.getId(), itemId, "order-1");
        entityManager.clear();

        BrokerOrderImportRun secondRun = writer.stage(stagingRequest(List.of(filled("order-1", "10"))));
        entityManager.flush();
        entityManager.clear();

        BrokerOrderImportRun found = brokerOrderImportRunRepository.findById(secondRun.getId()).orElseThrow();

        assertThat(found.getItems()).singleElement().satisfies(item ->
                assertThat(item.getStagingStatus()).isEqualTo(BrokerOrderStagingStatus.ALREADY_IMPORTED));
        assertThat(found.getCounts().alreadyImportedCount()).isEqualTo(1);
    }

    /**
     * 매매 원장에 쓰는 코드가 아직 없으므로 반영 링크는 SQL로 직접 넣는다.
     * 이 테스트가 검증하는 것은 링크를 읽어 판정하는 경로이지 링크를 만드는 경로가 아니다.
     */
    private void insertActiveLedgerLink(Long runId, Long itemId, String externalOrderId) {
        entityManager.getEntityManager().createNativeQuery("""
                        INSERT INTO broker_order_ledger_links (
                            broker_account_id, external_order_id, run_id, item_id, trade_transaction_id,
                            approved_quantity, approved_price, approved_fee, approved_traded_at,
                            approved_by_member_id, approved_at, status
                        ) VALUES (?, ?, ?, ?, NULL, 10, 100.25, 0, ?, ?, ?, 'ACTIVE')
                        """)
                .setParameter(1, account.getId())
                .setParameter(2, externalOrderId)
                .setParameter(3, runId)
                .setParameter(4, itemId)
                .setParameter(5, FILLED_AT)
                .setParameter(6, member.getId())
                .setParameter(7, STARTED_AT)
                .executeUpdate();
    }

    private void saveSnapshot(BigDecimal quantity) {
        portfolioBrokerHoldingSnapshotRepository.saveAndFlush(new PortfolioBrokerHoldingSnapshot(
                portfolio,
                connection,
                account,
                LocalDateTime.of(2026, 9, 7, 9, 0),
                0,
                List.of(new BrokerHolding(Market.US, "AAPL", quantity, new BigDecimal("100.25")))
        ));
        entityManager.flush();
        entityManager.clear();
    }

    /** 서버 구간 분할이 넘겨준 부분 커버 정보를 실행 행에 그대로 남긴다. */
    @Test
    void persistsThePartialCoverageDateWhenTheFetchDidNotFinishTheWholeRange() {
        BrokerOrderImportRun run = writer.stage(
                stagingRequest(List.of(filled("order-1", "10")), LocalDate.of(2026, 9, 3)));
        entityManager.flush();
        entityManager.clear();

        BrokerOrderImportRun found = brokerOrderImportRunRepository.findById(run.getId()).orElseThrow();

        assertThat(found.getCoveredOrderedTo()).isEqualTo(LocalDate.of(2026, 9, 3));
        assertThat(found.isFullyCovered()).isFalse();
    }

    /** 요청 구간을 끝까지 커버했으면 세 컬럼 모두 비어 있어야 한다. */
    @Test
    void leavesCoverageColumnsEmptyWhenTheFetchCoveredTheWholeRange() {
        BrokerOrderImportRun run = writer.stage(stagingRequest(List.of(filled("order-1", "10"))));
        entityManager.flush();
        entityManager.clear();

        BrokerOrderImportRun found = brokerOrderImportRunRepository.findById(run.getId()).orElseThrow();

        assertThat(found.getCoveredOrderedTo()).isNull();
        assertThat(found.isFullyCovered()).isTrue();
        assertThat(found.getCoverageAcknowledgedAt()).isNull();
        assertThat(found.getCoverageAcknowledgedByMemberId()).isNull();
    }

    @Test
    void refusesToStageWithNotFoundWhenThePortfolioDoesNotBelongToTheMember() {
        assertThatThrownBy(() -> writer.stage(new BrokerOrderImportRunWriter.StagingRequest(
                999L, portfolio.getId(), connection.getId(), account.getId(),
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 5),
                LocalDate.of(2026, 8, 30), LocalDate.of(2026, 9, 7),
                STARTED_AT, STARTED_AT.plusSeconds(3), List.of(), 0, 0, 0, 0, 0, null)))
                .isInstanceOf(PortfolioNotFoundException.class)
                .hasMessage("포트폴리오를 찾을 수 없습니다.");
    }

    @Test
    void refusesToStageWithNotFoundWhenThePortfolioHasNoBrokerLink() {
        Portfolio unlinkedPortfolio = portfolioRepository.saveAndFlush(new Portfolio(member, "연결 없는 포트폴리오"));

        assertThatThrownBy(() -> writer.stage(new BrokerOrderImportRunWriter.StagingRequest(
                member.getId(), unlinkedPortfolio.getId(), connection.getId(), account.getId(),
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 5),
                LocalDate.of(2026, 8, 30), LocalDate.of(2026, 9, 7),
                STARTED_AT, STARTED_AT.plusSeconds(3), List.of(), 0, 0, 0, 0, 0, null)))
                .isInstanceOf(PortfolioBrokerLinkNotFoundException.class)
                .hasMessage("포트폴리오에 연결된 증권사 계좌가 없습니다.");
    }

    private BrokerOrderImportRunWriter.StagingRequest stagingRequest(List<BrokerOrderRecord> records) {
        return stagingRequest(records, null);
    }

    private BrokerOrderImportRunWriter.StagingRequest stagingRequest(
            List<BrokerOrderRecord> records, LocalDate coveredOrderedTo) {
        return new BrokerOrderImportRunWriter.StagingRequest(
                member.getId(),
                portfolio.getId(),
                connection.getId(),
                account.getId(),
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 5),
                LocalDate.of(2026, 8, 30),
                LocalDate.of(2026, 9, 7),
                STARTED_AT,
                STARTED_AT.plusSeconds(3),
                records,
                0, 0, 0, 0, 0,
                coveredOrderedTo
        );
    }

    private BrokerOrderRecord filled(String orderId, String quantity) {
        return new BrokerOrderRecord(
                orderId, Market.US, "AAPL", BrokerOrderSide.BUY, BrokerOrderLifecycle.TERMINAL_WITH_FILL,
                "FILLED", "LIMIT", "DAY", "USD",
                new BigDecimal(quantity), new BigDecimal(quantity),
                new BigDecimal("100.25"), new BigDecimal(quantity).multiply(new BigDecimal("100.25")),
                BigDecimal.ZERO, BigDecimal.ZERO, ORDERED_AT, FILLED_AT, null);
    }
}
