package com.tradeguide.migration;

import com.tradeguide.domain.broker.BrokerAccount;
import com.tradeguide.domain.broker.BrokerConnection;
import com.tradeguide.domain.broker.BrokerConnectionSecretValue;
import com.tradeguide.domain.broker.BrokerCredentials;
import com.tradeguide.domain.broker.BrokerHolding;
import com.tradeguide.domain.broker.BrokerHoldingSnapshot;
import com.tradeguide.domain.broker.BrokerProvider;
import com.tradeguide.domain.broker.PortfolioBrokerHoldingSnapshot;
import com.tradeguide.domain.member.Member;
import com.tradeguide.domain.portfolio.Portfolio;
import com.tradeguide.domain.portfolio.PortfolioBrokerLink;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.exception.BrokerConnectionUnavailableException;
import com.tradeguide.repository.broker.BrokerConnectionRepository;
import com.tradeguide.repository.broker.PortfolioBrokerHoldingSnapshotRepository;
import com.tradeguide.repository.broker.PortfolioBrokerLinkRepository;
import com.tradeguide.repository.member.MemberRepository;
import com.tradeguide.repository.portfolio.PortfolioRepository;
import com.tradeguide.service.broker.BrokerCredentialCipher;
import com.tradeguide.service.broker.BrokerDuplicateCallGuard;
import com.tradeguide.service.broker.BrokerHoldingContextLoader;
import com.tradeguide.service.broker.BrokerHoldingPreviewCalculator;
import com.tradeguide.service.broker.BrokerHoldingsProvider;
import com.tradeguide.service.broker.EncryptedBrokerCredential;
import com.tradeguide.service.broker.PortfolioBrokerHoldingSnapshotService;
import com.tradeguide.service.broker.PortfolioBrokerHoldingSnapshotWriter;
import com.tradeguide.service.holding.HoldingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 증권사 보유 종목 스냅샷 경로의 트랜잭션 경계를 실제 PostgreSQL에서 검증한다.
 *
 * <p>H2와 목으로는 확인할 수 없는 것이 두 가지다. 하나는 <b>조회 컨텍스트가 정말 자기
 * 트랜잭션 안에서 전부 초기화되는가</b>이고(그렇지 않으면 증권사 호출 시점에 지연 로딩이
 * 터진다), 다른 하나는 <b>항목 저장이 실패할 때 스냅샷 헤더까지 함께 롤백되는가</b>다.
 * 두 번째는 실제 DB 제약이 있어야만 재현된다.
 *
 * <p>이 테스트는 어떤 증권사 API도 호출하지 않는다. 보유 종목 조회 어댑터는 고정 값을
 * 돌려주거나 실패하는 테스트 대역이다.
 *
 * <p>암호화 키는 여기서 만든 <b>0으로 채운 더미 키</b>이고, 자격 증명 픽스처도 고정된 더미
 * 문자열이다. 실제 키나 실제 자격 증명은 쓰지 않는다. Docker가 필요하며 기본 {@code test}
 * 태스크에서 제외된다.
 */
