package com.tradeguide.migration;

import com.tradeguide.domain.broker.BrokerAccount;
import com.tradeguide.domain.broker.BrokerConnection;
import com.tradeguide.domain.broker.BrokerConnectionSecretValue;
import com.tradeguide.domain.broker.BrokerHistoryPageRequest;
import com.tradeguide.domain.broker.BrokerHolding;
import com.tradeguide.domain.broker.BrokerOpeningBalanceBatchResult;
import com.tradeguide.domain.broker.BrokerOpeningBalanceSkip;
import com.tradeguide.domain.broker.BrokerOpeningBalanceSkipReason;
import com.tradeguide.domain.broker.BrokerProvider;
import com.tradeguide.domain.broker.PortfolioBrokerHoldingImport;
import com.tradeguide.domain.broker.PortfolioBrokerHoldingImportStatus;
import com.tradeguide.domain.broker.PortfolioBrokerHoldingSnapshot;
import com.tradeguide.domain.member.Member;
import com.tradeguide.domain.portfolio.Portfolio;
import com.tradeguide.domain.portfolio.PortfolioBrokerLink;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.domain.trade.TradeTransaction;
import com.tradeguide.domain.trade.TradeTransactionSource;
import com.tradeguide.domain.trade.TradeType;
import com.tradeguide.dto.ApiErrorCode;
import com.tradeguide.exception.BrokerHoldingImportUnprocessableException;
import com.tradeguide.repository.broker.BrokerConnectionRepository;
import com.tradeguide.repository.broker.PortfolioBrokerHoldingImportRepository;
import com.tradeguide.repository.broker.PortfolioBrokerHoldingSnapshotRepository;
import com.tradeguide.repository.broker.PortfolioBrokerLinkRepository;
import com.tradeguide.repository.member.MemberRepository;
import com.tradeguide.repository.portfolio.PortfolioRepository;
import com.tradeguide.repository.trade.TradeTransactionRepository;
import com.tradeguide.service.broker.PortfolioBrokerHoldingImportService;
import com.tradeguide.service.broker.PortfolioBrokerOpeningBalanceBatchWriter;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

/**
 * 일괄 개시 잔고 반영과 이력 서버 페이징을 실제 PostgreSQL에서 검증한다.
 *
 * <p>H2로는 확인할 수 없는 두 가지가 여기서 드러난다. 하나는 새 Flyway 인덱스가 적용된
 * 스키마에서 정렬이 실제로 결정적인가이고, 다른 하나는 같은 승인 시각을 가진 일괄 반영 이력이
 * 페이지 경계에서 흔들리지 않는가다.
 *
 * <p>이 테스트는 어떤 증권사 API도 호출하지 않는다. 저장된 스냅샷과 매매 원장만 읽고 쓴다.
 * Docker가 필요하며 기본 {@code test} 태스크에서 제외된다.
 */
@Tag("postgres")
@Testcontainers
@ActiveProfiles("test")
@SpringBootTest
class PostgresBrokerOpeningBalanceBatchIntegrationTest {

    private static final LocalDateTime SNAPSHOT_BASE_SYNCED_AT = LocalDateTime.of(2026, 9, 4, 9, 30);

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
    private PortfolioBrokerLinkRepository portfolioBrokerLinkRepository;

    @Autowired
    private PortfolioBrokerHoldingSnapshotRepository portfolioBrokerHoldingSnapshotRepository;

    @Autowired
    private PortfolioBrokerHoldingImportRepository portfolioBrokerHoldingImportRepository;

    @Autowired
    private TradeTransactionRepository tradeTransactionRepository;

    @Autowired
    private PortfolioBrokerOpeningBalanceBatchWriter batchWriter;

    @Autowired
    private PortfolioBrokerHoldingImportService importService;

    private Member member;
    private int savedSnapshotCount;
    private Portfolio portfolio;
    private BrokerConnection connection;
    private BrokerAccount account;

