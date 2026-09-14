package com.tradeguide.repository.broker;

import com.tradeguide.domain.broker.BrokerAccount;
import com.tradeguide.domain.broker.BrokerConnection;
import com.tradeguide.domain.broker.BrokerConnectionSecretValue;
import com.tradeguide.domain.broker.BrokerProvider;
import com.tradeguide.domain.member.Member;
import com.tradeguide.domain.portfolio.Portfolio;
import com.tradeguide.domain.portfolio.PortfolioBrokerLink;
import com.tradeguide.repository.member.MemberRepository;
import com.tradeguide.repository.portfolio.PortfolioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class PortfolioBrokerLinkRepositoryTest {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PortfolioRepository portfolioRepository;

    @Autowired
    private BrokerConnectionRepository brokerConnectionRepository;

    @Autowired
    private PortfolioBrokerLinkRepository portfolioBrokerLinkRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Portfolio portfolio;
    private BrokerConnection connection;

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
    }

    @Test
    void persistsAndFindsPortfolioBrokerLink() {
        PortfolioBrokerLink saved = portfolioBrokerLinkRepository.saveAndFlush(new PortfolioBrokerLink(
                portfolio,
                connection,
                connection.getAccounts().getFirst(),
                LocalDateTime.of(2026, 9, 4, 9, 30)
        ));
        entityManager.clear();

        Optional<PortfolioBrokerLink> found = portfolioBrokerLinkRepository.findByPortfolio_Id(portfolio.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());
        assertThat(found.get().getBrokerAccount().getMaskedAccountNumber()).isEqualTo("*****1234");
        assertThat(found.get().getLinkedAt()).isEqualTo(LocalDateTime.of(2026, 9, 4, 9, 30));
    }

    @Test
    void deletesLinksByBrokerConnectionSoConnectionRemovalDoesNotLeaveOrphans() {
        portfolioBrokerLinkRepository.saveAndFlush(new PortfolioBrokerLink(
                portfolio,
                connection,
                connection.getAccounts().getFirst(),
                LocalDateTime.of(2026, 9, 4, 9, 30)
        ));

        portfolioBrokerLinkRepository.deleteAllByBrokerConnection_Id(connection.getId());
        portfolioBrokerLinkRepository.flush();
        entityManager.clear();

        assertThat(portfolioBrokerLinkRepository.findByPortfolio_Id(portfolio.getId())).isEmpty();
    }
}
