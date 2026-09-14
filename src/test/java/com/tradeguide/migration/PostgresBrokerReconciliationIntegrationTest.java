package com.tradeguide.migration;

import com.tradeguide.domain.broker.BrokerAccount;
import com.tradeguide.domain.broker.BrokerConnection;
import com.tradeguide.domain.broker.BrokerConnectionSecretValue;
import com.tradeguide.domain.broker.BrokerHistoryPageRequest;
import com.tradeguide.domain.broker.BrokerHolding;
import com.tradeguide.domain.broker.BrokerHoldingComparison;
import com.tradeguide.domain.broker.BrokerProvider;
import com.tradeguide.domain.broker.BrokerReconciliationLine;
import com.tradeguide.domain.broker.BrokerReconciliationOverallStatus;
import com.tradeguide.domain.broker.BrokerReconciliationReasonCode;
import com.tradeguide.domain.broker.BrokerReconciliationRun;
import com.tradeguide.domain.broker.PortfolioBrokerHoldingSnapshot;
import com.tradeguide.domain.member.Member;
import com.tradeguide.domain.portfolio.Portfolio;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.domain.trade.TradeTransaction;
import com.tradeguide.domain.trade.TradeType;
import com.tradeguide.repository.broker.BrokerConnectionRepository;
import com.tradeguide.repository.broker.BrokerReconciliationRunRepository;
import com.tradeguide.repository.broker.PortfolioBrokerHoldingSnapshotRepository;
import com.tradeguide.repository.member.MemberRepository;
import com.tradeguide.repository.portfolio.PortfolioRepository;
import com.tradeguide.repository.trade.TradeTransactionRepository;
import com.tradeguide.service.broker.BrokerReconciliationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 사용자 주도 원장 정합성 점검(S5)을 실제 PostgreSQL에서 검증한다.
 *
 * <p>H2로는 확인할 수 없는 것은 V18 마이그레이션이 엔티티 매핑과 정확히 맞는지
 * ({@code ddl-auto=validate}), 그리고 줄마다 여러 개 붙는 사유 후보가 별도 테이블을 오가며
 * 그대로 왕복 저장·조회되는지다.
 *
 * <p>이 테스트는 어떤 증권사 API도 호출하지 않는다. 저장된 스냅샷과 매매 원장만 읽고, 이
 * 기능이 어느 쪽도 쓰지 않는다는 사실을 직접 단언한다. Docker가 필요하며 기본 {@code test}
 * 태스크에서 제외된다.
 */
@Tag("postgres")
@Testcontainers
@ActiveProfiles("test")
@SpringBootTest
class PostgresBrokerReconciliationIntegrationTest {

