package com.tradeguide.repository.broker;

import com.tradeguide.domain.broker.BrokerAccount;
import com.tradeguide.domain.broker.BrokerConnection;
import com.tradeguide.domain.broker.BrokerConnectionSecretValue;
import com.tradeguide.domain.broker.BrokerOrderImportCounts;
import com.tradeguide.domain.broker.BrokerOrderImportItem;
import com.tradeguide.domain.broker.BrokerOrderImportRun;
import com.tradeguide.domain.broker.BrokerOrderLedgerLink;
import com.tradeguide.domain.broker.BrokerOrderLedgerLinkStatus;
import com.tradeguide.domain.broker.BrokerOrderLifecycle;
import com.tradeguide.domain.broker.BrokerOrderReconciliationStatus;
import com.tradeguide.domain.broker.BrokerOrderRecord;
import com.tradeguide.domain.broker.BrokerOrderSide;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 주문 반영 승인 링크의 실행 단위 조회를 검증한다. 승인 취소는 실행(run) 단위로 요청되므로
 * 그 실행이 만든 활성 링크만 정확히 골라 읽어야 한다.
 */
@DataJpaTest
class BrokerOrderLedgerLinkRepositoryTest {

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
    private BrokerOrderLedgerLinkRepository brokerOrderLedgerLinkRepository;

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
    void findsOnlyTheActiveLinksCreatedByARun() {
        BrokerOrderImportRun run = brokerOrderImportRunRepository.saveAndFlush(
                stagedRun(List.of(stagedItem("order-1"), stagedItem("order-2"))));
        BrokerOrderImportItem itemOne = run.getItems().get(0);
        BrokerOrderImportItem itemTwo = run.getItems().get(1);

        BrokerOrderLedgerLink activeLink = new BrokerOrderLedgerLink(
                account, run, itemOne, 900L, member, LocalDateTime.of(2026, 9, 8, 10, 0));
        BrokerOrderLedgerLink revokedLink = new BrokerOrderLedgerLink(
                account, run, itemTwo, 901L, member, LocalDateTime.of(2026, 9, 8, 10, 0));
        revokedLink.revoke(LocalDateTime.of(2026, 9, 8, 11, 0));

        brokerOrderLedgerLinkRepository.saveAndFlush(activeLink);
        brokerOrderLedgerLinkRepository.saveAndFlush(revokedLink);
        entityManager.clear();

        List<BrokerOrderLedgerLink> found = brokerOrderLedgerLinkRepository
                .findAllByRun_IdAndStatus(run.getId(), BrokerOrderLedgerLinkStatus.ACTIVE);

        assertThat(found).extracting(BrokerOrderLedgerLink::getExternalOrderId).containsExactly("order-1");
        assertThat(found.getFirst().getTradeTransactionId()).isEqualTo(900L);
    }

    private BrokerOrderImportRun stagedRun(List<BrokerOrderStagedOrder> items) {
        int staged = items.size();
        BrokerOrderImportCounts counts = new BrokerOrderImportCounts(
                items.size(), staged, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

        return BrokerOrderImportRun.staged(
                portfolio, connection, account, member,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 5),
                LocalDate.of(2026, 8, 30), LocalDate.of(2026, 9, 7),
                LocalDateTime.of(2026, 9, 8, 9, 0), LocalDateTime.of(2026, 9, 8, 9, 0, 3),
                counts,
                BrokerOrderReconciliationStatus.MATCHED,
                LocalDateTime.of(2026, 9, 7, 9, 0),
                items,
                List.of(),
                null
        );
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
}
