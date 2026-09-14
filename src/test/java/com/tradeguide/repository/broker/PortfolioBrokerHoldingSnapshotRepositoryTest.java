package com.tradeguide.repository.broker;

import com.tradeguide.domain.broker.BrokerAccount;
import com.tradeguide.domain.broker.BrokerConnection;
import com.tradeguide.domain.broker.BrokerConnectionSecretValue;
import com.tradeguide.domain.broker.BrokerHolding;
import com.tradeguide.domain.broker.BrokerProvider;
import com.tradeguide.domain.broker.PortfolioBrokerHoldingSnapshot;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class PortfolioBrokerHoldingSnapshotRepositoryTest {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PortfolioRepository portfolioRepository;

    @Autowired
    private BrokerConnectionRepository brokerConnectionRepository;

    @Autowired
    private PortfolioBrokerHoldingSnapshotRepository portfolioBrokerHoldingSnapshotRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Portfolio portfolio;
    private BrokerConnection connection;
    private BrokerAccount account;

    @BeforeEach
    void setUp() {
        Member member = memberRepository.save(new Member("broker@example.com", "broker-user"));
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
    void persistsSnapshotWithItemsAndFindsMostRecentByPortfolio() {
        portfolioBrokerHoldingSnapshotRepository.saveAndFlush(new PortfolioBrokerHoldingSnapshot(
                portfolio,
                connection,
                account,
                LocalDateTime.of(2026, 9, 1, 9, 30),
                0,
                List.of(new BrokerHolding(Market.US, "AAPL", new BigDecimal("10"), new BigDecimal("140.00")))
        ));
        PortfolioBrokerHoldingSnapshot latest = portfolioBrokerHoldingSnapshotRepository.saveAndFlush(
                new PortfolioBrokerHoldingSnapshot(
                        portfolio,
                        connection,
                        account,
                        LocalDateTime.of(2026, 9, 4, 9, 30),
                        1,
                        List.of(new BrokerHolding(Market.US, "SOXL", new BigDecimal("30"), new BigDecimal("20.00")))
                )
        );
        entityManager.clear();

        Optional<PortfolioBrokerHoldingSnapshot> found = portfolioBrokerHoldingSnapshotRepository
                .findFirstByPortfolio_IdOrderBySyncedAtDesc(portfolio.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(latest.getId());
        assertThat(found.get().getSyncedAt()).isEqualTo(LocalDateTime.of(2026, 9, 4, 9, 30));
        assertThat(found.get().getUnsupportedMarketCount()).isEqualTo(1);
        assertThat(found.get().getBrokerAccount().getMaskedAccountNumber()).isEqualTo("*****1234");
        assertThat(found.get().getItems()).extracting("ticker", "quantity", "averagePurchasePrice")
                .containsExactly(org.assertj.core.groups.Tuple.tuple(
                        "SOXL", new BigDecimal("30.000000"), new BigDecimal("20.0000")
                ));
    }

    @Test
    void returnsEmptyWhenPortfolioHasNoSnapshotYet() {
        assertThat(portfolioBrokerHoldingSnapshotRepository
                .findFirstByPortfolio_IdOrderBySyncedAtDesc(portfolio.getId())).isEmpty();
    }
}
