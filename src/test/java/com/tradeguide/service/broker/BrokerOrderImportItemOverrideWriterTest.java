package com.tradeguide.service.broker;

import com.tradeguide.domain.broker.BrokerAccount;
import com.tradeguide.domain.broker.BrokerConnection;
import com.tradeguide.domain.broker.BrokerConnectionSecretValue;
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
import com.tradeguide.dto.ApiErrorCode;
import com.tradeguide.exception.BrokerOrderImportNotFoundException;
import com.tradeguide.exception.BrokerOrderOverrideConflictException;
import com.tradeguide.exception.PortfolioNotFoundException;
import com.tradeguide.repository.broker.BrokerOrderImportItemOverrideRepository;
import com.tradeguide.repository.broker.BrokerOrderImportItemRepository;
import com.tradeguide.repository.broker.BrokerOrderImportRunRepository;
import com.tradeguide.repository.portfolio.PortfolioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BrokerOrderImportItemOverrideWriterTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-09-08T10:00:00Z"), ZoneOffset.UTC);

    @Mock
    private PortfolioRepository portfolioRepository;

    @Mock
    private BrokerOrderImportRunRepository brokerOrderImportRunRepository;

    @Mock
    private BrokerOrderImportItemRepository brokerOrderImportItemRepository;

    @Mock
    private BrokerOrderImportItemOverrideRepository brokerOrderImportItemOverrideRepository;

    private BrokerOrderImportItemOverrideWriter writer;
    private Member member;
    private BrokerOrderImportItem stagedItem;
    private BrokerOrderImportItem suspectedItem;

    @BeforeEach
    void setUp() {
        writer = new BrokerOrderImportItemOverrideWriter(
                portfolioRepository,
                brokerOrderImportRunRepository,
                brokerOrderImportItemRepository,
                brokerOrderImportItemOverrideRepository,
                FIXED_CLOCK
        );

        member = new Member("broker@example.com", "broker-user");
        ReflectionTestUtils.setField(member, "id", 10L);
        Portfolio portfolio = new Portfolio(member, "성장 포트폴리오");
        ReflectionTestUtils.setField(portfolio, "id", 20L);

        BrokerConnection connection = new BrokerConnection(member, BrokerProvider.TOSS_SECURITIES, "개인 토스증권");
        connection.replaceSecretValues(List.of(
                new BrokerConnectionSecretValue("clientId", "encrypted-client-id", "client-id-iv", 1),
                new BrokerConnectionSecretValue("clientSecret", "encrypted-client-secret", "client-secret-iv", 1)
        ));
        connection.reconcileVerifiedAccounts(List.of(
                new BrokerAccount("encrypted-sequence", "sequence-iv", "*****1234", "위탁", 1)
        ));
        connection.markConnected("*****1234");
        BrokerAccount account = connection.getAccounts().getFirst();

        BrokerOrderImportRun run = BrokerOrderImportRun.staged(
                portfolio, connection, account, member,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 5),
                LocalDate.of(2026, 8, 30), LocalDate.of(2026, 9, 7),
                LocalDateTime.of(2026, 9, 8, 9, 0), LocalDateTime.of(2026, 9, 8, 9, 0, 3),
                new BrokerOrderImportCounts(2, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0),
                BrokerOrderReconciliationStatus.MATCHED,
                LocalDateTime.of(2026, 9, 7, 9, 0),
                List.of(
                        order("order-1", BrokerOrderStagingStatus.STAGED, null),
                        order("order-2", BrokerOrderStagingStatus.MANUAL_OVERLAP_SUSPECTED,
                                BrokerOrderSkipReason.MANUAL_OVERLAP)
                ),
                List.of(),
                null
        );
        ReflectionTestUtils.setField(run, "id", 5L);
        stagedItem = run.getItems().get(0);
        ReflectionTestUtils.setField(stagedItem, "id", 101L);
        suspectedItem = run.getItems().get(1);
        ReflectionTestUtils.setField(suspectedItem, "id", 102L);

        lenient().when(portfolioRepository.findByMember_IdAndId(10L, 20L)).thenReturn(Optional.of(portfolio));
        lenient().when(brokerOrderImportRunRepository.findWithLockByPortfolioIdAndId(20L, 5L))
                .thenReturn(Optional.of(run));
        lenient().when(brokerOrderImportItemRepository.findByIdAndRun_Id(101L, 5L)).thenReturn(Optional.of(stagedItem));
        lenient().when(brokerOrderImportItemRepository.findByIdAndRun_Id(102L, 5L)).thenReturn(Optional.of(suspectedItem));
        lenient().when(brokerOrderImportItemOverrideRepository.saveAndFlush(any(BrokerOrderImportItemOverride.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void recordsAnOverrideWithItsAuthorTimeReasonAndBothStatuses() {
        BrokerOrderImportItemOverrideWriter.OverrideResult result = writer.create(
                10L, 20L, 5L, 102L, BrokerOrderOverrideDecision.ALLOW_LEDGER_WRITE, " 증권사 화면에서 별개 체결 확인 ");

        assertThat(result.created()).isTrue();
        BrokerOrderImportItemOverride override = result.override();
        assertThat(override.getItem()).isSameAs(suspectedItem);
        assertThat(override.getRun().getId()).isEqualTo(5L);
        assertThat(override.getCreatedByMemberId()).isEqualTo(10L);
        assertThat(override.getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 9, 8, 10, 0));
        assertThat(override.getReason()).isEqualTo("증권사 화면에서 별개 체결 확인");
        assertThat(override.getOriginalStagingStatus()).isEqualTo(BrokerOrderStagingStatus.MANUAL_OVERLAP_SUSPECTED);
        assertThat(override.getResultingStagingStatus()).isEqualTo(BrokerOrderStagingStatus.STAGED);
        // 스테이징 원본은 그대로다.
        assertThat(suspectedItem.getStagingStatus()).isEqualTo(BrokerOrderStagingStatus.MANUAL_OVERLAP_SUSPECTED);
        verify(brokerOrderImportItemOverrideRepository).saveAndFlush(override);
    }

    /** 같은 결정을 다시 보내면 새 행 없이 최초 기록을 돌려준다. 사유도 최초 값을 유지한다. */
    @Test
    void returnsTheExistingOverrideWithoutWritingWhenTheSameDecisionIsRepeated() {
        BrokerOrderImportItemOverride existing = new BrokerOrderImportItemOverride(
                suspectedItem, BrokerOrderOverrideDecision.KEEP_EXCLUDED, "처음 적은 사유", member,
                LocalDateTime.of(2026, 9, 8, 9, 0));
        when(brokerOrderImportItemOverrideRepository.findByItem_Id(102L)).thenReturn(Optional.of(existing));

        BrokerOrderImportItemOverrideWriter.OverrideResult result = writer.create(
                10L, 20L, 5L, 102L, BrokerOrderOverrideDecision.KEEP_EXCLUDED, "다시 보낸 사유");

        assertThat(result.created()).isFalse();
        assertThat(result.override()).isSameAs(existing);
        assertThat(result.override().getReason()).isEqualTo("처음 적은 사유");
        verify(brokerOrderImportItemOverrideRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsADifferentDecisionForAnAlreadyOverriddenItem() {
        BrokerOrderImportItemOverride existing = new BrokerOrderImportItemOverride(
                suspectedItem, BrokerOrderOverrideDecision.KEEP_EXCLUDED, "제외 유지", member,
                LocalDateTime.of(2026, 9, 8, 9, 0));
        when(brokerOrderImportItemOverrideRepository.findByItem_Id(102L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> writer.create(
                10L, 20L, 5L, 102L, BrokerOrderOverrideDecision.ALLOW_LEDGER_WRITE, "마음이 바뀜"))
                .isInstanceOf(BrokerOrderOverrideConflictException.class)
                .extracting(exception -> ((BrokerOrderOverrideConflictException) exception).getCode())
                .isEqualTo(ApiErrorCode.ORDER_IMPORT_OVERRIDE_CONFLICT);

        verify(brokerOrderImportItemOverrideRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsAnItemThatIsNotSuspected() {
        assertThatThrownBy(() -> writer.create(
                10L, 20L, 5L, 101L, BrokerOrderOverrideDecision.KEEP_EXCLUDED, "반영 후보를 제외하려 함"))
                .isInstanceOf(BrokerOrderOverrideConflictException.class)
                .extracting(exception -> ((BrokerOrderOverrideConflictException) exception).getCode())
                .isEqualTo(ApiErrorCode.ORDER_IMPORT_ITEM_NOT_OVERRIDABLE);

        verify(brokerOrderImportItemOverrideRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsABlankReasonBeforeReadingAnything() {
        assertThatThrownBy(() -> writer.create(
                10L, 20L, 5L, 102L, BrokerOrderOverrideDecision.ALLOW_LEDGER_WRITE, "   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("재판정 사유는 필수입니다.");

        verifyNoInteractions(portfolioRepository, brokerOrderImportRunRepository,
                brokerOrderImportItemRepository, brokerOrderImportItemOverrideRepository);
    }

    @Test
    void rejectsAMissingDecision() {
        assertThatThrownBy(() -> writer.create(10L, 20L, 5L, 102L, null, "사유"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("재판정 결정은 필수입니다.");

        verifyNoInteractions(brokerOrderImportItemOverrideRepository);
    }

    /** 다른 실행의 항목 id를 끼워 넣어도 이 실행 범위에서 찾지 못하므로 404다. */
    @Test
    void reportsAnItemOutsideTheRunAsNotFound() {
        when(brokerOrderImportItemRepository.findByIdAndRun_Id(999L, 5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> writer.create(
                10L, 20L, 5L, 999L, BrokerOrderOverrideDecision.ALLOW_LEDGER_WRITE, "사유"))
                .isInstanceOf(BrokerOrderImportNotFoundException.class)
                .hasMessage("요청한 주문 항목을 찾을 수 없습니다.");

        verify(brokerOrderImportItemOverrideRepository, never()).saveAndFlush(any());
    }

    @Test
    void reportsARunOutsideThePortfolioAsNotFound() {
        when(brokerOrderImportRunRepository.findWithLockByPortfolioIdAndId(20L, 6L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> writer.create(
                10L, 20L, 6L, 102L, BrokerOrderOverrideDecision.ALLOW_LEDGER_WRITE, "사유"))
                .isInstanceOf(BrokerOrderImportNotFoundException.class);

        verifyNoInteractions(brokerOrderImportItemRepository, brokerOrderImportItemOverrideRepository);
    }

    @Test
    void reportsAPortfolioNotOwnedByTheMemberAsNotFound() {
        when(portfolioRepository.findByMember_IdAndId(99L, 20L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> writer.create(
                99L, 20L, 5L, 102L, BrokerOrderOverrideDecision.ALLOW_LEDGER_WRITE, "사유"))
                .isInstanceOf(PortfolioNotFoundException.class);

        verifyNoInteractions(brokerOrderImportRunRepository, brokerOrderImportItemRepository,
                brokerOrderImportItemOverrideRepository);
    }

    private BrokerOrderStagedOrder order(String orderId, BrokerOrderStagingStatus status, BrokerOrderSkipReason reason) {
        Instant filledAt = Instant.parse("2026-09-01T13:30:00Z");
        BrokerOrderRecord record = new BrokerOrderRecord(
                orderId, Market.US, "AAPL", BrokerOrderSide.BUY, BrokerOrderLifecycle.TERMINAL_WITH_FILL,
                "FILLED", "LIMIT", "DAY", "USD",
                new BigDecimal("10"), new BigDecimal("10"), new BigDecimal("100.25"), new BigDecimal("1002.50"),
                new BigDecimal("1.00"), new BigDecimal("0.00"),
                filledAt.minusSeconds(3600), filledAt, LocalDate.of(2026, 9, 3));
        return new BrokerOrderStagedOrder(record, "애플", status, reason, "fingerprint-" + orderId, false, false, false);
    }
}
