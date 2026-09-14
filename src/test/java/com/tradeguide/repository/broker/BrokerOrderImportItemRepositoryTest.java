package com.tradeguide.repository.broker;

import com.tradeguide.domain.broker.BrokerAccount;
import com.tradeguide.domain.broker.BrokerConnection;
import com.tradeguide.domain.broker.BrokerConnectionSecretValue;
import com.tradeguide.domain.broker.BrokerOrderImportCounts;
import com.tradeguide.domain.broker.BrokerOrderImportItem;
import com.tradeguide.domain.broker.BrokerOrderImportRun;
import com.tradeguide.domain.broker.BrokerOrderLifecycle;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실행 한 건의 주문 항목을 나눠 읽는 조회를 검증한다.
 *
 * <p>여기서 확인하려는 것은 세 가지다. 페이지 경계가 흔들리지 않는지, 거르기가 저장소에서
 * 실제로 적용되는지, 그리고 다른 실행의 항목이 섞이지 않는지다.
 */
@DataJpaTest
class BrokerOrderImportItemRepositoryTest {

    /** 서비스가 사용하는 항목 목록 정렬과 같다. */
    private static final Sort ITEM_SORT =
            Sort.by(Sort.Order.desc("orderedAt"), Sort.Order.desc("id"));

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
    private BrokerOrderImportItemRepository brokerOrderImportItemRepository;

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
    void readsItemsOfOneRunNewestOrderedFirstOnePageAtATime() {
        Long runId = savedRunId(
                stagedItem("order-1", "AAPL", Instant.parse("2026-09-01T00:30:00Z")),
                stagedItem("order-2", "AAPL", Instant.parse("2026-09-03T00:30:00Z")),
                stagedItem("order-3", "AAPL", Instant.parse("2026-09-02T00:30:00Z"))
        );

        Page<BrokerOrderImportItem> firstPage = brokerOrderImportItemRepository
                .findRunItems(runId, null, null, PageRequest.of(0, 2, ITEM_SORT));

        assertThat(firstPage.getTotalElements()).isEqualTo(3);
        assertThat(firstPage.hasNext()).isTrue();
        assertThat(firstPage.getContent()).extracting(BrokerOrderImportItem::getExternalOrderId)
                .containsExactly("order-2", "order-3");

        Page<BrokerOrderImportItem> secondPage = brokerOrderImportItemRepository
                .findRunItems(runId, null, null, PageRequest.of(1, 2, ITEM_SORT));

        assertThat(secondPage.hasNext()).isFalse();
        assertThat(secondPage.getContent()).extracting(BrokerOrderImportItem::getExternalOrderId)
                .containsExactly("order-1");
    }

    /**
     * 같은 시각에 주문된 건은 흔하다. 동점 기준이 없으면 페이지를 넘길 때 같은 항목이 두 번
     * 나오거나 아예 빠지므로, 두 페이지를 합쳤을 때 전체가 중복 없이 나와야 한다.
     */
    @Test
    void keepsPageBoundariesStableWhenManyItemsShareTheSameOrderTime() {
        Instant sameInstant = Instant.parse("2026-09-01T00:30:00Z");
        Long runId = savedRunId(
                stagedItem("order-1", "AAPL", sameInstant),
                stagedItem("order-2", "AAPL", sameInstant),
                stagedItem("order-3", "AAPL", sameInstant),
                stagedItem("order-4", "AAPL", sameInstant)
        );

        List<String> firstPage = orderIds(brokerOrderImportItemRepository
                .findRunItems(runId, null, null, PageRequest.of(0, 2, ITEM_SORT)));
        List<String> secondPage = orderIds(brokerOrderImportItemRepository
                .findRunItems(runId, null, null, PageRequest.of(1, 2, ITEM_SORT)));

        assertThat(firstPage).hasSize(2);
        assertThat(secondPage).hasSize(2);
        assertThat(firstPage).doesNotContainAnyElementsOf(secondPage);
    }

