package com.tradeguide.repository.broker;

import com.tradeguide.domain.broker.BrokerAccount;
import com.tradeguide.domain.broker.BrokerConnection;
import com.tradeguide.domain.broker.BrokerConnectionSecretValue;
import com.tradeguide.domain.broker.BrokerOrderImportCounts;
import com.tradeguide.domain.broker.BrokerOrderImportItem;
import com.tradeguide.domain.broker.BrokerOrderImportItemOverride;
import com.tradeguide.domain.broker.BrokerOrderImportRun;
import com.tradeguide.domain.broker.BrokerOrderLedgerLink;
import com.tradeguide.domain.broker.BrokerOrderLedgerLinkStatus;
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
import com.tradeguide.dto.ApiErrorCode;
import com.tradeguide.exception.BrokerOrderApprovalConflictException;
import com.tradeguide.exception.BrokerOrderImportNotFoundException;
import com.tradeguide.exception.BrokerOrderOverrideConflictException;
import com.tradeguide.exception.PortfolioNotFoundException;
import com.tradeguide.repository.member.MemberRepository;
import com.tradeguide.repository.portfolio.PortfolioRepository;
import com.tradeguide.repository.trade.TradeTransactionRepository;
import com.tradeguide.service.asset.AssetListingService;
import com.tradeguide.service.broker.BrokerOrderImportApprovalService;
import com.tradeguide.service.broker.BrokerOrderImportApprovalWriter;
import com.tradeguide.service.broker.BrokerOrderImportItemOverrideService;
import com.tradeguide.service.broker.BrokerOrderImportItemOverrideWriter;
import com.tradeguide.service.holding.HoldingCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * 의심 항목 재판정부터 승인·취소까지를 실제 JPA 매핑과 저장소 질의로 확인한다.
 *
 * <p>목으로는 확인할 수 없는 것이 세 가지다. 항목당 재판정 한 건이라는 유니크 제약이 실제로
 * 걸리는지, 유효 상태를 세는 {@code EXISTS} 질의가 승인 경로와 같은 답을 내는지, 승인 취소 뒤에도
 * 재판정 행과 원장 링크의 재판정 참조가 감사 이력으로 남는지다.
 *
 * <p>증권사 API는 호출하지 않는다. 계좌 식별값·자격 증명은 고정 더미 문자열이다.
 */
