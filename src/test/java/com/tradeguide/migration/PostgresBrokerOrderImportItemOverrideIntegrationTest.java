package com.tradeguide.migration;

import com.tradeguide.domain.broker.BrokerAccount;
import com.tradeguide.domain.broker.BrokerConnection;
import com.tradeguide.domain.broker.BrokerConnectionSecretValue;
import com.tradeguide.domain.broker.BrokerOrderImportApprovalAssessment;
import com.tradeguide.domain.broker.BrokerOrderImportCounts;
import com.tradeguide.domain.broker.BrokerOrderImportItem;
import com.tradeguide.domain.broker.BrokerOrderImportItemOverride;
import com.tradeguide.domain.broker.BrokerOrderImportRun;
import com.tradeguide.domain.broker.BrokerOrderLifecycle;
import com.tradeguide.domain.broker.BrokerOrderOverrideDecision;
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
import com.tradeguide.repository.broker.BrokerConnectionRepository;
import com.tradeguide.repository.broker.BrokerOrderImportItemOverrideRepository;
import com.tradeguide.repository.broker.BrokerOrderImportItemRepository;
import com.tradeguide.repository.broker.BrokerOrderImportRunRepository;
import com.tradeguide.repository.broker.BrokerOrderLedgerLinkRepository;
import com.tradeguide.repository.member.MemberRepository;
import com.tradeguide.repository.portfolio.PortfolioRepository;
import com.tradeguide.repository.trade.TradeTransactionRepository;
import com.tradeguide.service.broker.BrokerCredentialCipher;
import com.tradeguide.service.broker.BrokerOrderImportApprovalService;
import com.tradeguide.service.broker.BrokerOrderImportApprovalWriter;
import com.tradeguide.service.broker.BrokerOrderImportItemOverrideService;
import com.tradeguide.service.broker.BrokerOrderImportService;
import com.tradeguide.service.broker.EncryptedBrokerCredential;
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V24 재판정 스키마와 승인·취소 흐름을 실제 PostgreSQL에서 검증한다.
 *
 * <p>H2 테스트가 확인하지 못하는 것은 Flyway 마이그레이션 자체다. {@code CHECK} 제약(빈 사유,
 * 결정과 결과 상태의 모순)과 엔티티 매핑 {@code validate}, 원장 링크의 {@code override_id} 외래키가
 * 마이그레이션에만 있으므로 여기서만 걸린다.
 *
 * <p>증권사 API는 호출하지 않는다. 자격 증명은 0으로 채운 더미 키로 암호화한 고정 더미 문자열이다.
 * Docker가 필요하며 기본 {@code test} 태스크에서 제외된다.
 */
@Tag("postgres")
@Testcontainers
@ActiveProfiles("test")
@SpringBootTest
class PostgresBrokerOrderImportItemOverrideIntegrationTest {

    private static final Instant FILLED_AT = Instant.parse("2026-09-01T13:30:00Z");
    private static final String REASON = "증권사 화면에서 수기 기록과 별개 체결로 확인";

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
    private BrokerOrderImportRunRepository brokerOrderImportRunRepository;

    @Autowired
    private BrokerOrderImportItemRepository brokerOrderImportItemRepository;

    @Autowired
    private BrokerOrderImportItemOverrideRepository brokerOrderImportItemOverrideRepository;

    @Autowired
    private BrokerOrderLedgerLinkRepository brokerOrderLedgerLinkRepository;

    @Autowired
    private TradeTransactionRepository tradeTransactionRepository;

    @Autowired
    private BrokerCredentialCipher brokerCredentialCipher;

    @Autowired
    private BrokerOrderImportItemOverrideService brokerOrderImportItemOverrideService;

    @Autowired
    private BrokerOrderImportApprovalService brokerOrderImportApprovalService;

    @Autowired
    private BrokerOrderImportService brokerOrderImportService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Member member;
    private Portfolio portfolio;
    private BrokerOrderImportRun run;