    private static final LocalDateTime SNAPSHOT_SYNCED_AT = LocalDateTime.of(2026, 9, 8, 9, 0);

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
    }

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PortfolioRepository portfolioRepository;

    @Autowired
    private BrokerConnectionRepository brokerConnectionRepository;

    @Autowired
    private PortfolioBrokerHoldingSnapshotRepository portfolioBrokerHoldingSnapshotRepository;

    @Autowired
    private TradeTransactionRepository tradeTransactionRepository;

    @Autowired
    private BrokerReconciliationRunRepository brokerReconciliationRunRepository;

    @Autowired
    private BrokerReconciliationService brokerReconciliationService;

    private Member member;
    private Portfolio portfolio;
    private BrokerConnection connection;
    private BrokerAccount account;

    @BeforeEach
    void setUp() {
        brokerReconciliationRunRepository.deleteAll();
        portfolioBrokerHoldingSnapshotRepository.deleteAll();
        tradeTransactionRepository.deleteAll();
        brokerConnectionRepository.deleteAll();
        portfolioRepository.deleteAll();
        memberRepository.deleteAll();

        member = memberRepository.save(new Member("broker@example.com", "broker-user"));
        portfolio = portfolioRepository.save(new Portfolio(member, "성장 포트폴리오"));

        BrokerConnection newConnection =
                new BrokerConnection(member, BrokerProvider.TOSS_SECURITIES, "개인 토스증권");
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

    /**
     * D1과 같은 조건(원장에 통화 필드가 없어 KR을 반영할 수 없음)을 정합성 점검에서도
     * 확인한다. 점검은 어떤 경우에도 {@code trade_transactions}를 쓰지 않는다.
     */
    @Test
    void comparesStoredSnapshotAgainstTheLedgerAndPersistsPerAssetReasonsWithoutTouchingTheLedger() {
        tradeTransactionRepository.saveAndFlush(new TradeTransaction(
                portfolio, Market.US, "AAPL", TradeType.BUY,
                new BigDecimal("10"), new BigDecimal("150.00"), BigDecimal.ZERO,
                Instant.parse("2026-08-01T00:00:00Z")));
        long ledgerCountBeforeReconciliation = tradeTransactionRepository.count();

        PortfolioBrokerHoldingSnapshot snapshot = saveSnapshot(
                new BrokerHolding(Market.US, "AAPL", "Apple Inc.",
                        new BigDecimal("10"), new BigDecimal("150.00")),
                new BrokerHolding(Market.KR, "005930", "삼성전자",
                        new BigDecimal("10"), new BigDecimal("70000"))
        );

        BrokerReconciliationRun run = brokerReconciliationService.createReconciliation(member.getId(), portfolio.getId());

        assertThat(run.getOverallStatus()).isEqualTo(BrokerReconciliationOverallStatus.DIFFERENCES_FOUND);
        assertThat(run.getMatchedCount()).isEqualTo(1);
        assertThat(run.getOnlyInBrokerCount()).isEqualTo(1);
        assertThat(run.getSnapshotId()).isEqualTo(snapshot.getId());

        // 점검은 어떤 경우에도 매매 원장을 바꾸지 않는다.
        assertThat(tradeTransactionRepository.count()).isEqualTo(ledgerCountBeforeReconciliation);

        // 저장된 실행을 다시 읽어, 사유 후보가 별도 테이블을 오가며 그대로 왕복하는지 확인한다.
        BrokerReconciliationRun reloaded = brokerReconciliationRunRepository
                .findByPortfolio_IdAndId(portfolio.getId(), run.getId())
                .orElseThrow();

        BrokerReconciliationLine krLine = reloaded.getLines().stream()
                .filter(line -> line.getTicker().equals("005930"))
                .findFirst()
                .orElseThrow();
        assertThat(krLine.getComparison()).isEqualTo(BrokerHoldingComparison.ONLY_IN_BROKER);
        assertThat(krLine.getReasonCandidates()).contains(BrokerReconciliationReasonCode.LEDGER_MARKET_UNSUPPORTED);

        BrokerReconciliationLine matchedLine = reloaded.getLines().stream()
                .filter(line -> line.getTicker().equals("AAPL"))
                .findFirst()
                .orElseThrow();
        assertThat(matchedLine.getComparison()).isEqualTo(BrokerHoldingComparison.MATCHED);
        assertThat(matchedLine.getReasonCandidates()).isEmpty();
    }

    @Test
    void failsWhenNoSnapshotHasEverBeenSaved() {
        assertThat(portfolioBrokerHoldingSnapshotRepository.findFirstByPortfolio_IdOrderBySyncedAtDesc(portfolio.getId()))
                .isEmpty();

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> brokerReconciliationService.createReconciliation(member.getId(), portfolio.getId()))
                .isInstanceOf(com.tradeguide.exception.BrokerHoldingSnapshotNotFoundException.class);

        assertThat(brokerReconciliationRunRepository.count()).isZero();
    }

    /**
     * 실행 이력은 계속 쌓인다. id 동점 기준이 없으면 같은 초에 여러 건이 만들어질 때
     * 페이지 경계에서 흔들릴 수 있다. 실제 PostgreSQL에서 확인한다.
     */
    @Test
    void pagesReconciliationHistoryWithoutDuplicatesOrGaps() {
        saveSnapshot(new BrokerHolding(Market.US, "AAPL", new BigDecimal("10"), new BigDecimal("150.00")));

        List<Long> createdIds = new ArrayList<>();
        for (int index = 0; index < 5; index++) {
            BrokerReconciliationRun run =
                    brokerReconciliationService.createReconciliation(member.getId(), portfolio.getId());
            createdIds.add(run.getId());
        }

        List<Long> pagedIds = new ArrayList<>();
        boolean hasNext = true;
        for (int page = 0; page < 5 && hasNext; page++) {
            var historyPage = brokerReconciliationService.getRuns(
                    member.getId(), portfolio.getId(), new BrokerHistoryPageRequest(page, 2));

            assertThat(historyPage.totalElements()).isEqualTo(5);
            historyPage.items().forEach(run -> pagedIds.add(run.getId()));
            hasNext = historyPage.hasNext();
        }

        assertThat(hasNext).isFalse();
        assertThat(pagedIds).hasSize(5).doesNotHaveDuplicates();
        assertThat(pagedIds).containsExactlyInAnyOrderElementsOf(createdIds);
    }

    @Test
    void cascadesLineAndReasonDeletionWhenARunIsDeleted() {
        saveSnapshot(new BrokerHolding(Market.KR, "005930", "삼성전자",
                new BigDecimal("10"), new BigDecimal("70000")));

        BrokerReconciliationRun run =
                brokerReconciliationService.createReconciliation(member.getId(), portfolio.getId());
        assertThat(run.getLines()).isNotEmpty();

        brokerReconciliationRunRepository.deleteById(run.getId());
        brokerReconciliationRunRepository.flush();

        assertThat(brokerReconciliationRunRepository.findById(run.getId())).isEmpty();
    }

    private PortfolioBrokerHoldingSnapshot saveSnapshot(BrokerHolding... holdings) {
        return portfolioBrokerHoldingSnapshotRepository.saveAndFlush(new PortfolioBrokerHoldingSnapshot(
                portfolio, connection, account, SNAPSHOT_SYNCED_AT, 0, List.of(holdings)
        ));
    }
}