    @BeforeEach
    void setUp() {
        portfolioBrokerHoldingImportRepository.deleteAll();
        portfolioBrokerLinkRepository.deleteAll();
        portfolioBrokerHoldingSnapshotRepository.deleteAll();
        tradeTransactionRepository.deleteAll();
        brokerConnectionRepository.deleteAll();
        portfolioRepository.deleteAll();
        memberRepository.deleteAll();
        savedSnapshotCount = 0;

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

        portfolioBrokerLinkRepository.saveAndFlush(
                new PortfolioBrokerLink(portfolio, connection, account, SNAPSHOT_BASE_SYNCED_AT));
    }

    @Test
    void approvesEveryOnlyInBrokerHoldingAndExcludesTheConflictingOnesWithReasons() {
        // 전량 매도로 보유 수량은 0이지만 원장 이력이 남아 있는 종목이다.
        tradeTransactionRepository.saveAndFlush(new TradeTransaction(
                portfolio, Market.US, "TSLA", TradeType.BUY,
                new BigDecimal("5"), new BigDecimal("200.00"), BigDecimal.ZERO,
                Instant.parse("2026-08-01T00:00:00Z")));
        tradeTransactionRepository.saveAndFlush(new TradeTransaction(
                portfolio, Market.US, "TSLA", TradeType.SELL,
                new BigDecimal("5"), new BigDecimal("210.00"), BigDecimal.ZERO,
                Instant.parse("2026-08-02T00:00:00Z")));

        PortfolioBrokerHoldingSnapshot snapshot = saveSnapshot(
                new BrokerHolding(Market.US, "SOXL", "Direxion Daily Semiconductor Bull 3X",
                        new BigDecimal("30"), new BigDecimal("20.00")),
                new BrokerHolding(Market.US, "PFE", "Pfizer Inc.",
                        new BigDecimal("50"), new BigDecimal("28.50")),
                new BrokerHolding(Market.US, "TSLA", "Tesla, Inc.",
                        new BigDecimal("5"), new BigDecimal("200.00"))
        );

        BrokerOpeningBalanceBatchResult result = batchWriter.approveAll(
                member.getId(), portfolio.getId(), snapshot.getId(), member.getId());

        assertThat(result.approved())
                .extracting(PortfolioBrokerHoldingImport::getTicker)
                .containsExactlyInAnyOrder("SOXL", "PFE");
        assertThat(result.skipped())
                .extracting(BrokerOpeningBalanceSkip::ticker, BrokerOpeningBalanceSkip::reason)
                .containsExactly(tuple("TSLA", BrokerOpeningBalanceSkipReason.LEDGER_CONFLICT));

        // 반영된 종목만 개시 잔고 원장 행이 생긴다. 기존 수동 기록은 그대로다.
        assertThat(tradeTransactionRepository.findAllByPortfolio_IdOrderByTradedAtAsc(portfolio.getId()))
                .filteredOn(transaction ->
                        transaction.getSource() == TradeTransactionSource.BROKER_OPENING_BALANCE)
                .extracting(TradeTransaction::getTicker)
                .containsExactlyInAnyOrder("SOXL", "PFE");
        assertThat(portfolioBrokerHoldingImportRepository.count()).isEqualTo(2);
    }

    /**
     * 같은 스냅샷을 다시 반영해도 이미 반영된 종목이 두 번 들어가지 않는다.
     *
     * <p>이때 사유는 {@code ALREADY_APPROVED}가 아니라 {@code NOT_ONLY_IN_BROKER}다. 반영으로
     * 원장에 보유 수량이 생겼으므로 비교 결과 자체가 이미 {@code MATCHED}로 바뀌어 있다.
     * 사용자에게는 "이미 승인했다"보다 "지금은 양쪽이 일치한다"가 현재 상태를 더 정확히 말해 준다.
     */
    @Test
    void doesNotApproveTheSameHoldingTwiceWhenTheBatchIsRepeated() {
        PortfolioBrokerHoldingSnapshot snapshot = saveSnapshot(
                new BrokerHolding(Market.US, "SOXL", new BigDecimal("30"), new BigDecimal("20.00")));

        batchWriter.approveAll(member.getId(), portfolio.getId(), snapshot.getId(), member.getId());

        BrokerOpeningBalanceBatchResult second = batchWriter.approveAll(
                member.getId(), portfolio.getId(), snapshot.getId(), member.getId());

        assertThat(second.approved()).isEmpty();
        assertThat(second.skipped()).singleElement()
                .satisfies(skip -> assertThat(skip.reason())
                        .isEqualTo(BrokerOpeningBalanceSkipReason.NOT_ONLY_IN_BROKER));
        assertThat(portfolioBrokerHoldingImportRepository.count()).isEqualTo(1);
    }