    @Test
    void narrowsItemsByStagingStatus() {
        Long runId = savedRunId(
                stagedItem("order-1", "AAPL", Instant.parse("2026-09-01T00:30:00Z")),
                pendingItem("order-2", "AAPL", Instant.parse("2026-09-02T00:30:00Z"))
        );

        Page<BrokerOrderImportItem> page = brokerOrderImportItemRepository.findRunItems(
                runId, BrokerOrderStagingStatus.STAGED, null, PageRequest.of(0, 20, ITEM_SORT));

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent()).extracting(BrokerOrderImportItem::getExternalOrderId)
                .containsExactly("order-1");
    }

    /** 사용자가 소문자로 적어도 같은 종목이어야 한다. */
    @Test
    void narrowsItemsBySymbolIgnoringCase() {
        Long runId = savedRunId(
                stagedItem("order-1", "AAPL", Instant.parse("2026-09-01T00:30:00Z")),
                stagedItem("order-2", "MSFT", Instant.parse("2026-09-02T00:30:00Z"))
        );

        Page<BrokerOrderImportItem> page = brokerOrderImportItemRepository.findRunItems(
                runId, null, "MSFT", PageRequest.of(0, 20, ITEM_SORT));

        assertThat(page.getContent()).extracting(BrokerOrderImportItem::getTicker)
                .containsExactly("MSFT");
    }

    @Test
    void appliesTheStatusAndSymbolFiltersTogether() {
        Long runId = savedRunId(
                stagedItem("order-1", "AAPL", Instant.parse("2026-09-01T00:30:00Z")),
                pendingItem("order-2", "AAPL", Instant.parse("2026-09-02T00:30:00Z")),
                stagedItem("order-3", "MSFT", Instant.parse("2026-09-03T00:30:00Z"))
        );

        Page<BrokerOrderImportItem> page = brokerOrderImportItemRepository.findRunItems(
                runId, BrokerOrderStagingStatus.STAGED, "AAPL", PageRequest.of(0, 20, ITEM_SORT));

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent()).extracting(BrokerOrderImportItem::getExternalOrderId)
                .containsExactly("order-1");
    }

    /** 실행 감사 기록은 실행 단위로 격리돼야 한다. 다른 실행의 항목이 한 건이라도 섞이면 안 된다. */
    @Test
    void neverMixesItemsFromAnotherRun() {
        Long firstRunId = savedRunId(stagedItem("order-1", "AAPL", Instant.parse("2026-09-01T00:30:00Z")));
        Long secondRunId = savedRunId(stagedItem("order-2", "AAPL", Instant.parse("2026-09-02T00:30:00Z")));

        assertThat(orderIds(brokerOrderImportItemRepository
                .findRunItems(firstRunId, null, null, PageRequest.of(0, 20, ITEM_SORT))))
                .containsExactly("order-1");
        assertThat(orderIds(brokerOrderImportItemRepository
                .findRunItems(secondRunId, null, null, PageRequest.of(0, 20, ITEM_SORT))))
                .containsExactly("order-2");
    }

    private List<String> orderIds(Page<BrokerOrderImportItem> page) {
        return page.getContent().stream().map(BrokerOrderImportItem::getExternalOrderId).toList();
    }

    private Long savedRunId(BrokerOrderStagedOrder... items) {
        List<BrokerOrderStagedOrder> stagedOrders = List.of(items);
        LocalDateTime startedAt = LocalDateTime.of(2026, 9, 8, 9, 0);

        BrokerOrderImportRun saved = brokerOrderImportRunRepository.saveAndFlush(BrokerOrderImportRun.staged(
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
                countsFor(stagedOrders),
                BrokerOrderReconciliationStatus.NOT_AVAILABLE,
                null,
                stagedOrders,
                List.of(),
                null
        ));
        entityManager.clear();

        return saved.getId();
    }

    private BrokerOrderImportCounts countsFor(List<BrokerOrderStagedOrder> items) {
        int staged = (int) items.stream()
                .filter(item -> item.stagingStatus() == BrokerOrderStagingStatus.STAGED)
                .count();
        return new BrokerOrderImportCounts(
                items.size(), staged, 0, items.size() - staged, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0);
    }

    private BrokerOrderStagedOrder stagedItem(String orderId, String ticker, Instant orderedAt) {
        BrokerOrderRecord record = new BrokerOrderRecord(
                orderId, Market.US, ticker, BrokerOrderSide.BUY, BrokerOrderLifecycle.TERMINAL_WITH_FILL,
                "FILLED", "LIMIT", "DAY", "USD",
                new BigDecimal("10"), new BigDecimal("10"), new BigDecimal("100.25"), new BigDecimal("1002.50"),
                new BigDecimal("1.00"), new BigDecimal("0.00"), orderedAt, FILLED_AT, LocalDate.of(2026, 9, 3));

        return new BrokerOrderStagedOrder(
                record, "표시명", BrokerOrderStagingStatus.STAGED, null,
                "fingerprint-" + orderId, false, false, false);
    }

    private BrokerOrderStagedOrder pendingItem(String orderId, String ticker, Instant orderedAt) {
        BrokerOrderRecord record = new BrokerOrderRecord(
                orderId, Market.US, ticker, BrokerOrderSide.BUY, BrokerOrderLifecycle.PARTIALLY_FILLED_OPEN,
                "PARTIAL_FILLED", "LIMIT", "DAY", "USD",
                new BigDecimal("10"), new BigDecimal("5"), new BigDecimal("100.25"), new BigDecimal("501.25"),
                new BigDecimal("0.50"), new BigDecimal("0.30"), orderedAt, FILLED_AT, null);

        return new BrokerOrderStagedOrder(
                record, "표시명", BrokerOrderStagingStatus.PENDING_SETTLEMENT,
                BrokerOrderSkipReason.PARTIAL_FILL_PENDING,
                "fingerprint-" + orderId, true, false, true);
    }
}
