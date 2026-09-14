package com.tradeguide.migration;

import com.tradeguide.domain.broker.BrokerAccount;
import com.tradeguide.domain.broker.BrokerConnection;
import com.tradeguide.domain.broker.BrokerConnectionSecretValue;
import com.tradeguide.domain.broker.BrokerOrderImportApprovalAssessment;
import com.tradeguide.domain.broker.BrokerOrderImportApprovalBlocker;
import com.tradeguide.domain.broker.BrokerOrderImportCounts;
import com.tradeguide.domain.broker.BrokerOrderImportItem;
import com.tradeguide.domain.broker.BrokerOrderImportRun;
import com.tradeguide.domain.broker.BrokerOrderLedgerLink;
import com.tradeguide.domain.broker.BrokerOrderLifecycle;
import com.tradeguide.domain.broker.BrokerOrderRecord;
import com.tradeguide.domain.broker.BrokerOrderReconciliationStatus;
import com.tradeguide.domain.broker.BrokerOrderSide;
import com.tradeguide.domain.broker.BrokerOrderSkipReason;
import com.tradeguide.domain.broker.BrokerOrderStagedOrder;
import com.tradeguide.domain.broker.BrokerOrderStagingStatus;
import com.tradeguide.domain.broker.BrokerProvider;
import com.tradeguide.domain.member.Member;
import com.tradeguide.domain.portfolio.Portfolio;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.repository.broker.BrokerConnectionRepository;
import com.tradeguide.repository.broker.BrokerOrderImportItemRepository;
import com.tradeguide.repository.broker.BrokerOrderImportRunRepository;
import com.tradeguide.repository.broker.BrokerOrderLedgerLinkRepository;
import com.tradeguide.repository.member.MemberRepository;
import com.tradeguide.repository.portfolio.PortfolioRepository;
import com.tradeguide.service.broker.BrokerCredentialCipher;
import com.tradeguide.service.broker.BrokerOrderImportService;
import com.tradeguide.service.broker.EncryptedBrokerCredential;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 승인 판정 집계를 실제 PostgreSQL에서 검증한다.
 *
 * <p>이 집계는 두 개의 새 JPQL count 질의로 계산한다. 목이나 H2로는 확인할 수 없는 것이
 * 세 가지다.
 *
 * <ol>
 *   <li>기준 시각 파라미터가 {@code null}일 때 {@code :baseline IS NULL OR ...} 가 PostgreSQL에서
 *       실행되는가. PostgreSQL은 타입 없는 파라미터를 거부하므로 이 형태는 실제 DB에서만
 *       참·거짓이 갈린다.</li>
 *   <li>{@code EXISTS} 상관 서브질의가 원장 연계 여부를 계좌 기준으로 맞게 세는가.</li>
 *   <li>경계가 {@code >}인가. 기준 시각에 <b>정확히</b> 체결된 주문이 반영 대상에 들어가면
 *       개시 잔고와 이중 계상된다. 한 칸 어긋난 부등호는 목 테스트를 그대로 통과한다.</li>
 * </ol>
 *
 * <p>이 테스트는 어떤 증권사 API도 호출하지 않고 매매 원장도 바꾸지 않는다. 자격 증명은
 * 0으로 채운 더미 키로 암호화한 고정 더미 문자열이며 실제 키·자격 증명은 쓰지 않는다.
 * Docker가 필요하며 기본 {@code test} 태스크에서 제외된다.
 */
@Tag("postgres")
@Testcontainers
@ActiveProfiles("test")
@SpringBootTest
class PostgresBrokerOrderImportApprovalAssessmentIntegrationTest {

    /** 활성 개시 잔고 승인 시각으로 쓸 값이다. 이 시각 <b>이후</b> 체결만 반영 대상이다. */
    private static final Instant BASELINE = Instant.parse("2026-09-03T00:00:00Z");

    private static final LocalDate ORDERED_FROM = LocalDate.of(2026, 9, 1);
    private static final LocalDate ORDERED_TO = LocalDate.of(2026, 9, 5);

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
    private BrokerOrderLedgerLinkRepository brokerOrderLedgerLinkRepository;

    @Autowired
    private BrokerCredentialCipher brokerCredentialCipher;

    @Autowired
    private BrokerOrderImportService brokerOrderImportService;