    /**
     * 일괄 반영은 모든 승인 이력이 같은 {@code approved_at}을 갖는다. id 동점 기준이 없으면
     * 페이지 경계에서 같은 이력이 두 번 나오거나 빠진다. 실제 PostgreSQL에서 확인한다.
     */
    @Test
    void pagesBatchApprovedHistoryWithoutDuplicatesOrGapsDespiteIdenticalApprovalTimestamps() {
        List<BrokerHolding> holdings = new ArrayList<>();
        for (int index = 0; index < 5; index++) {
            holdings.add(new BrokerHolding(
                    Market.US, "TCK" + index, new BigDecimal("10"), new BigDecimal("11.00")));
        }
        PortfolioBrokerHoldingSnapshot snapshot = saveSnapshot(holdings.toArray(new BrokerHolding[0]));

        BrokerOpeningBalanceBatchResult result = batchWriter.approveAll(
                member.getId(), portfolio.getId(), snapshot.getId(), member.getId());
        assertThat(result.approved()).hasSize(5);
        assertThat(result.approved())
                .extracting(PortfolioBrokerHoldingImport::getApprovedAt)
                .containsOnly(result.approved().getFirst().getApprovedAt());

        List<Long> pagedIds = new ArrayList<>();
        boolean hasNext = true;
        for (int page = 0; page < 5 && hasNext; page++) {
            var historyPage = importService.getImportHistory(
                    member.getId(), portfolio.getId(), new BrokerHistoryPageRequest(page, 2));

            assertThat(historyPage.totalElements()).isEqualTo(5);
            assertThat(historyPage.page()).isEqualTo(page);
            assertThat(historyPage.size()).isEqualTo(2);
            historyPage.items().forEach(record -> pagedIds.add(record.getId()));
            hasNext = historyPage.hasNext();
        }

        assertThat(hasNext).isFalse();
        assertThat(pagedIds).hasSize(5).doesNotHaveDuplicates();
        assertThat(pagedIds).isSortedAccordingTo((left, right) -> Long.compare(right, left));
    }

    @Test
    void keepsRevokedOpeningBalanceOutOfALaterBatch() {
        PortfolioBrokerHoldingSnapshot snapshot = saveSnapshot(
                new BrokerHolding(Market.US, "SOXL", new BigDecimal("30"), new BigDecimal("20.00")));
        BrokerOpeningBalanceBatchResult first = batchWriter.approveAll(
                member.getId(), portfolio.getId(), snapshot.getId(), member.getId());

        importService.revokeOpeningBalance(
                member.getId(), portfolio.getId(), first.approved().getFirst().getId());

        // 취소로 원장 행이 사라졌으므로 새 스냅샷에서는 다시 ONLY_IN_BROKER로 보인다.
        PortfolioBrokerHoldingSnapshot laterSnapshot = saveSnapshot(
                new BrokerHolding(Market.US, "SOXL", new BigDecimal("30"), new BigDecimal("20.00")));

        BrokerOpeningBalanceBatchResult second = batchWriter.approveAll(
                member.getId(), portfolio.getId(), laterSnapshot.getId(), member.getId());

        assertThat(second.approved()).isEmpty();
        assertThat(second.skipped()).singleElement()
                .satisfies(skip -> assertThat(skip.reason())
                        .isEqualTo(BrokerOpeningBalanceSkipReason.PREVIOUSLY_REVOKED));
        assertThat(portfolioBrokerHoldingImportRepository.findAll())
                .singleElement()
                .satisfies(record -> assertThat(record.getStatus())
                        .isEqualTo(PortfolioBrokerHoldingImportStatus.REVOKED));
    }