@DataJpaTest
class BrokerOrderImportItemOverrideJpaTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-09-08T10:00:00Z"), ZoneOffset.UTC);
    private static final Instant FILLED_AT = Instant.parse("2026-09-01T13:30:00Z");
    private static final String REASON = "증권사 화면에서 수기 기록과 별개 체결로 확인";

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
    private PortfolioBrokerHoldingImportRepository portfolioBrokerHoldingImportRepository;

    @Autowired
    private TradeTransactionRepository tradeTransactionRepository;

    @Autowired
    private TestEntityManager entityManager;

    private BrokerOrderImportApprovalService approvalService;
    private BrokerOrderImportItemOverrideService overrideService;

    private Member member;
    private Portfolio portfolio;
    private BrokerConnection connection;
    private BrokerAccount account;

    @BeforeEach
    void setUp() {
        member = memberRepository.save(new Member("override@example.com", "override-user"));
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

        HoldingCalculator holdingCalculator = new HoldingCalculator();
        BrokerOrderImportApprovalWriter approvalWriter = new BrokerOrderImportApprovalWriter(
                portfolioRepository,
                brokerOrderImportRunRepository,
                brokerOrderLedgerLinkRepository,
                brokerOrderImportItemOverrideRepository,
                portfolioBrokerHoldingImportRepository,
                tradeTransactionRepository,
                // 자산 카탈로그 등록은 이 테스트의 관심사가 아니다. 반환값을 쓰지 않는 부수 효과라 목으로 둔다.
                mock(AssetListingService.class),
                holdingCalculator,
                FIXED_CLOCK
        );
        approvalService = new BrokerOrderImportApprovalService(
                portfolioRepository,
                brokerOrderImportRunRepository,
                brokerOrderLedgerLinkRepository,
                tradeTransactionRepository,
                approvalWriter,
                holdingCalculator,
                FIXED_CLOCK
        );
        overrideService = new BrokerOrderImportItemOverrideService(
                portfolioRepository,
                brokerOrderImportRunRepository,
                brokerOrderImportItemOverrideRepository,
                new BrokerOrderImportItemOverrideWriter(
                        portfolioRepository,
                        brokerOrderImportRunRepository,
                        brokerOrderImportItemRepository,
                        brokerOrderImportItemOverrideRepository,
                        FIXED_CLOCK
                )
        );
    }

    /** 재판정 전에는 의심 항목이 원장에 들어가지 않는다. 같은 실행의 반영 후보만 반영된다. */
    @Test
    void leavesSuspectedItemsOutOfTheLedgerUntilTheyAreOverridden() {
        BrokerOrderImportRun run = saveRun(
                staged("order-staged"),
                suspected("order-manual", BrokerOrderStagingStatus.MANUAL_OVERLAP_SUSPECTED, BrokerOrderSide.BUY),
                suspected("order-duplicate", BrokerOrderStagingStatus.DUPLICATE_SUSPECTED, BrokerOrderSide.BUY));

        assertThat(brokerOrderImportItemRepository.countEligibleItems(run.getId(), null)).isEqualTo(1);

        BrokerOrderImportApprovalWriter.ApprovalResult result =
                approvalService.approve(member.getId(), portfolio.getId(), run.getId(), false);

        assertThat(result.writtenCount()).isEqualTo(1);
        assertThat(result.overrideAllowedCount()).isZero();
        assertThat(tradeTransactionRepository.findAllByPortfolio_IdOrderByTradedAtAsc(portfolio.getId())).hasSize(1);
        assertThat(brokerOrderLedgerLinkRepository.findAllByRun_IdAndStatus(run.getId(), BrokerOrderLedgerLinkStatus.ACTIVE))
                .extracting(BrokerOrderLedgerLink::getExternalOrderId)
                .containsExactly("order-staged");
    }

    /**
     * 반영 허용 항목만 반영 후보에 들고, 제외 유지 항목은 빠진다. 판정 집계의 {@code EXISTS} 질의와
     * 승인 경로가 같은 답을 내야 화면이 안내한 건수와 실제 반영 건수가 같다. 스테이징 원본은 그대로다.
     */
    @Test
    void approvesAnItemAllowedByOverrideAndKeepsTheExcludedOneOut() {
        BrokerOrderImportRun run = saveRun(
                staged("order-staged"),
                suspected("order-manual", BrokerOrderStagingStatus.MANUAL_OVERLAP_SUSPECTED, BrokerOrderSide.BUY),
                suspected("order-duplicate", BrokerOrderStagingStatus.DUPLICATE_SUSPECTED, BrokerOrderSide.BUY));
        Long manualItemId = itemId(run, "order-manual");
        Long duplicateItemId = itemId(run, "order-duplicate");

        BrokerOrderImportItemOverride allow = overrideService.createOverride(member.getId(), portfolio.getId(),
                run.getId(), manualItemId, BrokerOrderOverrideDecision.ALLOW_LEDGER_WRITE, REASON).override();
        overrideService.createOverride(member.getId(), portfolio.getId(),
                run.getId(), duplicateItemId, BrokerOrderOverrideDecision.KEEP_EXCLUDED, "같은 주문으로 판단");

        assertThat(brokerOrderImportItemRepository.countEligibleItems(run.getId(), null)).isEqualTo(2);
        assertThat(brokerOrderImportItemRepository.countOverridesByDecision(
                run.getId(), BrokerOrderOverrideDecision.ALLOW_LEDGER_WRITE)).isEqualTo(1);
        assertThat(brokerOrderImportItemRepository.countOverridesByDecision(
                run.getId(), BrokerOrderOverrideDecision.KEEP_EXCLUDED)).isEqualTo(1);

        BrokerOrderImportApprovalWriter.ApprovalResult result =
                approvalService.approve(member.getId(), portfolio.getId(), run.getId(), false);

        assertThat(result.eligibleCount()).isEqualTo(2);
        assertThat(result.writtenCount()).isEqualTo(2);
        assertThat(result.overrideAllowedCount()).isEqualTo(1);

        List<BrokerOrderLedgerLink> links = brokerOrderLedgerLinkRepository
                .findAllByRun_IdAndStatus(run.getId(), BrokerOrderLedgerLinkStatus.ACTIVE);
        assertThat(links).extracting(BrokerOrderLedgerLink::getExternalOrderId)
                .containsExactlyInAnyOrder("order-staged", "order-manual");
        assertThat(links).filteredOn(link -> link.getExternalOrderId().equals("order-manual"))
                .singleElement()
                .satisfies(link -> assertThat(link.getOverride().getId()).isEqualTo(allow.getId()));
        assertThat(links).filteredOn(link -> link.getExternalOrderId().equals("order-staged"))
                .singleElement()
                .satisfies(link -> assertThat(link.getOverride()).isNull());

        entityManager.flush();
        entityManager.clear();
        assertThat(brokerOrderImportItemRepository.findById(manualItemId).orElseThrow().getStagingStatus())
                .isEqualTo(BrokerOrderStagingStatus.MANUAL_OVERLAP_SUSPECTED);
    }

    /** 반영 허용 항목도 전체 재생 검증을 지난다. 초과 매도가 생기면 한 행도 쓰지 않는다. */
    @Test
    void writesNothingWhenTheFullReplayFailsForAnAllowedItem() {
        BrokerOrderImportRun run = saveRun(
                suspected("order-sell", BrokerOrderStagingStatus.MANUAL_OVERLAP_SUSPECTED, BrokerOrderSide.SELL));
        overrideService.createOverride(member.getId(), portfolio.getId(), run.getId(),
                itemId(run, "order-sell"), BrokerOrderOverrideDecision.ALLOW_LEDGER_WRITE, REASON);

        assertThatThrownBy(() -> approvalService.approve(member.getId(), portfolio.getId(), run.getId(), false))
                .isInstanceOf(BrokerOrderApprovalConflictException.class)
                .extracting(exception -> ((BrokerOrderApprovalConflictException) exception).getCode())
                .isEqualTo(ApiErrorCode.REPLAY_VALIDATION_FAILED);

        assertThat(tradeTransactionRepository.findAllByPortfolio_IdOrderByTradedAtAsc(portfolio.getId())).isEmpty();
        assertThat(brokerOrderLedgerLinkRepository.findAllByRun_IdAndStatus(run.getId(), BrokerOrderLedgerLinkStatus.ACTIVE))
                .isEmpty();
    }

    /**
     * 승인을 취소하면 원장 행은 지워지지만 재판정 행과, 링크가 가리키던 재판정 참조는 그대로 남는다.
     * "무엇을 근거로 넣었다가 뺐는지"가 감사 이력이다.
     */
    @Test
    void preservesTheOverrideAndItsLedgerLinkReferenceAfterRevoke() {
        BrokerOrderImportRun run = saveRun(
                suspected("order-manual", BrokerOrderStagingStatus.MANUAL_OVERLAP_SUSPECTED, BrokerOrderSide.BUY));
        Long itemId = itemId(run, "order-manual");
        BrokerOrderImportItemOverride allow = overrideService.createOverride(member.getId(), portfolio.getId(),
                run.getId(), itemId, BrokerOrderOverrideDecision.ALLOW_LEDGER_WRITE, REASON).override();
        approvalService.approve(member.getId(), portfolio.getId(), run.getId(), false);

        approvalService.revoke(member.getId(), portfolio.getId(), run.getId());
        entityManager.flush();
        entityManager.clear();

        assertThat(tradeTransactionRepository.findAllByPortfolio_IdOrderByTradedAtAsc(portfolio.getId())).isEmpty();

        BrokerOrderImportItemOverride preserved =
                brokerOrderImportItemOverrideRepository.findByItem_Id(itemId).orElseThrow();
        assertThat(preserved.getId()).isEqualTo(allow.getId());
        assertThat(preserved.getDecision()).isEqualTo(BrokerOrderOverrideDecision.ALLOW_LEDGER_WRITE);
        assertThat(preserved.getReason()).isEqualTo(REASON);
        assertThat(preserved.getCreatedByMemberId()).isEqualTo(member.getId());
        assertThat(preserved.getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 9, 8, 10, 0));

        List<BrokerOrderLedgerLink> revokedLinks = brokerOrderLedgerLinkRepository
                .findAllByRun_IdAndStatus(run.getId(), BrokerOrderLedgerLinkStatus.REVOKED);
        assertThat(revokedLinks).singleElement().satisfies(link -> {
            assertThat(link.getOverride().getId()).isEqualTo(allow.getId());
            assertThat(link.getTradeTransactionId()).isNotNull();
            assertThat(link.getRevokedAt()).isNotNull();
        });
    }

    /** 애플리케이션 검사를 건너뛰어도 같은 항목의 두 번째 재판정 행은 DB가 막는다. */
    @Test
    void rejectsASecondOverrideRowForTheSameItemAtTheDatabase() {
        BrokerOrderImportRun run = saveRun(
                suspected("order-manual", BrokerOrderStagingStatus.MANUAL_OVERLAP_SUSPECTED, BrokerOrderSide.BUY));
        BrokerOrderImportItem item = run.getItems().getFirst();
        LocalDateTime now = LocalDateTime.of(2026, 9, 8, 10, 0);

        brokerOrderImportItemOverrideRepository.saveAndFlush(new BrokerOrderImportItemOverride(
                item, BrokerOrderOverrideDecision.ALLOW_LEDGER_WRITE, REASON, member, now));

        assertThatThrownBy(() -> brokerOrderImportItemOverrideRepository.saveAndFlush(new BrokerOrderImportItemOverride(
                item, BrokerOrderOverrideDecision.KEEP_EXCLUDED, REASON, member, now)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void repeatsTheSameDecisionIdempotentlyAndRejectsADifferentOne() {
        BrokerOrderImportRun run = saveRun(
                suspected("order-duplicate", BrokerOrderStagingStatus.DUPLICATE_SUSPECTED, BrokerOrderSide.BUY));
        Long itemId = itemId(run, "order-duplicate");

        BrokerOrderImportItemOverrideWriter.OverrideResult first = overrideService.createOverride(
                member.getId(), portfolio.getId(), run.getId(), itemId, BrokerOrderOverrideDecision.KEEP_EXCLUDED, REASON);
        BrokerOrderImportItemOverrideWriter.OverrideResult repeated = overrideService.createOverride(
                member.getId(), portfolio.getId(), run.getId(), itemId, BrokerOrderOverrideDecision.KEEP_EXCLUDED, REASON);

        assertThat(first.created()).isTrue();
        assertThat(repeated.created()).isFalse();
        assertThat(repeated.override().getId()).isEqualTo(first.override().getId());
        assertThatThrownBy(() -> overrideService.createOverride(
                member.getId(), portfolio.getId(), run.getId(), itemId, BrokerOrderOverrideDecision.ALLOW_LEDGER_WRITE, REASON))
                .isInstanceOf(BrokerOrderOverrideConflictException.class)
                .extracting(exception -> ((BrokerOrderOverrideConflictException) exception).getCode())
                .isEqualTo(ApiErrorCode.ORDER_IMPORT_OVERRIDE_CONFLICT);
        assertThat(brokerOrderImportItemOverrideRepository.count()).isEqualTo(1);
    }

    /** 다른 회원의 포트폴리오, 다른 실행의 항목으로는 재판정할 수 없고 아무 행도 남지 않는다. */
    @Test
    void rejectsOverridesOutsideTheOwnedPortfolioAndRun() {
        BrokerOrderImportRun run = saveRun(
                suspected("order-manual", BrokerOrderStagingStatus.MANUAL_OVERLAP_SUSPECTED, BrokerOrderSide.BUY));
        BrokerOrderImportRun otherRun = saveRun(
                suspected("order-other", BrokerOrderStagingStatus.MANUAL_OVERLAP_SUSPECTED, BrokerOrderSide.BUY));
        Member otherMember = memberRepository.save(new Member("other@example.com", "other-user"));

        assertThatThrownBy(() -> overrideService.createOverride(otherMember.getId(), portfolio.getId(), run.getId(),
                itemId(run, "order-manual"), BrokerOrderOverrideDecision.ALLOW_LEDGER_WRITE, REASON))
                .isInstanceOf(PortfolioNotFoundException.class);
        assertThatThrownBy(() -> overrideService.createOverride(member.getId(), portfolio.getId(), run.getId(),
                itemId(otherRun, "order-other"), BrokerOrderOverrideDecision.ALLOW_LEDGER_WRITE, REASON))
                .isInstanceOf(BrokerOrderImportNotFoundException.class);
        assertThat(brokerOrderImportItemOverrideRepository.count()).isZero();
    }

    private BrokerOrderImportRun saveRun(BrokerOrderStagedOrder... orders) {
        List<BrokerOrderStagedOrder> items = List.of(orders);
        int staged = countStatus(items, BrokerOrderStagingStatus.STAGED);
        int manual = countStatus(items, BrokerOrderStagingStatus.MANUAL_OVERLAP_SUSPECTED);
        int duplicate = countStatus(items, BrokerOrderStagingStatus.DUPLICATE_SUSPECTED);

        return brokerOrderImportRunRepository.saveAndFlush(BrokerOrderImportRun.staged(
                portfolio, connection, account, member,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 5),
                LocalDate.of(2026, 8, 30), LocalDate.of(2026, 9, 7),
                LocalDateTime.of(2026, 9, 8, 9, 0), LocalDateTime.of(2026, 9, 8, 9, 0, 3),
                new BrokerOrderImportCounts(
                        items.size(), staged, 0, 0, 0, 0, 0, 0, 0, 0, 0, manual, duplicate, 0, 0, 0, 0, 0, 0),
                BrokerOrderReconciliationStatus.MATCHED,
                LocalDateTime.of(2026, 9, 7, 9, 0),
                items,
                List.of(),
                null
        ));
    }

    private int countStatus(List<BrokerOrderStagedOrder> items, BrokerOrderStagingStatus status) {
        return (int) items.stream().filter(item -> item.stagingStatus() == status).count();
    }

    private Long itemId(BrokerOrderImportRun run, String externalOrderId) {
        return run.getItems().stream()
                .filter(item -> item.getExternalOrderId().equals(externalOrderId))
                .findFirst()
                .orElseThrow()
                .getId();
    }

    private BrokerOrderStagedOrder staged(String orderId) {
        return new BrokerOrderStagedOrder(
                record(orderId, BrokerOrderSide.BUY, FILLED_AT), "애플", BrokerOrderStagingStatus.STAGED, null,
                "fingerprint-" + orderId, false, false, false);
    }

    private BrokerOrderStagedOrder suspected(String orderId, BrokerOrderStagingStatus status, BrokerOrderSide side) {
        BrokerOrderSkipReason reason = status == BrokerOrderStagingStatus.MANUAL_OVERLAP_SUSPECTED
                ? BrokerOrderSkipReason.MANUAL_OVERLAP
                : BrokerOrderSkipReason.DUPLICATE_FINGERPRINT;
        return new BrokerOrderStagedOrder(
                record(orderId, side, FILLED_AT.plusSeconds(60)), "애플", status, reason,
                "fingerprint-" + orderId, false, false, false);
    }

    private BrokerOrderRecord record(String orderId, BrokerOrderSide side, Instant filledAt) {
        return new BrokerOrderRecord(
                orderId, Market.US, "AAPL", side, BrokerOrderLifecycle.TERMINAL_WITH_FILL,
                "FILLED", "LIMIT", "DAY", "USD",
                new BigDecimal("10"), new BigDecimal("10"), new BigDecimal("100.25"), new BigDecimal("1002.50"),
                new BigDecimal("1.00"), new BigDecimal("0.00"),
                filledAt.minusSeconds(3600), filledAt, LocalDate.of(2026, 9, 3));
    }
}