    private Member member;
    private Portfolio portfolio;
    private BrokerConnection connection;
    private BrokerAccount account;
    private BrokerOrderImportRun run;

    @BeforeEach
    void setUp() {
        brokerOrderLedgerLinkRepository.deleteAll();
        brokerOrderImportRunRepository.deleteAll();
        brokerConnectionRepository.deleteAll();
        portfolioRepository.deleteAll();
        memberRepository.deleteAll();

        member = memberRepository.save(new Member("order-import@example.com", "order-import-user"));
        portfolio = portfolioRepository.save(new Portfolio(member, "성장 포트폴리오"));
        connection = brokerConnectionRepository.saveAndFlush(verifiedConnection());
        account = connection.getAccounts().getFirst();
        run = brokerOrderImportRunRepository.saveAndFlush(stagedRun());
    }

    /**
     * 기준점이 없으면 아무것도 걸러지지 않는다. 이 경로가 PostgreSQL에서 실행되는지가
     * 핵심이다. {@code null} 파라미터 비교는 DB마다 다르게 굴고, 여기서 깨지면 개시 잔고를
     * 한 번도 승인하지 않은 계좌의 상세 조회가 통째로 실패한다.
     */
    @Test
    void countsEveryStagedItemWhenThereIsNoOpeningBalanceBaseline() {
        assertThat(brokerOrderImportItemRepository.countEligibleItems(run.getId(), null)).isEqualTo(3);
    }

    /**
     * 기준 시각에 정확히 체결된 주문은 반영 대상이 아니다. 그 주문은 이미 개시 잔고에 들어
     * 있으므로, 부등호가 {@code >=}로 바뀌면 같은 거래가 원장에 두 번 잡힌다.
     */
    @Test
    void excludesTheOrderFilledExactlyAtTheBaselineInstant() {
        assertThat(brokerOrderImportItemRepository.countEligibleItems(run.getId(), BASELINE))
                .isEqualTo(1);
    }

    /**
     * 이미 원장에 연계된 주문은 반영 대상에는 남되 새로 쓰지는 않는다. 두 건수를 나눠 세지
     * 않으면 화면은 "2건 반영"이라 안내하고 실제로는 1건만 들어간다.
     */
    @Test
    void separatesOrdersThatAreAlreadyLinkedToTheLedger() {
        BrokerOrderImportItem linkedItem = itemByExternalOrderId("order-after-baseline");
        linkLedger(linkedItem);

        assertThat(brokerOrderImportItemRepository.countEligibleItems(run.getId(), BASELINE))
                .isEqualTo(1);
        assertThat(brokerOrderImportItemRepository.countEligibleItemsAlreadyLinked(
                run.getId(), account.getId(), BASELINE)).isEqualTo(1);
    }

    /**
     * 승인을 취소하면 그 주문은 다시 새로 쓸 대상이 된다. 링크는 행을 지우지 않고 상태로만
     * 남으므로, 상태를 보지 않고 세면 취소한 주문이 영원히 반영되지 않는다.
     */
    @Test
    void stopsCountingALinkOnceItsApprovalIsRevoked() {
        BrokerOrderImportItem linkedItem = itemByExternalOrderId("order-after-baseline");
        BrokerOrderLedgerLink link = linkLedger(linkedItem);

        link.revoke(LocalDateTime.of(2026, 9, 9, 0, 0));
        brokerOrderLedgerLinkRepository.saveAndFlush(link);

        assertThat(brokerOrderImportItemRepository.countEligibleItemsAlreadyLinked(
                run.getId(), account.getId(), BASELINE)).isZero();
    }

    /**
     * 다른 계좌의 연계는 이 계좌의 판정에 끼어들지 않는다. 계좌를 좁히지 않으면 같은 주문
     * 식별자를 쓰는 다른 계좌의 반영이 이 실행을 조용히 잠근다.
     */
    @Test
    void ignoresLedgerLinksBelongingToAnotherBrokerAccount() {
        BrokerOrderImportItem linkedItem = itemByExternalOrderId("order-after-baseline");
        linkLedger(linkedItem);

        BrokerConnection otherConnection = brokerConnectionRepository.saveAndFlush(verifiedConnection());

        assertThat(brokerOrderImportItemRepository.countEligibleItemsAlreadyLinked(
                run.getId(), otherConnection.getAccounts().getFirst().getId(), BASELINE)).isZero();
    }