    @BeforeEach
    void setUp() {
        brokerOrderLedgerLinkRepository.deleteAll();
        brokerOrderImportItemOverrideRepository.deleteAll();
        tradeTransactionRepository.deleteAll();
        brokerOrderImportRunRepository.deleteAll();
        brokerConnectionRepository.deleteAll();
        portfolioRepository.deleteAll();
        memberRepository.deleteAll();

        member = memberRepository.save(new Member("override@example.com", "override-user"));
        portfolio = portfolioRepository.save(new Portfolio(member, "성장 포트폴리오"));
        BrokerConnection connection = brokerConnectionRepository.saveAndFlush(verifiedConnection());
        BrokerAccount account = connection.getAccounts().getFirst();

        run = brokerOrderImportRunRepository.saveAndFlush(BrokerOrderImportRun.staged(
                portfolio, connection, account, member,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 5),
                LocalDate.of(2026, 8, 30), LocalDate.of(2026, 9, 7),
                LocalDateTime.of(2026, 9, 8, 9, 0), LocalDateTime.of(2026, 9, 8, 9, 0, 3),
                new BrokerOrderImportCounts(3, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 0, 0, 0, 0, 0, 0),
                BrokerOrderReconciliationStatus.MATCHED,
                LocalDateTime.of(2026, 9, 7, 9, 0),
                List.of(
                        order("order-staged", BrokerOrderStagingStatus.STAGED, null),
                        order("order-manual", BrokerOrderStagingStatus.MANUAL_OVERLAP_SUSPECTED,
                                BrokerOrderSkipReason.MANUAL_OVERLAP),
                        order("order-duplicate", BrokerOrderStagingStatus.DUPLICATE_SUSPECTED,
                                BrokerOrderSkipReason.DUPLICATE_FINGERPRINT)
                ),
                List.of(),
                null
        ));
    }

    /**
     * 판정 집계와 승인이 같은 유효 상태 규칙을 쓰고, 승인 취소 뒤에도 재판정 행과 원장 링크의
     * 재판정 참조가 남는다.
     */
    @Test
    void approvesAllowedItemsAndPreservesTheOverrideAuditAfterRevoke() {
        BrokerOrderImportItemOverride allow = brokerOrderImportItemOverrideService.createOverride(
                member.getId(), portfolio.getId(), run.getId(), item("order-manual").getId(),
                BrokerOrderOverrideDecision.ALLOW_LEDGER_WRITE, REASON).override();
        brokerOrderImportItemOverrideService.createOverride(
                member.getId(), portfolio.getId(), run.getId(), item("order-duplicate").getId(),
                BrokerOrderOverrideDecision.KEEP_EXCLUDED, "같은 주문으로 판단");

        BrokerOrderImportApprovalAssessment approval = brokerOrderImportService
                .getRun(member.getId(), portfolio.getId(), run.getId())
                .approval();
        assertThat(approval.stagedCount()).isEqualTo(1);
        assertThat(approval.eligibleCount()).isEqualTo(2);
        assertThat(approval.overrideAllowedCount()).isEqualTo(1);
        assertThat(approval.overrideKeptExcludedCount()).isEqualTo(1);
        assertThat(approval.unresolvedSuspectedCount()).isZero();
        assertThat(approval.writableCount()).isEqualTo(2);

        BrokerOrderImportApprovalWriter.ApprovalResult result = brokerOrderImportApprovalService
                .approve(member.getId(), portfolio.getId(), run.getId(), false);
        assertThat(result.writtenCount()).isEqualTo(2);
        assertThat(tradeTransactionRepository.count()).isEqualTo(2);
        assertThat(overrideIdOfLink("order-manual")).isEqualTo(allow.getId());
        assertThat(overrideIdOfLink("order-staged")).isNull();

        brokerOrderImportApprovalService.revoke(member.getId(), portfolio.getId(), run.getId());

        assertThat(tradeTransactionRepository.count()).isZero();
        assertThat(brokerOrderImportItemOverrideRepository.count()).isEqualTo(2);
        assertThat(overrideIdOfLink("order-manual")).isEqualTo(allow.getId());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM broker_order_ledger_links WHERE external_order_id = 'order-manual'", String.class))
                .isEqualTo("REVOKED");
    }

    @Test
    void rejectsASecondOverrideRowForTheSameItem() {
        insertOverride(item("order-manual"), "ALLOW_LEDGER_WRITE", "STAGED", REASON);

        assertThatThrownBy(() -> insertOverride(item("order-manual"), "KEEP_EXCLUDED", "MANUAL_OVERLAP_SUSPECTED", REASON))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /** 애플리케이션 검사를 건너뛴 쓰기라도 공백뿐인 사유는 DB가 거부한다. */
    @Test
    void rejectsABlankReasonAtTheDatabase() {
        assertThatThrownBy(() -> insertOverride(item("order-manual"), "KEEP_EXCLUDED", "MANUAL_OVERLAP_SUSPECTED", "   "))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /** 반영 허용인데 결과가 STAGED가 아니면 결정과 결과가 모순이다. 새 상태 값이 섞이는 것도 막는다. */
    @Test
    void rejectsAResultingStatusThatContradictsTheDecision() {
        assertThatThrownBy(() -> insertOverride(item("order-manual"), "ALLOW_LEDGER_WRITE", "MANUAL_OVERLAP_SUSPECTED", REASON))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsAnOverrideOfAnItemThatWasNotSuspected() {
        assertThatThrownBy(() -> jdbcTemplate.update("""
                        INSERT INTO broker_order_import_item_overrides
                            (run_id, item_id, external_order_id, original_staging_status, decision,
                             resulting_staging_status, reason, created_by_member_id, created_at)
                        VALUES (?, ?, 'order-staged', 'STAGED', 'KEEP_EXCLUDED', 'STAGED', ?, ?, now())
                        """,
                run.getId(), item("order-staged").getId(), REASON, member.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void insertOverride(BrokerOrderImportItem item, String decision, String resultingStatus, String reason) {
        jdbcTemplate.update("""
                        INSERT INTO broker_order_import_item_overrides
                            (run_id, item_id, external_order_id, original_staging_status, original_skip_reason_code,
                             decision, resulting_staging_status, reason, created_by_member_id, created_at)
                        VALUES (?, ?, ?, 'MANUAL_OVERLAP_SUSPECTED', 'MANUAL_OVERLAP', ?, ?, ?, ?, now())
                        """,
                run.getId(), item.getId(), item.getExternalOrderId(), decision, resultingStatus, reason, member.getId());
    }

    private Long overrideIdOfLink(String externalOrderId) {
        return jdbcTemplate.queryForObject(
                "SELECT override_id FROM broker_order_ledger_links WHERE external_order_id = ?",
                Long.class, externalOrderId);
    }

    /** 앰비언트 트랜잭션이 없으므로 실행 컬렉션을 지연 로딩하지 않고 저장소에서 직접 읽는다. */
    private BrokerOrderImportItem item(String externalOrderId) {
        return brokerOrderImportItemRepository.findAll().stream()
                .filter(item -> item.getExternalOrderId().equals(externalOrderId))
                .findFirst()
                .orElseThrow();
    }

    private BrokerOrderStagedOrder order(String orderId, BrokerOrderStagingStatus status, BrokerOrderSkipReason reason) {
        return new BrokerOrderStagedOrder(
                new BrokerOrderRecord(
                        orderId, Market.US, "AAPL", BrokerOrderSide.BUY, BrokerOrderLifecycle.TERMINAL_WITH_FILL,
                        "FILLED", "LIMIT", "DAY", "USD",
                        new BigDecimal("10"), new BigDecimal("10"), new BigDecimal("100.0000"),
                        new BigDecimal("1000.0000"), new BigDecimal("1.0000"), BigDecimal.ZERO,
                        FILLED_AT.minusSeconds(600), FILLED_AT, LocalDate.of(2026, 9, 3)),
                "Apple Inc.", status, reason, "fingerprint-" + orderId, false, false, false);
    }

    private BrokerConnection verifiedConnection() {
        BrokerConnection newConnection =
                new BrokerConnection(member, BrokerProvider.TOSS_SECURITIES, "개인 토스증권 " + System.nanoTime());
        newConnection.replaceSecretValues(List.of(
                secretValue("clientId", "dummy-client-id"),
                secretValue("clientSecret", "dummy-client-secret")
        ));

        EncryptedBrokerCredential encryptedSequence = brokerCredentialCipher.encrypt("dummy-account-sequence");
        newConnection.reconcileVerifiedAccounts(List.of(new BrokerAccount(
                encryptedSequence.ciphertext(),
                encryptedSequence.initializationVector(),
                "*****1234",
                "위탁",
                encryptedSequence.keyVersion()
        )));
        newConnection.markConnected("*****1234");
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
}