    /**
     * D1 회귀 테스트를 실제 PostgreSQL에서 확인한다. 단건과 일괄 두 경로 모두, 원장이 표현할
     * 수 없는 시장의 종목은 {@code trade_transactions}에 어떤 행도 남기지 않아야 한다.
     * 매매 원장에는 통화 필드가 없으므로 KR 종목이 한 건이라도 들어가면 원화 금액이 달러
     * 원장에 섞이고, 그 뒤 포트폴리오 평가 조회가 통째로 실패한다.
     */
    @Test
    void neverWritesNonLedgerWritableMarketToTheLedgerThroughEitherApprovalPath() {
        PortfolioBrokerHoldingSnapshot snapshot = saveSnapshot(
                new BrokerHolding(Market.US, "SOXL", "Direxion Daily Semiconductor Bull 3X",
                        new BigDecimal("30"), new BigDecimal("20.00")),
                new BrokerHolding(Market.KR, "005930", "삼성전자",
                        new BigDecimal("10"), new BigDecimal("70000"))
        );
        Long koreanSnapshotItemId = snapshot.getItems().stream()
                .filter(item -> item.getMarket() == Market.KR)
                .findFirst()
                .orElseThrow()
                .getId();

        // 단건 승인 경로: 422로 매핑되는 판정 예외로 거부한다.
        assertThatThrownBy(() -> importService.approveOpeningBalance(
                member.getId(), portfolio.getId(), koreanSnapshotItemId))
                .isInstanceOf(BrokerHoldingImportUnprocessableException.class)
                .satisfies(exception -> assertThat(
                        ((BrokerHoldingImportUnprocessableException) exception).getCode())
                        .isEqualTo(ApiErrorCode.BROKER_LEDGER_MARKET_UNSUPPORTED));
        assertThat(tradeTransactionRepository.count()).isZero();
        assertThat(portfolioBrokerHoldingImportRepository.count()).isZero();

        // 일괄 경로: KR은 사유 있는 제외로 빠지고 US는 그대로 반영된다.
        BrokerOpeningBalanceBatchResult result = batchWriter.approveAll(
                member.getId(), portfolio.getId(), snapshot.getId(), member.getId());

        assertThat(result.approved())
                .extracting(PortfolioBrokerHoldingImport::getTicker)
                .containsExactly("SOXL");
        assertThat(result.skipped())
                .extracting(BrokerOpeningBalanceSkip::ticker, BrokerOpeningBalanceSkip::reason)
                .containsExactly(tuple("005930", BrokerOpeningBalanceSkipReason.UNSUPPORTED_MARKET));

        assertThat(tradeTransactionRepository.findAllByPortfolio_IdOrderByTradedAtAsc(portfolio.getId()))
                .extracting(TradeTransaction::getMarket)
                .containsOnly(Market.US);
    }

    /**
     * 스냅샷마다 서로 다른 기준 시각을 준다. 최신 스냅샷 조회는 {@code syncedAt} 내림차순이므로,
     * 같은 시각으로 여러 건을 저장하면 무엇이 최신인지 정해지지 않는다.
     */
    private PortfolioBrokerHoldingSnapshot saveSnapshot(BrokerHolding... holdings) {
        return portfolioBrokerHoldingSnapshotRepository.saveAndFlush(new PortfolioBrokerHoldingSnapshot(
                portfolio,
                connection,
                account,
                SNAPSHOT_BASE_SYNCED_AT.plusMinutes(savedSnapshotCount++),
                0,
                List.of(holdings)
        ));
    }
}