    /**
     * 상세 조회가 항목 배열 없이 승인 판정을 돌려준다. 이것이 성립해야 화면이 항목을 세지
     * 않고도 승인 버튼을 열지 말지 정할 수 있다.
     */
    @Test
    void returnsTheApprovalAssessmentFromTheDetailReadWithoutAnItemList() {
        BrokerOrderImportService.RunDetail detail =
                brokerOrderImportService.getRun(member.getId(), portfolio.getId(), run.getId());

        BrokerOrderImportApprovalAssessment approval = detail.approval();
        assertThat(approval.stagedCount()).isEqualTo(3);
        // 이 포트폴리오에는 활성 개시 잔고가 없으므로 기준점이 없다.
        assertThat(approval.baselineAt()).isNull();
        assertThat(approval.eligibleCount()).isEqualTo(3);
        assertThat(approval.baselineExcludedCount()).isZero();
        assertThat(approval.writableCount()).isEqualTo(3);
        assertThat(approval.excludedCount()).isEqualTo(1);
        assertThat(approval.suspectedCount()).isZero();
        assertThat(approval.approvable()).isTrue();
        assertThat(approval.blocker()).isNull();
        assertThat(detail.reconciliationLines()).isEmpty();
    }

    /**
     * 대조가 불일치인 실행은 서버가 승인을 거부한다. 판정도 같은 답을 내야 화면과 서버가
     * 어긋나지 않는다.
     */
    @Test
    void reportsTheSameRefusalTheApprovalPathWouldGive() {
        BrokerOrderImportRun mismatchedRun = brokerOrderImportRunRepository.saveAndFlush(
                stagedRun(BrokerOrderReconciliationStatus.MISMATCHED));

        BrokerOrderImportApprovalAssessment approval = brokerOrderImportService
                .getRun(member.getId(), portfolio.getId(), mismatchedRun.getId())
                .approval();

        assertThat(approval.approvable()).isFalse();
        assertThat(approval.blocker())
                .isEqualTo(BrokerOrderImportApprovalBlocker.RECONCILIATION_MISMATCHED);
    }

    /**
     * 실패한 실행도 상세로 읽을 수 있다. 그 실행에는 항목도 대조 결과도 없으므로, 집계를
     * 계산하는 경로가 빈 건수와 없는 기준점을 그대로 견뎌야 한다. 여기서 깨지면 사용자는
     * 조회가 왜 실패했는지 확인하러 들어갔다가 화면 자체가 열리지 않는 것을 본다.
     */
    @Test
    void readsAFailedRunWithoutTryingToCountItemsItNeverStaged() {
        BrokerOrderImportRun failedRun = brokerOrderImportRunRepository.saveAndFlush(
                BrokerOrderImportRun.failed(
                        portfolio,
                        connection,
                        account,
                        member,
                        ORDERED_FROM,
                        ORDERED_TO,
                        ORDERED_FROM.minusDays(2),
                        ORDERED_TO.plusDays(2),
                        LocalDateTime.of(2026, 9, 8, 9, 0),
                        LocalDateTime.of(2026, 9, 8, 9, 0, 1),
                        "PROVIDER_UNAVAILABLE",
                        null));

        BrokerOrderImportApprovalAssessment approval = brokerOrderImportService
                .getRun(member.getId(), portfolio.getId(), failedRun.getId())
                .approval();

        assertThat(approval.stagedCount()).isZero();
        assertThat(approval.eligibleCount()).isZero();
        assertThat(approval.approvable()).isFalse();
        assertThat(approval.blocker()).isEqualTo(BrokerOrderImportApprovalBlocker.RUN_NOT_STAGED);
    }

    /**
     * V20이 추가한 세 컬럼이 실제 PostgreSQL 스키마와 엔티티 매핑에서 어긋나지 않는지,
     * 그리고 이 컬럼이 생기기 전의 실행은 세 값 모두 NULL로 해석되는지 확인한다.
     */
    @Test
    void leavesTheNewCoverageColumnsNullForARunStagedBeforeThisSlice() {
        BrokerOrderImportRun found = brokerOrderImportRunRepository.findById(run.getId()).orElseThrow();

        assertThat(found.getCoveredOrderedTo()).isNull();
        assertThat(found.isFullyCovered()).isTrue();
        assertThat(found.getCoverageAcknowledgedAt()).isNull();
        assertThat(found.getCoverageAcknowledgedByMemberId()).isNull();
    }