@Tag("postgres")
@Testcontainers
@ActiveProfiles("test")
@SpringBootTest
class PostgresBrokerHoldingSnapshotTransactionIntegrationTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-09-04T09:30:00Z"), ZoneOffset.UTC);
    private static final LocalDateTime LINKED_AT = LocalDateTime.of(2026, 9, 1, 0, 0);

    /** 스키마의 {@code display_name VARCHAR(255)}를 넘겨 항목 저장만 실패시키는 값이다. */
    private static final String OVERLONG_DISPLAY_NAME = "N".repeat(300);

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
        // 더미 키다. 복호화가 실제로 동작하는지만 보면 되므로 0으로 채운 32바이트를 쓴다.
        registry.add("tradeguide.broker.encryption-key",
                () -> Base64.getEncoder().encodeToString(new byte[32]));
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
    private BrokerCredentialCipher brokerCredentialCipher;

    /** 실제 스프링 빈이라 {@code @Transactional} 프록시가 붙어 있다. 그 경계를 확인한다. */
    @Autowired
    private BrokerHoldingContextLoader brokerHoldingContextLoader;

    @Autowired
    private PortfolioBrokerHoldingSnapshotWriter portfolioBrokerHoldingSnapshotWriter;

    @Autowired
    private HoldingService holdingService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Member member;
    private Portfolio portfolio;
    private BrokerConnection connection;
    private BrokerAccount account;
    private RecordingHoldingsProvider holdingsProvider;

    @BeforeEach
    void setUp() {
        portfolioBrokerLinkRepository.deleteAll();
        portfolioBrokerHoldingSnapshotRepository.deleteAll();
        brokerConnectionRepository.deleteAll();
        portfolioRepository.deleteAll();
        memberRepository.deleteAll();

        holdingsProvider = new RecordingHoldingsProvider();

        member = memberRepository.save(new Member("broker@example.com", "broker-user"));
        portfolio = portfolioRepository.save(new Portfolio(member, "성장 포트폴리오"));
        connection = brokerConnectionRepository.saveAndFlush(verifiedConnection("*****1234"));
        account = connection.getAccounts().getFirst();
        portfolioBrokerLinkRepository.saveAndFlush(
                new PortfolioBrokerLink(portfolio, connection, account, LINKED_AT));
    }

    /**
     * 컨텍스트 로더는 앰비언트 트랜잭션이 없는 상태에서 호출돼도 증권사 호출에 필요한 값을
     * 전부 초기화해 돌려준다. 이것이 성립해야 증권사 호출을 트랜잭션 밖으로 뺄 수 있다.
     *
     * <p>로더의 {@code @Transactional}이 사라지면 자격 증명 컬렉션이 준영속 상태에서 지연
     * 로딩돼 이 테스트가 깨진다. 그 실패가 곧 회귀 신호다.
     */
    @Test
    void loadsEveryValueTheBrokerCallNeedsBeforeAnyTransactionCloses() {
        BrokerHoldingContextLoader.HoldingContext context =
                brokerHoldingContextLoader.load(member.getId(), portfolio.getId());

        assertThat(context.brokerConnectionId()).isEqualTo(connection.getId());
        assertThat(context.brokerAccountId()).isEqualTo(account.getId());
        assertThat(context.provider()).isEqualTo(BrokerProvider.TOSS_SECURITIES);
        assertThat(context.maskedAccountNumber()).isEqualTo("*****1234");
        assertThat(context.credentials().values())
                .containsEntry("clientId", "dummy-client-id")
                .containsEntry("clientSecret", "dummy-client-secret");
        assertThat(context.accountSequence()).isEqualTo("dummy-account-sequence");
        // 평문은 문자열 표현으로 새지 않는다.
        assertThat(context.toString()).doesNotContain("dummy-account-sequence", "dummy-client-secret");
    }

    @Test
    void persistsTheSnapshotAndItsItemsInOneCommit() {
        holdingsProvider.snapshot = new BrokerHoldingSnapshot(List.of(
                new BrokerHolding(Market.US, "SOXL", "Direxion Daily Semiconductor Bull 3X",
                        new BigDecimal("30"), new BigDecimal("20.00")),
                new BrokerHolding(Market.US, "AAPL", "Apple Inc.",
                        new BigDecimal("10"), new BigDecimal("150.00"))
        ), 2);

        PortfolioBrokerHoldingSnapshot saved =
                serviceWithStubbedContext().refreshSnapshot(member.getId(), portfolio.getId());

        assertThat(saved.getId()).isNotNull();
        assertThat(snapshotRowCount()).isEqualTo(1);
        assertThat(snapshotItemRowCount()).isEqualTo(2);
        assertThat(portfolioBrokerHoldingSnapshotRepository
                .findFirstByPortfolio_IdOrderBySyncedAtDesc(portfolio.getId()))
                .get()
                .satisfies(snapshot -> {
                    assertThat(snapshot.getSyncedAt()).isEqualTo(LocalDateTime.of(2026, 9, 4, 9, 30));
                    assertThat(snapshot.getUnsupportedMarketCount()).isEqualTo(2);
                    assertThat(snapshot.getItems()).extracting("ticker")
                            .containsExactlyInAnyOrder("SOXL", "AAPL");
                });
    }

    /**
     * 증권사 호출이 실패하면 쓰기 트랜잭션이 시작조차 하지 않는다. 부분 영속화의 첫 번째
     * 방어선이며, 여기서는 "행이 하나도 생기지 않는다"로 확인한다.
     */
    @Test
    void writesNothingWhenTheBrokerCallFails() {
        holdingsProvider.failure = new BrokerConnectionUnavailableException("증권사 응답을 받지 못했습니다.");

        assertThatThrownBy(() ->
                serviceWithStubbedContext().refreshSnapshot(member.getId(), portfolio.getId()))
                .isInstanceOf(BrokerConnectionUnavailableException.class);

        assertThat(snapshotRowCount()).isZero();
        assertThat(snapshotItemRowCount()).isZero();
    }

    /**
     * 부분 영속화의 두 번째 방어선이다. 항목 하나가 스키마 제약에 걸려 저장에 실패하면
     * 스냅샷 헤더까지 함께 롤백돼야 한다. 헤더만 남으면 "항목이 하나도 없는 스냅샷"이
     * 최근 스냅샷으로 조회되고, 사용자는 보유 종목이 사라진 것으로 읽는다.
     */
    @Test
    void rollsBackTheSnapshotHeaderWhenPersistingAnItemFails() {
        holdingsProvider.snapshot = new BrokerHoldingSnapshot(List.of(
                new BrokerHolding(Market.US, "SOXL", "Direxion Daily Semiconductor Bull 3X",
                        new BigDecimal("30"), new BigDecimal("20.00")),
                new BrokerHolding(Market.US, "AAPL", OVERLONG_DISPLAY_NAME,
                        new BigDecimal("10"), new BigDecimal("150.00"))
        ), 0);

        assertThatThrownBy(() ->
                serviceWithStubbedContext().refreshSnapshot(member.getId(), portfolio.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(snapshotRowCount()).isZero();
        assertThat(snapshotItemRowCount()).isZero();
    }

    /**
     * 조회하는 동안 사용자가 링크를 다른 계좌로 바꾸면 저장하지 않는다. 조회를 트랜잭션 밖으로
     * 빼면서 조회 시점과 저장 시점 사이가 벌어졌기 때문에 새로 생긴 경계 조건이다.
     */
    @Test
    void refusesAndWritesNothingWhenTheLinkChangedWhileTheBrokerCallWasInFlight() {
        BrokerHoldingContextLoader.HoldingContext staleContext =
                brokerHoldingContextLoader.load(member.getId(), portfolio.getId());

        BrokerConnection otherConnection = brokerConnectionRepository.saveAndFlush(
                verifiedConnection("*****9999"));
        PortfolioBrokerLink link = portfolioBrokerLinkRepository
                .findByPortfolio_Id(portfolio.getId())
                .orElseThrow();
        link.changeAccount(otherConnection, otherConnection.getAccounts().getFirst(), LINKED_AT);
        portfolioBrokerLinkRepository.saveAndFlush(link);

        assertThatThrownBy(() -> portfolioBrokerHoldingSnapshotWriter.save(
                new PortfolioBrokerHoldingSnapshotWriter.SaveRequest(
                        member.getId(),
                        portfolio.getId(),
                        staleContext.brokerConnectionId(),
                        staleContext.brokerAccountId(),
                        LocalDateTime.now(FIXED_CLOCK),
                        new BrokerHoldingSnapshot(List.of(
                                new BrokerHolding(Market.US, "SOXL",
                                        new BigDecimal("30"), new BigDecimal("20.00"))
                        ), 0)
                )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("증권사 계좌 연결이 바뀌었습니다");

        assertThat(snapshotRowCount()).isZero();
        assertThat(snapshotItemRowCount()).isZero();
    }

    /**
     * 실제 증권사를 부르지 않도록 컨텍스트 로더만 대역으로 바꾼 서비스다. 라이터는 스프링 빈
     * 그대로여서 트랜잭션 경계가 운영과 같고, 서비스 자신은 프록시가 아니어서
     * {@code refreshSnapshot}에 트랜잭션이 없다는 사실도 함께 확인된다.
     */
    private PortfolioBrokerHoldingSnapshotService serviceWithStubbedContext() {
        BrokerHoldingContextLoader stubLoader = mock(BrokerHoldingContextLoader.class);
        when(stubLoader.load(member.getId(), portfolio.getId())).thenReturn(
                new BrokerHoldingContextLoader.HoldingContext(
                        connection.getId(),
                        account.getId(),
                        BrokerProvider.TOSS_SECURITIES,
                        "*****1234",
                        holdingsProvider,
                        new BrokerCredentials(java.util.Map.of("clientId", "dummy-client-id")),
                        "dummy-account-sequence"
                ));

        return new PortfolioBrokerHoldingSnapshotService(
                portfolioRepository,
                portfolioBrokerHoldingSnapshotRepository,
                stubLoader,
                portfolioBrokerHoldingSnapshotWriter,
                holdingService,
                new BrokerHoldingPreviewCalculator(),
                new BrokerDuplicateCallGuard(),
                FIXED_CLOCK
        );
    }

    private BrokerConnection verifiedConnection(String maskedAccountNumber) {
        BrokerConnection newConnection =
                new BrokerConnection(member, BrokerProvider.TOSS_SECURITIES, "개인 토스증권 " + maskedAccountNumber);
        newConnection.replaceSecretValues(List.of(
                secretValue("clientId", "dummy-client-id"),
                secretValue("clientSecret", "dummy-client-secret")
        ));

        EncryptedBrokerCredential encryptedSequence = brokerCredentialCipher.encrypt("dummy-account-sequence");
        newConnection.reconcileVerifiedAccounts(List.of(new BrokerAccount(
                encryptedSequence.ciphertext(),
                encryptedSequence.initializationVector(),
                maskedAccountNumber,
                "위탁",
                encryptedSequence.keyVersion()
        )));
        newConnection.markConnected(maskedAccountNumber);
        return newConnection;
    }

    private BrokerConnectionSecretValue secretValue(String fieldKey, String dummyPlaintext) {
        EncryptedBrokerCredential encrypted = brokerCredentialCipher.encrypt(dummyPlaintext);
        return new BrokerConnectionSecretValue(
                fieldKey,
                encrypted.ciphertext(),
                encrypted.initializationVector(),
                encrypted.keyVersion()
        );
    }

    private int snapshotRowCount() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM broker_holding_snapshots", Integer.class);
    }

    private int snapshotItemRowCount() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM broker_holding_snapshot_items", Integer.class);
    }

    /** 테스트용 가짜 제공자다. 실제 토스증권 API를 호출하지 않는다. */
    private static final class RecordingHoldingsProvider implements BrokerHoldingsProvider {

        private BrokerHoldingSnapshot snapshot = new BrokerHoldingSnapshot(List.of(), 0);
        private RuntimeException failure;

        @Override
        public BrokerProvider getProvider() {
            return BrokerProvider.TOSS_SECURITIES;
        }

        @Override
        public BrokerHoldingSnapshot fetchHoldings(BrokerCredentials credentials, String accountSequence) {
            if (failure != null) {
                throw failure;
            }
            return snapshot;
        }
    }
}
