package com.tradeguide.repository.broker;

import com.tradeguide.domain.broker.BrokerAccount;
import com.tradeguide.domain.broker.BrokerConnection;
import com.tradeguide.domain.broker.BrokerConnectionSecretValue;
import com.tradeguide.domain.broker.BrokerOrderImportCounts;
import com.tradeguide.domain.broker.BrokerOrderImportItem;
import com.tradeguide.domain.broker.BrokerOrderImportRun;
import com.tradeguide.domain.broker.BrokerOrderImportRunStatus;
import com.tradeguide.domain.broker.BrokerOrderLifecycle;
import com.tradeguide.domain.broker.BrokerOrderReconciliationLineValue;
import com.tradeguide.domain.broker.BrokerOrderReconciliationStatus;
import com.tradeguide.domain.broker.BrokerOrderRecord;
import com.tradeguide.domain.broker.BrokerOrderSide;
import com.tradeguide.domain.broker.BrokerOrderSkipReason;
import com.tradeguide.domain.broker.BrokerOrderStagedOrder;
import com.tradeguide.domain.broker.BrokerOrderStagingStatus;
import com.tradeguide.domain.broker.BrokerProvider;
import com.tradeguide.domain.member.Member;
import com.tradeguide.domain.portfolio.Portfolio;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.repository.member.MemberRepository;
import com.tradeguide.repository.portfolio.PortfolioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class BrokerOrderImportRunRepositoryTest {

    /** 서비스가 사용하는 실행 목록 정렬과 같다. */
    private static final Sort RUN_SORT =
            Sort.by(Sort.Order.desc("startedAt"), Sort.Order.desc("id"));

    private static final Instant ORDERED_AT = Instant.parse("2026-09-01T00:30:00Z");
    private static final Instant FILLED_AT = Instant.parse("2026-09-01T13:30:00Z");

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PortfolioRepository portfolioRepository;

    @Autowired
    private BrokerConnectionRepository brokerConnectionRepository;

    @Autowired
    private BrokerOrderImportRunRepository brokerOrderImportRunRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Member member;
    private Portfolio portfolio;
    private BrokerConnection connection;
    private BrokerAccount account;

    @BeforeEach
    void setUp() {
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
    }

    @Test
    void persistsARunWithItsItemsAndReconciliationLines() {
        BrokerOrderImportRun saved = brokerOrderImportRunRepository.saveAndFlush(stagedRun(
                LocalDateTime.of(2026, 9, 8, 9, 0),
                List.of(stagedItem("order-1"), skippedItem("order-2")),
                List.of(new BrokerOrderReconciliationLineValue(
                        Market.US, "AAPL", new BigDecimal("10.000000"), new BigDecimal("12.000000")))
        ));
        entityManager.clear();

        BrokerOrderImportRun found = brokerOrderImportRunRepository.findById(saved.getId()).orElseThrow();

        assertThat(found.getStatus()).isEqualTo(BrokerOrderImportRunStatus.STAGED);
        assertThat(found.getRequestedOrderedFrom()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(found.getQueriedOrderedFrom()).isEqualTo(LocalDate.of(2026, 8, 30));
        assertThat(found.getCounts().fetchedCount()).isEqualTo(2);
        assertThat(found.getItems()).extracting(BrokerOrderImportItem::getExternalOrderId)
                .containsExactlyInAnyOrder("order-1", "order-2");
        assertThat(found.getReconciliationLines()).singleElement().satisfies(line -> {
            assertThat(line.getReconstructedQuantity()).isEqualByComparingTo("10");
            assertThat(line.getSnapshotQuantity()).isEqualByComparingTo("12");
            assertThat(line.getQuantityDifference()).isEqualByComparingTo("-2");
        });
    }

    /** 값과 판정이 모두 왕복해야 한다. 하나라도 빠지면 감사 기록으로서 쓸모가 없다. */
    @Test
    void preservesProviderReportedValuesAndOurVerdictOnEachItem() {
        BrokerOrderImportRun saved = brokerOrderImportRunRepository.saveAndFlush(stagedRun(
                LocalDateTime.of(2026, 9, 8, 9, 0), List.of(skippedItem("order-2")), List.of()));
        entityManager.clear();

        BrokerOrderImportItem item = brokerOrderImportRunRepository.findById(saved.getId())
                .orElseThrow().getItems().getFirst();

        assertThat(item.getProviderStatusCode()).isEqualTo("PARTIAL_FILLED");
        assertThat(item.getLifecycle()).isEqualTo(BrokerOrderLifecycle.PARTIALLY_FILLED_OPEN);
        assertThat(item.getOrderedQuantity()).isEqualByComparingTo("10");
        assertThat(item.getFilledQuantity()).isEqualByComparingTo("5");
        assertThat(item.getAverageFilledPrice()).isEqualByComparingTo("100.25");
        assertThat(item.getFilledAmount()).isEqualByComparingTo("501.25");
        assertThat(item.getTax()).isEqualByComparingTo("0.30");
        assertThat(item.getOrderedAt()).isEqualTo(ORDERED_AT);
        assertThat(item.getFilledAt()).isEqualTo(FILLED_AT);
        assertThat(item.getStagingStatus()).isEqualTo(BrokerOrderStagingStatus.PENDING_SETTLEMENT);
        assertThat(item.getSkipReasonCode()).isEqualTo(BrokerOrderSkipReason.PARTIAL_FILL_PENDING);
        // 신호는 실행 단위 건수와 항목 양쪽에 남아야 "괴리 1건"이 어느 주문인지 짚을 수 있다.
        assertThat(item.isAmountMismatch()).isTrue();
        assertThat(item.isFeeUnknown()).isFalse();
        assertThat(item.isBuyTax()).isTrue();
    }

    /**
     * 커서 순회가 같은 주문을 두 번 담으면 한 실행 안에 같은 주문이 두 행이 된다.
     * 애플리케이션 로직이 실수하더라도 DB가 막아야 한다.
     */
    @Test
    void rejectsTheSameOrderTwiceWithinOneRun() {
        BrokerOrderImportRun run = stagedRun(
                LocalDateTime.of(2026, 9, 8, 9, 0),
                List.of(stagedItem("order-1"), stagedItem("order-1")),
                List.of()
        );

        assertThatThrownBy(() -> brokerOrderImportRunRepository.saveAndFlush(run))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void listsRunsForAPortfolioNewestFirst() {
        brokerOrderImportRunRepository.saveAndFlush(
                stagedRun(LocalDateTime.of(2026, 9, 6, 9, 0), List.of(stagedItem("order-1")), List.of()));
        brokerOrderImportRunRepository.saveAndFlush(
                stagedRun(LocalDateTime.of(2026, 9, 8, 9, 0), List.of(stagedItem("order-2")), List.of()));
        entityManager.clear();

        List<BrokerOrderImportRun> runs = brokerOrderImportRunRepository
                .findAllByPortfolio_Id(portfolio.getId(), PageRequest.of(0, 10, RUN_SORT))
                .getContent();

        assertThat(runs).extracting(BrokerOrderImportRun::getStartedAt)
                .containsExactly(LocalDateTime.of(2026, 9, 8, 9, 0), LocalDateTime.of(2026, 9, 6, 9, 0));
    }

    @Test
    void readsRunHistoryOnePageAtATimeWithTotalCountAndNextPageFlag() {
        for (int index = 0; index < 3; index++) {
            brokerOrderImportRunRepository.saveAndFlush(stagedRun(
                    LocalDateTime.of(2026, 9, 6 + index, 9, 0),
                    List.of(stagedItem("order-" + index)),
                    List.of()
            ));
        }
        entityManager.clear();

        Page<BrokerOrderImportRun> firstPage = brokerOrderImportRunRepository
                .findAllByPortfolio_Id(portfolio.getId(), PageRequest.of(0, 2, RUN_SORT));

        assertThat(firstPage.getTotalElements()).isEqualTo(3);
        assertThat(firstPage.hasNext()).isTrue();
        assertThat(firstPage.getContent()).extracting(BrokerOrderImportRun::getStartedAt)
                .containsExactly(LocalDateTime.of(2026, 9, 8, 9, 0), LocalDateTime.of(2026, 9, 7, 9, 0));

        Page<BrokerOrderImportRun> secondPage = brokerOrderImportRunRepository
                .findAllByPortfolio_Id(portfolio.getId(), PageRequest.of(1, 2, RUN_SORT));

        assertThat(secondPage.hasNext()).isFalse();
        assertThat(secondPage.getContent()).extracting(BrokerOrderImportRun::getStartedAt)
                .containsExactly(LocalDateTime.of(2026, 9, 6, 9, 0));
    }

    /** 직전 실행을 찾을 수 있어야 같은 구간을 두 번 돌렸을 때 주문 식별자가 바뀌었는지 볼 수 있다. */
    @Test
    void findsTheMostRecentStagedRunForAnAccountWithItsItems() {
        brokerOrderImportRunRepository.saveAndFlush(
                stagedRun(LocalDateTime.of(2026, 9, 6, 9, 0), List.of(stagedItem("order-1")), List.of()));
        brokerOrderImportRunRepository.saveAndFlush(
                stagedRun(LocalDateTime.of(2026, 9, 8, 9, 0), List.of(stagedItem("order-2")), List.of()));
        entityManager.clear();

        Optional<BrokerOrderImportRun> found = brokerOrderImportRunRepository
                .findFirstByBrokerAccount_IdAndStatusOrderByStartedAtDesc(
                        account.getId(), BrokerOrderImportRunStatus.STAGED);

        assertThat(found).isPresent();
        assertThat(found.get().getItems()).extracting(BrokerOrderImportItem::getExternalOrderId)
                .containsExactly("order-2");
    }

    /** 실패한 실행은 직전 실행 후보가 아니다. 조회하지 못한 실행을 근거로 비교하면 안 된다. */
    @Test
    void ignoresFailedRunsWhenLookingForThePreviousStagedRun() {
        brokerOrderImportRunRepository.saveAndFlush(
                stagedRun(LocalDateTime.of(2026, 9, 6, 9, 0), List.of(stagedItem("order-1")), List.of()));
        brokerOrderImportRunRepository.saveAndFlush(failedRun(LocalDateTime.of(2026, 9, 8, 9, 0)));
        entityManager.clear();

        Optional<BrokerOrderImportRun> found = brokerOrderImportRunRepository
                .findFirstByBrokerAccount_IdAndStatusOrderByStartedAtDesc(
                        account.getId(), BrokerOrderImportRunStatus.STAGED);

        assertThat(found).isPresent();
        assertThat(found.get().getItems()).extracting(BrokerOrderImportItem::getExternalOrderId)
                .containsExactly("order-1");
    }

    /**
     * 중복 호출 가드의 쿨다운 기준 시각 조회다. 성공(STAGED)이든 실패(FAILED)든 최신 시도
     * 하나를 봐야 한다. 실패한 시도도 이미 증권사를 호출했으므로 쿨다운 계산에 포함해야 한다.
     */
    @Test
    void findsTheMostRecentRunForAPortfolioRegardlessOfStatus() {
        brokerOrderImportRunRepository.saveAndFlush(
                stagedRun(LocalDateTime.of(2026, 9, 6, 9, 0), List.of(stagedItem("order-1")), List.of()));
        brokerOrderImportRunRepository.saveAndFlush(failedRun(LocalDateTime.of(2026, 9, 8, 9, 0)));
        entityManager.clear();

        Optional<BrokerOrderImportRun> found = brokerOrderImportRunRepository
                .findFirstByPortfolio_IdOrderByStartedAtDesc(portfolio.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getStartedAt()).isEqualTo(LocalDateTime.of(2026, 9, 8, 9, 0));
        assertThat(found.get().getStatus()).isEqualTo(BrokerOrderImportRunStatus.FAILED);
    }

    @Test
    void findsNoPreviousRunForAPortfolioThatHasNeverImported() {
        Optional<BrokerOrderImportRun> found = brokerOrderImportRunRepository
                .findFirstByPortfolio_IdOrderByStartedAtDesc(portfolio.getId());

        assertThat(found).isEmpty();
    }

    /** 실패 기록에는 정제된 코드와 불투명한 상관 id만 남는다. */
    @Test
    void persistsAFailedRunWithoutAnyItems() {
        BrokerOrderImportRun saved =
                brokerOrderImportRunRepository.saveAndFlush(failedRun(LocalDateTime.of(2026, 9, 8, 9, 0)));
        entityManager.clear();

        BrokerOrderImportRun found = brokerOrderImportRunRepository.findById(saved.getId()).orElseThrow();

        assertThat(found.getStatus()).isEqualTo(BrokerOrderImportRunStatus.FAILED);
        assertThat(found.getFailureCode()).isEqualTo("PROVIDER_UNAVAILABLE");
        assertThat(found.getProviderRequestId()).isEqualTo("req-abc-123");
        assertThat(found.getItems()).isEmpty();
        assertThat(found.getCounts().fetchedCount()).isZero();
    }

    private BrokerOrderImportRun stagedRun(
            LocalDateTime startedAt,
            List<BrokerOrderStagedOrder> items,
            List<BrokerOrderReconciliationLineValue> lines
    ) {
        return BrokerOrderImportRun.staged(
                portfolio,
                connection,
                account,
                member,
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 5),
                LocalDate.of(2026, 8, 30),
                LocalDate.of(2026, 9, 7),
                startedAt,
                startedAt.plusSeconds(3),
                countsFor(items),
                lines.isEmpty()
                        ? BrokerOrderReconciliationStatus.NOT_AVAILABLE
                        : BrokerOrderReconciliationStatus.MISMATCHED,
                lines.isEmpty() ? null : LocalDateTime.of(2026, 9, 7, 9, 0),
                items,
                lines,
                null
        );
    }

    private BrokerOrderImportRun failedRun(LocalDateTime startedAt) {
        return BrokerOrderImportRun.failed(
                portfolio,
                connection,
                account,
                member,
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 5),
                LocalDate.of(2026, 8, 30),
                LocalDate.of(2026, 9, 7),
                startedAt,
                startedAt.plusSeconds(1),
                "PROVIDER_UNAVAILABLE",
                "req-abc-123"
        );
    }

    private BrokerOrderImportCounts countsFor(List<BrokerOrderStagedOrder> items) {
        int staged = (int) items.stream()
                .filter(item -> item.stagingStatus() == BrokerOrderStagingStatus.STAGED)
                .count();
        return new BrokerOrderImportCounts(
                items.size(), staged, 0, items.size() - staged, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0);
    }

    private BrokerOrderStagedOrder stagedItem(String orderId) {
        BrokerOrderRecord record = new BrokerOrderRecord(
                orderId, Market.US, "AAPL", BrokerOrderSide.BUY, BrokerOrderLifecycle.TERMINAL_WITH_FILL,
                "FILLED", "LIMIT", "DAY", "USD",
                new BigDecimal("10"), new BigDecimal("10"), new BigDecimal("100.25"), new BigDecimal("1002.50"),
                new BigDecimal("1.00"), new BigDecimal("0.00"), ORDERED_AT, FILLED_AT, LocalDate.of(2026, 9, 3));

        return new BrokerOrderStagedOrder(
                record, "애플", BrokerOrderStagingStatus.STAGED, null,
                "fingerprint-" + orderId, false, false, false);
    }

    private BrokerOrderStagedOrder skippedItem(String orderId) {
        BrokerOrderRecord record = new BrokerOrderRecord(
                orderId, Market.US, "AAPL", BrokerOrderSide.BUY, BrokerOrderLifecycle.PARTIALLY_FILLED_OPEN,
                "PARTIAL_FILLED", "LIMIT", "DAY", "USD",
                new BigDecimal("10"), new BigDecimal("5"), new BigDecimal("100.25"), new BigDecimal("501.25"),
                new BigDecimal("0.50"), new BigDecimal("0.30"), ORDERED_AT, FILLED_AT, null);

        return new BrokerOrderStagedOrder(
                record, "애플", BrokerOrderStagingStatus.PENDING_SETTLEMENT,
                BrokerOrderSkipReason.PARTIAL_FILL_PENDING,
                "fingerprint-" + orderId, true, false, true);
    }
}
