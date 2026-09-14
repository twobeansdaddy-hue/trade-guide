package com.tradeguide.service.broker;

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
import com.tradeguide.domain.broker.PortfolioBrokerHoldingImport;
import com.tradeguide.domain.broker.PortfolioBrokerHoldingImportStatus;
import com.tradeguide.domain.member.Member;
import com.tradeguide.domain.portfolio.Portfolio;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.domain.trade.TradeTransaction;
import com.tradeguide.domain.trade.TradeTransactionSource;
import com.tradeguide.domain.trade.TradeType;
import com.tradeguide.dto.ApiErrorCode;
import com.tradeguide.exception.BrokerOrderApprovalConflictException;
import com.tradeguide.exception.BrokerOrderImportNotFoundException;
import com.tradeguide.exception.PortfolioNotFoundException;
import com.tradeguide.repository.broker.BrokerOrderImportItemOverrideRepository;
import com.tradeguide.repository.broker.BrokerOrderImportRunRepository;
import com.tradeguide.repository.broker.BrokerOrderLedgerLinkRepository;
import com.tradeguide.repository.broker.PortfolioBrokerHoldingImportRepository;
import com.tradeguide.repository.portfolio.PortfolioRepository;
import com.tradeguide.repository.trade.TradeTransactionRepository;
import com.tradeguide.service.asset.AssetListingService;
import com.tradeguide.service.holding.HoldingCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BrokerOrderImportApprovalWriterTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-09-08T10:00:00Z"), ZoneOffset.UTC);

    @Mock
    private PortfolioRepository portfolioRepository;

    @Mock
    private BrokerOrderImportRunRepository brokerOrderImportRunRepository;

    @Mock
    private BrokerOrderLedgerLinkRepository brokerOrderLedgerLinkRepository;

    @Mock
    private BrokerOrderImportItemOverrideRepository brokerOrderImportItemOverrideRepository;

    @Mock
    private PortfolioBrokerHoldingImportRepository portfolioBrokerHoldingImportRepository;

    @Mock
    private TradeTransactionRepository tradeTransactionRepository;

    @Mock
    private AssetListingService assetListingService;

    private BrokerOrderImportApprovalWriter writer;

    private Member member;
    private Portfolio portfolio;
    private BrokerConnection connection;
    private BrokerAccount account;

    @BeforeEach
    void setUp() {
        writer = new BrokerOrderImportApprovalWriter(
                portfolioRepository,
                brokerOrderImportRunRepository,
                brokerOrderLedgerLinkRepository,
                brokerOrderImportItemOverrideRepository,
                portfolioBrokerHoldingImportRepository,
                tradeTransactionRepository,
                assetListingService,
                new HoldingCalculator(),
                FIXED_CLOCK
        );

        member = new Member("broker@example.com", "broker-user");
        ReflectionTestUtils.setField(member, "id", 10L);
        portfolio = new Portfolio(member, "성장 포트폴리오");
        ReflectionTestUtils.setField(portfolio, "id", 20L);

        connection = new BrokerConnection(member, BrokerProvider.TOSS_SECURITIES, "개인 토스증권");
        connection.replaceSecretValues(List.of(
                new BrokerConnectionSecretValue("clientId", "encrypted-client-id", "client-id-iv", 1),
                new BrokerConnectionSecretValue("clientSecret", "encrypted-client-secret", "client-secret-iv", 1)
        ));
        connection.reconcileVerifiedAccounts(List.of(
                new BrokerAccount("encrypted-sequence", "sequence-iv", "*****1234", "위탁", 1)
        ));
        connection.markConnected("*****1234");
        ReflectionTestUtils.setField(connection, "id", 1L);
        account = connection.getAccounts().getFirst();
        ReflectionTestUtils.setField(account, "id", 2L);

        lenient().when(portfolioRepository.findByMember_IdAndId(10L, 20L)).thenReturn(Optional.of(portfolio));
        lenient().when(portfolioBrokerHoldingImportRepository
                        .findFirstByPortfolio_IdAndStatusOrderByApprovedAtDesc(20L, PortfolioBrokerHoldingImportStatus.ACTIVE))
                .thenReturn(Optional.empty());
        lenient().when(tradeTransactionRepository.findAllByPortfolio_IdOrderByTradedAtAsc(20L))
                .thenReturn(List.of());
        lenient().when(brokerOrderLedgerLinkRepository.findAllByBrokerAccount_IdAndStatus(2L, BrokerOrderLedgerLinkStatus.ACTIVE))
                .thenReturn(List.of());
        lenient().when(tradeTransactionRepository.save(any(TradeTransaction.class)))
                .thenAnswer(invocation -> {
                    TradeTransaction transaction = invocation.getArgument(0);
                    ReflectionTestUtils.setField(transaction, "id", 900L);
                    return transaction;
                });
        lenient().when(brokerOrderLedgerLinkRepository.saveAndFlush(any(BrokerOrderLedgerLink.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void approvesEligibleOrdersAndCreatesLedgerRowsUsingProviderExecutionTimestamps() {
        Instant filledAt = Instant.parse("2026-09-01T13:30:00Z");
        BrokerOrderImportRun run = stagedRun(BrokerOrderReconciliationStatus.MATCHED,
                List.of(stagedOrder("order-1", filledAt)));
        stubRun(run);

        BrokerOrderImportApprovalWriter.ApprovalResult result = writer.approve(10L, 20L, 5L, false);

        assertThat(result.eligibleCount()).isEqualTo(1);
        assertThat(result.baselineExcludedCount()).isZero();
        assertThat(result.alreadyLinkedCount()).isZero();
        assertThat(result.writtenCount()).isEqualTo(1);
        assertThat(result.approvedAt()).isEqualTo(LocalDateTime.of(2026, 9, 8, 10, 0));
        assertThat(result.approvedByMemberId()).isEqualTo(10L);
        assertThat(result.baselineAt()).isNull();
        assertThat(result.overrideAllowedCount()).isZero();

        ArgumentCaptor<TradeTransaction> captor = ArgumentCaptor.forClass(TradeTransaction.class);
        verify(tradeTransactionRepository).save(captor.capture());
        TradeTransaction saved = captor.getValue();
        assertThat(saved.getSource()).isEqualTo(TradeTransactionSource.BROKER_ORDER_HISTORY);
        assertThat(saved.getTradeType()).isEqualTo(TradeType.BUY);
        assertThat(saved.getTradedAt()).isEqualTo(filledAt);
        assertThat(saved.getQuantity()).isEqualByComparingTo("10");
        assertThat(saved.getExecutedPrice()).isEqualByComparingTo("100.25");

        ArgumentCaptor<BrokerOrderLedgerLink> linkCaptor = ArgumentCaptor.forClass(BrokerOrderLedgerLink.class);
        verify(brokerOrderLedgerLinkRepository).saveAndFlush(linkCaptor.capture());
        assertThat(linkCaptor.getValue().getExternalOrderId()).isEqualTo("order-1");
        assertThat(linkCaptor.getValue().getApprovedTradedAt()).isEqualTo(filledAt);
        assertThat(linkCaptor.getValue().getTradeTransactionId()).isEqualTo(900L);
        assertThat(linkCaptor.getValue().getOverride()).isNull();
    }

    @Test
    void filtersOutOrdersAtOrBeforeTheOpeningBalanceBaselineAndKeepsThemOutOfTheLedger() {
        LocalDateTime baselineApprovedAt = LocalDateTime.of(2026, 9, 3, 0, 0);
        PortfolioBrokerHoldingImport baseline = mock(PortfolioBrokerHoldingImport.class);
        when(baseline.getApprovedAt()).thenReturn(baselineApprovedAt);
        when(portfolioBrokerHoldingImportRepository
                .findFirstByPortfolio_IdAndStatusOrderByApprovedAtDesc(20L, PortfolioBrokerHoldingImportStatus.ACTIVE))
                .thenReturn(Optional.of(baseline));

        Instant atBaseline = baselineApprovedAt.toInstant(ZoneOffset.UTC);
        Instant afterBaseline = atBaseline.plusSeconds(3600);

        BrokerOrderImportRun run = stagedRun(BrokerOrderReconciliationStatus.MATCHED, List.of(
                stagedOrder("order-historical", atBaseline),
                stagedOrder("order-new", afterBaseline)
        ));
        stubRun(run);

        BrokerOrderImportApprovalWriter.ApprovalResult result = writer.approve(10L, 20L, 5L, false);

        assertThat(result.eligibleCount()).isEqualTo(1);
        assertThat(result.baselineExcludedCount()).isEqualTo(1);
        assertThat(result.writtenCount()).isEqualTo(1);
        assertThat(result.baselineAt()).isEqualTo(baselineApprovedAt);

        ArgumentCaptor<TradeTransaction> captor = ArgumentCaptor.forClass(TradeTransaction.class);
        verify(tradeTransactionRepository).save(captor.capture());
        assertThat(captor.getValue().getTradedAt()).isEqualTo(afterBaseline);
    }

    @Test
    void treatsAlreadyLinkedOrdersAsIdempotentAndWritesOnlyTheRemainingOnes() {
        Instant filledAt = Instant.parse("2026-09-01T13:30:00Z");
        BrokerOrderImportRun run = stagedRun(BrokerOrderReconciliationStatus.MATCHED, List.of(
                stagedOrder("order-1", filledAt),
                stagedOrder("order-2", filledAt.plusSeconds(60))
        ));
        stubRun(run);

        BrokerOrderLedgerLink existingLink = mock(BrokerOrderLedgerLink.class);
        when(existingLink.getExternalOrderId()).thenReturn("order-1");
        when(brokerOrderLedgerLinkRepository.findAllByBrokerAccount_IdAndStatus(2L, BrokerOrderLedgerLinkStatus.ACTIVE))
                .thenReturn(List.of(existingLink));

        BrokerOrderImportApprovalWriter.ApprovalResult result = writer.approve(10L, 20L, 5L, false);

        assertThat(result.eligibleCount()).isEqualTo(2);
        assertThat(result.alreadyLinkedCount()).isEqualTo(1);
        assertThat(result.writtenCount()).isEqualTo(1);

        ArgumentCaptor<TradeTransaction> captor = ArgumentCaptor.forClass(TradeTransaction.class);
        verify(tradeTransactionRepository).save(captor.capture());
        assertThat(captor.getValue().getTradedAt()).isEqualTo(filledAt.plusSeconds(60));
    }

    @Test
    void rejectsWithoutWritesWhenReconciliationStatusIsMismatched() {
        BrokerOrderImportRun run = stagedRun(BrokerOrderReconciliationStatus.MISMATCHED,
                List.of(stagedOrder("order-1", Instant.parse("2026-09-01T13:30:00Z"))));
        stubRun(run);

        assertThatThrownBy(() -> writer.approve(10L, 20L, 5L, false))
                .isInstanceOf(BrokerOrderApprovalConflictException.class)
                .extracting(exception -> ((BrokerOrderApprovalConflictException) exception).getCode())
                .isEqualTo(ApiErrorCode.RECONCILIATION_MISMATCH);

        verify(tradeTransactionRepository, never()).save(any());
        verify(brokerOrderLedgerLinkRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsWithoutWritesWhenNoEligibleItemsRemainAfterBaselineFilter() {
        LocalDateTime baselineApprovedAt = LocalDateTime.of(2026, 9, 3, 0, 0);
        PortfolioBrokerHoldingImport baseline = mock(PortfolioBrokerHoldingImport.class);
        when(baseline.getApprovedAt()).thenReturn(baselineApprovedAt);
        when(portfolioBrokerHoldingImportRepository
                .findFirstByPortfolio_IdAndStatusOrderByApprovedAtDesc(20L, PortfolioBrokerHoldingImportStatus.ACTIVE))
                .thenReturn(Optional.of(baseline));

        Instant beforeBaseline = baselineApprovedAt.toInstant(ZoneOffset.UTC).minusSeconds(60);
        BrokerOrderImportRun run = stagedRun(BrokerOrderReconciliationStatus.MATCHED,
                List.of(stagedOrder("order-1", beforeBaseline)));
        stubRun(run);

        assertThatThrownBy(() -> writer.approve(10L, 20L, 5L, false))
                .isInstanceOf(BrokerOrderApprovalConflictException.class)
                .extracting(exception -> ((BrokerOrderApprovalConflictException) exception).getCode())
                .isEqualTo(ApiErrorCode.BASELINE_EXCLUDED);

        verify(tradeTransactionRepository, never()).save(any());
        verify(brokerOrderLedgerLinkRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsWithoutWritesWhenReplayValidationFindsAnOversell() {
        Instant filledAt = Instant.parse("2026-09-01T13:30:00Z");
        BrokerOrderRecord sellRecord = new BrokerOrderRecord(
                "order-1", Market.US, "AAPL", BrokerOrderSide.SELL, BrokerOrderLifecycle.TERMINAL_WITH_FILL,
                "FILLED", "LIMIT", "DAY", "USD",
                new BigDecimal("10"), new BigDecimal("10"), new BigDecimal("100.25"), new BigDecimal("1002.50"),
                new BigDecimal("1.00"), new BigDecimal("0.00"),
                filledAt.minusSeconds(3600), filledAt, LocalDate.of(2026, 9, 3));
        BrokerOrderStagedOrder sellOrder = new BrokerOrderStagedOrder(
                sellRecord, "애플", BrokerOrderStagingStatus.STAGED, null, "fingerprint-order-1",
                false, false, false);

        BrokerOrderImportRun run = stagedRun(BrokerOrderReconciliationStatus.MATCHED, List.of(sellOrder));
        stubRun(run);

        assertThatThrownBy(() -> writer.approve(10L, 20L, 5L, false))
                .isInstanceOf(BrokerOrderApprovalConflictException.class)
                .extracting(exception -> ((BrokerOrderApprovalConflictException) exception).getCode())
                .isEqualTo(ApiErrorCode.REPLAY_VALIDATION_FAILED);

        verify(tradeTransactionRepository, never()).save(any());
        verify(brokerOrderLedgerLinkRepository, never()).saveAndFlush(any());
    }

    /**
     * 재판정하지 않은 의심 항목은 반영 후보가 아니다. 같은 실행의 반영 후보는 그대로 반영되고
     * 의심 항목은 원장에 들어가지 않는다.
     */
    @Test
    void leavesSuspectedItemsWithoutAnOverrideOutOfTheLedger() {
        Instant filledAt = Instant.parse("2026-09-01T13:30:00Z");
        BrokerOrderImportRun run = stagedRun(BrokerOrderReconciliationStatus.MATCHED, List.of(
                stagedOrder("order-1", filledAt),
                suspectedOrder("order-2", filledAt.plusSeconds(60), BrokerOrderSide.BUY,
                        BrokerOrderStagingStatus.MANUAL_OVERLAP_SUSPECTED)
        ));
        stubRun(run);

        BrokerOrderImportApprovalWriter.ApprovalResult result = writer.approve(10L, 20L, 5L, false);

        assertThat(result.eligibleCount()).isEqualTo(1);
        assertThat(result.writtenCount()).isEqualTo(1);
        assertThat(result.overrideAllowedCount()).isZero();

        ArgumentCaptor<TradeTransaction> captor = ArgumentCaptor.forClass(TradeTransaction.class);
        verify(tradeTransactionRepository).save(captor.capture());
        assertThat(captor.getValue().getTradedAt()).isEqualTo(filledAt);
    }

    /** 의심 항목만 있는 실행은 재판정 전에는 반영할 주문이 없다. 원장에 한 행도 쓰지 않는다. */
    @Test
    void rejectsWithoutWritesWhenTheOnlyCandidatesAreSuspectedItemsWithoutAnOverride() {
        BrokerOrderImportRun run = stagedRun(BrokerOrderReconciliationStatus.MATCHED, List.of(
                suspectedOrder("order-2", Instant.parse("2026-09-01T13:30:00Z"), BrokerOrderSide.BUY,
                        BrokerOrderStagingStatus.DUPLICATE_SUSPECTED)
        ));
        stubRun(run);

        assertThatThrownBy(() -> writer.approve(10L, 20L, 5L, false))
                .isInstanceOf(BrokerOrderApprovalConflictException.class)
                .hasMessage("이 실행에는 원장에 반영할 주문이 없습니다.");

        verify(tradeTransactionRepository, never()).save(any());
        verify(brokerOrderLedgerLinkRepository, never()).saveAndFlush(any());
    }

    /**
     * 반영 허용으로 재판정한 의심 항목은 반영 후보로 들어가 원장에 기록되고, 그 원장 링크는
     * 근거가 된 재판정을 가리킨다. 스테이징 항목의 상태는 바뀌지 않는다.
     */
    @Test
    void writesASuspectedItemAllowedByAnOverrideAndRecordsTheOverrideOnItsLedgerLink() {
        Instant filledAt = Instant.parse("2026-09-01T13:30:00Z");
        BrokerOrderImportRun run = stagedRun(BrokerOrderReconciliationStatus.MATCHED, List.of(
                suspectedOrder("order-2", filledAt, BrokerOrderSide.BUY, BrokerOrderStagingStatus.DUPLICATE_SUSPECTED)
        ));
        BrokerOrderImportItem suspectedItem = itemWithId(run, 0, 102L);
        BrokerOrderImportItemOverride override = override(suspectedItem, BrokerOrderOverrideDecision.ALLOW_LEDGER_WRITE);
        when(brokerOrderImportItemOverrideRepository.findAllByRun_Id(5L)).thenReturn(List.of(override));
        stubRun(run);

        BrokerOrderImportApprovalWriter.ApprovalResult result = writer.approve(10L, 20L, 5L, false);

        assertThat(result.eligibleCount()).isEqualTo(1);
        assertThat(result.writtenCount()).isEqualTo(1);
        assertThat(result.overrideAllowedCount()).isEqualTo(1);

        ArgumentCaptor<BrokerOrderLedgerLink> linkCaptor = ArgumentCaptor.forClass(BrokerOrderLedgerLink.class);
        verify(brokerOrderLedgerLinkRepository).saveAndFlush(linkCaptor.capture());
        assertThat(linkCaptor.getValue().getExternalOrderId()).isEqualTo("order-2");
        assertThat(linkCaptor.getValue().getOverride()).isSameAs(override);
        assertThat(suspectedItem.getStagingStatus()).isEqualTo(BrokerOrderStagingStatus.DUPLICATE_SUSPECTED);
    }

    /** 제외 유지로 재판정한 의심 항목은 반영 후보에서 빠진다. 재판정이 없을 때와 결과가 같다. */
    @Test
    void keepsASuspectedItemOutOfTheLedgerWhenItsOverrideKeepsItExcluded() {
        Instant filledAt = Instant.parse("2026-09-01T13:30:00Z");
        BrokerOrderImportRun run = stagedRun(BrokerOrderReconciliationStatus.MATCHED, List.of(
                stagedOrder("order-1", filledAt),
                suspectedOrder("order-2", filledAt.plusSeconds(60), BrokerOrderSide.BUY,
                        BrokerOrderStagingStatus.MANUAL_OVERLAP_SUSPECTED)
        ));
        itemWithId(run, 0, 101L);
        BrokerOrderImportItem suspectedItem = itemWithId(run, 1, 102L);
        when(brokerOrderImportItemOverrideRepository.findAllByRun_Id(5L))
                .thenReturn(List.of(override(suspectedItem, BrokerOrderOverrideDecision.KEEP_EXCLUDED)));
        stubRun(run);

        BrokerOrderImportApprovalWriter.ApprovalResult result = writer.approve(10L, 20L, 5L, false);

        assertThat(result.eligibleCount()).isEqualTo(1);
        assertThat(result.writtenCount()).isEqualTo(1);
        assertThat(result.overrideAllowedCount()).isZero();

        ArgumentCaptor<BrokerOrderLedgerLink> linkCaptor = ArgumentCaptor.forClass(BrokerOrderLedgerLink.class);
        verify(brokerOrderLedgerLinkRepository).saveAndFlush(linkCaptor.capture());
        assertThat(linkCaptor.getValue().getExternalOrderId()).isEqualTo("order-1");
        assertThat(linkCaptor.getValue().getOverride()).isNull();
    }

    /**
     * 반영 허용 재판정은 전체 재생 검증을 건너뛰는 근거가 아니다. 허용한 항목 때문에 초과 매도가
     * 생기면 배치 전체를 거부하고 원장에 한 행도 쓰지 않는다.
     */
    @Test
    void rejectsWithoutWritesWhenReplayFailsForASuspectedItemAllowedByAnOverride() {
        BrokerOrderImportRun run = stagedRun(BrokerOrderReconciliationStatus.MATCHED, List.of(
                suspectedOrder("order-2", Instant.parse("2026-09-01T13:30:00Z"), BrokerOrderSide.SELL,
                        BrokerOrderStagingStatus.MANUAL_OVERLAP_SUSPECTED)
        ));
        BrokerOrderImportItem suspectedItem = itemWithId(run, 0, 102L);
        when(brokerOrderImportItemOverrideRepository.findAllByRun_Id(5L))
                .thenReturn(List.of(override(suspectedItem, BrokerOrderOverrideDecision.ALLOW_LEDGER_WRITE)));
        stubRun(run);

        assertThatThrownBy(() -> writer.approve(10L, 20L, 5L, false))
                .isInstanceOf(BrokerOrderApprovalConflictException.class)
                .extracting(exception -> ((BrokerOrderApprovalConflictException) exception).getCode())
                .isEqualTo(ApiErrorCode.REPLAY_VALIDATION_FAILED);

        verify(tradeTransactionRepository, never()).save(any());
        verify(brokerOrderLedgerLinkRepository, never()).saveAndFlush(any());
    }

    /**
     * 부분 커버이면서 대조 기준이 없어(NOT_AVAILABLE) 이력 누락 가능성을 배제할 수 없는데
     * 확인 파라미터 없이 승인을 시도하면 409로 거부하고 어떤 행도 쓰지 않는다.
     */
    @Test
    void rejectsWithoutWritesWhenIncompleteCoverageIsNotAcknowledged() {
        BrokerOrderImportRun run = stagedRun(
                BrokerOrderReconciliationStatus.NOT_AVAILABLE,
                List.of(stagedOrder("order-1", Instant.parse("2026-09-01T13:30:00Z"))),
                LocalDate.of(2026, 9, 3));
        stubRun(run);

        assertThatThrownBy(() -> writer.approve(10L, 20L, 5L, false))
                .isInstanceOf(BrokerOrderApprovalConflictException.class)
                .extracting(exception -> ((BrokerOrderApprovalConflictException) exception).getCode())
                .isEqualTo(ApiErrorCode.ORDER_IMPORT_COVERAGE_ACKNOWLEDGEMENT_REQUIRED);

        verify(tradeTransactionRepository, never()).save(any());
        verify(brokerOrderLedgerLinkRepository, never()).saveAndFlush(any());
        assertThat(run.getCoverageAcknowledgedAt()).isNull();
    }

    /** 확인 파라미터를 붙여 같은 요청을 재호출하면 승인이 성공하고 확인 사실이 남는다. */
    @Test
    void approvesAndRecordsTheAcknowledgementWhenIncompleteCoverageIsExplicitlyConfirmed() {
        BrokerOrderImportRun run = stagedRun(
                BrokerOrderReconciliationStatus.NOT_AVAILABLE,
                List.of(stagedOrder("order-1", Instant.parse("2026-09-01T13:30:00Z"))),
                LocalDate.of(2026, 9, 3));
        stubRun(run);

        BrokerOrderImportApprovalWriter.ApprovalResult result = writer.approve(10L, 20L, 5L, true);

        assertThat(result.writtenCount()).isEqualTo(1);
        assertThat(result.coverageAcknowledged()).isTrue();
        assertThat(run.getCoverageAcknowledgedAt()).isEqualTo(LocalDateTime.of(2026, 9, 8, 10, 0));
        assertThat(run.getCoverageAcknowledgedByMemberId()).isEqualTo(10L);
    }

    /** 이미 확인된 실행은 확인 파라미터 없이 재승인해도 통과한다. 다시 묻지 않는다. */
    @Test
    void approvesWithoutReaskingWhenTheRunWasAlreadyAcknowledged() {
        BrokerOrderImportRun run = stagedRun(
                BrokerOrderReconciliationStatus.NOT_AVAILABLE,
                List.of(stagedOrder("order-1", Instant.parse("2026-09-01T13:30:00Z"))),
                LocalDate.of(2026, 9, 3));
        Member firstApprover = new Member("first@example.com", "first-approver");
        ReflectionTestUtils.setField(firstApprover, "id", 99L);
        run.acknowledgeCoverageIfNeeded(LocalDateTime.of(2026, 9, 8, 8, 0), firstApprover);
        stubRun(run);

        BrokerOrderImportApprovalWriter.ApprovalResult result = writer.approve(10L, 20L, 5L, false);

        assertThat(result.writtenCount()).isEqualTo(1);
        assertThat(result.coverageAcknowledged()).isTrue();
        // 최초 확인 시각·주체는 재승인으로 바뀌지 않는다.
        assertThat(run.getCoverageAcknowledgedAt()).isEqualTo(LocalDateTime.of(2026, 9, 8, 8, 0));
        assertThat(run.getCoverageAcknowledgedByMemberId()).isEqualTo(99L);
    }

    /**
     * 확인이 필요 없는 실행(완전 커버 또는 대조 MATCHED)에 확인 파라미터를 보내도 무해하게
     * 통과하고, 확인 기록을 남기지 않는다.
     */
    @Test
    void ignoresTheAcknowledgementFlagWhenTheRunDidNotNeedIt() {
        BrokerOrderImportRun run = stagedRun(BrokerOrderReconciliationStatus.MATCHED,
                List.of(stagedOrder("order-1", Instant.parse("2026-09-01T13:30:00Z"))));
        stubRun(run);

        BrokerOrderImportApprovalWriter.ApprovalResult result = writer.approve(10L, 20L, 5L, true);

        assertThat(result.writtenCount()).isEqualTo(1);
        assertThat(result.coverageAcknowledged()).isFalse();
        assertThat(run.getCoverageAcknowledgedAt()).isNull();
    }

    @Test
    void reportsAMissingRunAsNotFound() {
        when(brokerOrderImportRunRepository.findWithLockByPortfolioIdAndId(20L, 5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> writer.approve(10L, 20L, 5L, false))
                .isInstanceOf(BrokerOrderImportNotFoundException.class);
    }

    @Test
    void rejectsApprovalWithNotFoundWhenPortfolioIsNotOwnedByMember() {
        when(portfolioRepository.findByMember_IdAndId(99L, 20L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> writer.approve(99L, 20L, 5L, false))
                .isInstanceOf(PortfolioNotFoundException.class)
                .hasMessage("포트폴리오를 찾을 수 없습니다.");

        verifyNoInteractions(brokerOrderImportRunRepository, tradeTransactionRepository,
                brokerOrderLedgerLinkRepository, brokerOrderImportItemOverrideRepository);
    }

    private void stubRun(BrokerOrderImportRun run) {
        when(brokerOrderImportRunRepository.findWithLockByPortfolioIdAndId(20L, 5L)).thenReturn(Optional.of(run));
    }

    private BrokerOrderImportRun stagedRun(
            BrokerOrderReconciliationStatus reconciliationStatus,
            List<BrokerOrderStagedOrder> items
    ) {
        return stagedRun(reconciliationStatus, items, null);
    }

    private BrokerOrderImportRun stagedRun(
            BrokerOrderReconciliationStatus reconciliationStatus,
            List<BrokerOrderStagedOrder> items,
            LocalDate coveredOrderedTo
    ) {
        BrokerOrderImportRun run = BrokerOrderImportRun.staged(
                portfolio, connection, account, member,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 5),
                LocalDate.of(2026, 8, 30), LocalDate.of(2026, 9, 7),
                LocalDateTime.of(2026, 9, 8, 9, 0), LocalDateTime.of(2026, 9, 8, 9, 0, 3),
                countsFor(items),
                reconciliationStatus,
                reconciliationStatus == BrokerOrderReconciliationStatus.NOT_AVAILABLE
                        ? null : LocalDateTime.of(2026, 9, 7, 9, 0),
                items,
                List.of(),
                coveredOrderedTo
        );
        ReflectionTestUtils.setField(run, "id", 5L);
        return run;
    }

    private BrokerOrderImportCounts countsFor(List<BrokerOrderStagedOrder> items) {
        int staged = (int) items.stream()
                .filter(item -> item.stagingStatus() == BrokerOrderStagingStatus.STAGED)
                .count();
        return new BrokerOrderImportCounts(
                items.size(), staged, 0, items.size() - staged, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0);
    }

    private BrokerOrderStagedOrder stagedOrder(String orderId, Instant filledAt) {
        return new BrokerOrderStagedOrder(
                record(orderId, filledAt, BrokerOrderSide.BUY), "애플", BrokerOrderStagingStatus.STAGED, null,
                "fingerprint-" + orderId, false, false, false);
    }

    private BrokerOrderStagedOrder suspectedOrder(
            String orderId,
            Instant filledAt,
            BrokerOrderSide side,
            BrokerOrderStagingStatus status
    ) {
        BrokerOrderSkipReason reason = status == BrokerOrderStagingStatus.MANUAL_OVERLAP_SUSPECTED
                ? BrokerOrderSkipReason.MANUAL_OVERLAP
                : BrokerOrderSkipReason.DUPLICATE_FINGERPRINT;
        return new BrokerOrderStagedOrder(
                record(orderId, filledAt, side), "애플", status, reason,
                "fingerprint-" + orderId, false, false, false);
    }

    private BrokerOrderRecord record(String orderId, Instant filledAt, BrokerOrderSide side) {
        return new BrokerOrderRecord(
                orderId, Market.US, "AAPL", side, BrokerOrderLifecycle.TERMINAL_WITH_FILL,
                "FILLED", "LIMIT", "DAY", "USD",
                new BigDecimal("10"), new BigDecimal("10"), new BigDecimal("100.25"), new BigDecimal("1002.50"),
                new BigDecimal("1.00"), new BigDecimal("0.00"),
                filledAt.minusSeconds(3600), filledAt, LocalDate.of(2026, 9, 3));
    }

    private BrokerOrderImportItem itemWithId(BrokerOrderImportRun run, int index, Long id) {
        BrokerOrderImportItem item = run.getItems().get(index);
        ReflectionTestUtils.setField(item, "id", id);
        return item;
    }

    private BrokerOrderImportItemOverride override(BrokerOrderImportItem item, BrokerOrderOverrideDecision decision) {
        return new BrokerOrderImportItemOverride(
                item, decision, "증권사 화면에서 별개 체결로 확인", member, LocalDateTime.of(2026, 9, 8, 9, 30));
    }
}
