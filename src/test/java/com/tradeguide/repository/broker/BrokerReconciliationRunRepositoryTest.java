package com.tradeguide.repository.broker;

import com.tradeguide.domain.broker.BrokerAccount;
import com.tradeguide.domain.broker.BrokerConnection;
import com.tradeguide.domain.broker.BrokerConnectionSecretValue;
import com.tradeguide.domain.broker.BrokerHoldingComparison;
import com.tradeguide.domain.broker.BrokerProvider;
import com.tradeguide.domain.broker.BrokerReconciliationLine;
import com.tradeguide.domain.broker.BrokerReconciliationLineValue;
import com.tradeguide.domain.broker.BrokerReconciliationReasonCode;
import com.tradeguide.domain.broker.BrokerReconciliationRun;
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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code lines.reasons}를 함께 즉시 로딩하는 {@link BrokerReconciliationRunRepository#findByPortfolio_IdAndId}가
 * 한 줄에 사유가 둘 이상 붙어도 그 줄을 정확히 한 번만 반환하는지 검증한다.
 *
 * <p>회귀 대상: {@code lines}가 {@code List}(bag)였을 때, {@code lines.reasons}를 함께 join fetch하면
 * 사유 개수만큼 같은 줄이 중복으로 쌓였다. 사유는 병합됐지만(그래서 reasonCandidates 자체는
 * 맞았다) 줄 배열에는 같은 종목이 여러 번 나타나 matchedCount/quantityMismatchCount와 어긋났다.
 */
@DataJpaTest
class BrokerReconciliationRunRepositoryTest {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PortfolioRepository portfolioRepository;

    @Autowired
    private BrokerConnectionRepository brokerConnectionRepository;

    @Autowired
    private BrokerReconciliationRunRepository brokerReconciliationRunRepository;

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
    void returnsEachLineExactlyOnceWhenALineHasMultipleReasonCandidates() {
        BrokerReconciliationLineValue soxlWithTwoReasons = new BrokerReconciliationLineValue(
                Market.US, "SOXL", "Direxion Daily Semiconductor Bull 3X",
                new BigDecimal("10"), null,
                BrokerHoldingComparison.ONLY_IN_BROKER,
                Set.of(
                        BrokerReconciliationReasonCode.BASELINE_EXCLUDED_HISTORY,
                        BrokerReconciliationReasonCode.OUT_OF_PERIOD_HISTORY
                )
        );
        BrokerReconciliationLineValue aaplMatched = new BrokerReconciliationLineValue(
                Market.US, "AAPL", "Apple Inc.",
                new BigDecimal("10"), new BigDecimal("10"),
                BrokerHoldingComparison.MATCHED,
                Set.of()
        );

        BrokerReconciliationRun run = BrokerReconciliationRun.of(
                portfolio, connection, account,
                1L, LocalDateTime.of(2026, 9, 8, 9, 0),
                member, LocalDateTime.of(2026, 9, 8, 9, 5),
                List.of(soxlWithTwoReasons, aaplMatched)
        );
        BrokerReconciliationRun saved = brokerReconciliationRunRepository.saveAndFlush(run);
        entityManager.clear();

        BrokerReconciliationRun reloaded = brokerReconciliationRunRepository
                .findByPortfolio_IdAndId(portfolio.getId(), saved.getId())
                .orElseThrow();

        assertThat(reloaded.getMatchedCount()).isEqualTo(1);
        assertThat(reloaded.getOnlyInBrokerCount()).isEqualTo(1);
        assertThat(reloaded.getLines()).hasSize(2);
        assertThat(reloaded.getLines()).extracting(BrokerReconciliationLine::getTicker)
                .containsExactly("AAPL", "SOXL");

        BrokerReconciliationLine soxlLine = reloaded.getLines().stream()
                .filter(line -> line.getTicker().equals("SOXL"))
                .findFirst()
                .orElseThrow();
        assertThat(soxlLine.getReasonCandidates()).containsExactlyInAnyOrder(
                BrokerReconciliationReasonCode.BASELINE_EXCLUDED_HISTORY,
                BrokerReconciliationReasonCode.OUT_OF_PERIOD_HISTORY
        );
        assertThat(reloaded.getLines().stream().filter(line -> line.getTicker().equals("SOXL")).count())
                .isEqualTo(1);
    }
}