    private BrokerOrderLedgerLink linkLedger(BrokerOrderImportItem item) {
        // 매매 원장은 이 테스트에서 만들지 않는다. 링크가 세어지는지만 보면 되므로 식별자만 둔다.
        return brokerOrderLedgerLinkRepository.saveAndFlush(new BrokerOrderLedgerLink(
                account, run, item, 9001L, member, LocalDateTime.of(2026, 9, 8, 10, 0)));
    }

    /**
     * 항목은 실행에서 지연 로딩으로 꺼내지 않고 저장소에서 직접 읽는다. 이 테스트에는
     * 앰비언트 트랜잭션이 없어 실행의 컬렉션을 건드리면 지연 로딩이 터진다.
     */
    private BrokerOrderImportItem itemByExternalOrderId(String externalOrderId) {
        return brokerOrderImportItemRepository.findAll().stream()
                .filter(item -> item.getExternalOrderId().equals(externalOrderId))
                .findFirst()
                .orElseThrow();
    }

    private BrokerOrderImportRun stagedRun() {
        return stagedRun(BrokerOrderReconciliationStatus.MATCHED);
    }

    /**
     * 반영 후보 3건과 정상 제외 1건을 담은 실행이다. 후보 셋은 기준 시각을 사이에 두고
     * 이전·정각·이후로 하나씩 놓아 경계 규칙을 눈으로 확인할 수 있게 한다.
     */
    private BrokerOrderImportRun stagedRun(BrokerOrderReconciliationStatus reconciliationStatus) {
        return BrokerOrderImportRun.staged(
                portfolio,
                connection,
                account,
                member,
                ORDERED_FROM,
                ORDERED_TO,
                ORDERED_FROM.minusDays(2),
                ORDERED_TO.plusDays(2),
                LocalDateTime.of(2026, 9, 8, 9, 0),
                LocalDateTime.of(2026, 9, 8, 9, 0, 3),
                new BrokerOrderImportCounts(
                        4, 3, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
                reconciliationStatus,
                null,
                List.of(
                        stagedOrder("order-before-baseline", BASELINE.minusSeconds(1)),
                        stagedOrder("order-at-baseline", BASELINE),
                        stagedOrder("order-after-baseline", BASELINE.plusSeconds(1)),
                        notFilledOrder("order-not-filled")
                ),
                List.of(),
                null
        );
    }

    private BrokerOrderStagedOrder stagedOrder(String externalOrderId, Instant filledAt) {
        return new BrokerOrderStagedOrder(
                new BrokerOrderRecord(
                        externalOrderId,
                        Market.US,
                        "AAPL",
                        BrokerOrderSide.BUY,
                        BrokerOrderLifecycle.TERMINAL_WITH_FILL,
                        "FILLED",
                        "LIMIT",
                        "DAY",
                        "USD",
                        new BigDecimal("10"),
                        new BigDecimal("10"),
                        new BigDecimal("100.0000"),
                        new BigDecimal("1000.0000"),
                        new BigDecimal("1.0000"),
                        BigDecimal.ZERO,
                        filledAt.minusSeconds(600),
                        filledAt,
                        LocalDate.of(2026, 9, 7)
                ),
                "Apple Inc.",
                BrokerOrderStagingStatus.STAGED,
                null,
                "fingerprint-" + externalOrderId,
                false,
                false,
                false
        );
    }

    private BrokerOrderStagedOrder notFilledOrder(String externalOrderId) {
        return new BrokerOrderStagedOrder(
                new BrokerOrderRecord(
                        externalOrderId,
                        Market.US,
                        "AAPL",
                        BrokerOrderSide.BUY,
                        BrokerOrderLifecycle.TERMINAL_WITHOUT_FILL,
                        "CANCELLED",
                        "LIMIT",
                        "DAY",
                        "USD",
                        new BigDecimal("10"),
                        BigDecimal.ZERO,
                        null,
                        null,
                        null,
                        null,
                        BASELINE.plusSeconds(60),
                        null,
                        null
                ),
                "Apple Inc.",
                BrokerOrderStagingStatus.SKIPPED_NOT_FILLED,
                BrokerOrderSkipReason.NOT_FILLED,
                "fingerprint-" + externalOrderId,
                false,
                false,
                false
        );
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
