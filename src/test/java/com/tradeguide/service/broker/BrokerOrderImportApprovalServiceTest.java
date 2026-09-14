package com.tradeguide.service.broker;

import com.tradeguide.domain.broker.BrokerOrderImportRun;
import com.tradeguide.domain.broker.BrokerOrderLedgerLink;
import com.tradeguide.domain.broker.BrokerOrderLedgerLinkStatus;
import com.tradeguide.domain.member.Member;
import com.tradeguide.domain.portfolio.Portfolio;
import com.tradeguide.domain.trade.TradeTransaction;
import com.tradeguide.exception.BrokerOrderImportNotFoundException;
import com.tradeguide.exception.PortfolioNotFoundException;
import com.tradeguide.repository.broker.BrokerOrderImportRunRepository;
import com.tradeguide.repository.broker.BrokerOrderLedgerLinkRepository;
import com.tradeguide.repository.portfolio.PortfolioRepository;
import com.tradeguide.repository.trade.TradeTransactionRepository;
import com.tradeguide.service.holding.HoldingCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BrokerOrderImportApprovalServiceTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-09-08T10:00:00Z"), ZoneOffset.UTC);

    @Mock
    private PortfolioRepository portfolioRepository;

    @Mock
    private BrokerOrderImportRunRepository brokerOrderImportRunRepository;

    @Mock
    private BrokerOrderLedgerLinkRepository brokerOrderLedgerLinkRepository;

    @Mock
    private TradeTransactionRepository tradeTransactionRepository;

    @Mock
    private BrokerOrderImportApprovalWriter brokerOrderImportApprovalWriter;

    @Mock
    private HoldingCalculator holdingCalculator;

    private BrokerOrderImportApprovalService service;
    private Portfolio portfolio;

    @BeforeEach
    void setUp() {
        service = new BrokerOrderImportApprovalService(
                portfolioRepository,
                brokerOrderImportRunRepository,
                brokerOrderLedgerLinkRepository,
                tradeTransactionRepository,
                brokerOrderImportApprovalWriter,
                holdingCalculator,
                FIXED_CLOCK
        );

        Member member = new Member("broker@example.com", "broker-user");
        ReflectionTestUtils.setField(member, "id", 10L);
        portfolio = new Portfolio(member, "성장 포트폴리오");
        ReflectionTestUtils.setField(portfolio, "id", 20L);
    }

    @Test
    void retriesOnceWhenAConcurrentApprovalWinsTheUniqueConstraintRaceAndReturnsTheRetriedResult() {
        BrokerOrderImportApprovalWriter.ApprovalResult retriedResult = new BrokerOrderImportApprovalWriter.ApprovalResult(
                5L, 1, 0, 1, 0, LocalDateTime.of(2026, 9, 8, 10, 0), 10L, null, false, 0);
        when(brokerOrderImportApprovalWriter.approve(10L, 20L, 5L, false))
                .thenThrow(new DataIntegrityViolationException("duplicate key"))
                .thenReturn(retriedResult);

        BrokerOrderImportApprovalWriter.ApprovalResult result = service.approve(10L, 20L, 5L, false);

        assertThat(result).isSameAs(retriedResult);
        verify(brokerOrderImportApprovalWriter, times(2)).approve(10L, 20L, 5L, false);
    }

    @Test
    void propagatesWhenTheRetryAlsoFailsWithAUniqueConstraintViolation() {
        when(brokerOrderImportApprovalWriter.approve(10L, 20L, 5L, false))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> service.approve(10L, 20L, 5L, false))
                .isInstanceOf(DataIntegrityViolationException.class);

        verify(brokerOrderImportApprovalWriter, times(2)).approve(10L, 20L, 5L, false);
    }

    /** 확인 플래그는 그대로 전달돼야 한다. 재시도 경로에서 빠뜨리면 두 번째 시도가 다시 막힌다. */
    @Test
    void passesTheAcknowledgementFlagThroughToTheWriter() {
        BrokerOrderImportApprovalWriter.ApprovalResult result = new BrokerOrderImportApprovalWriter.ApprovalResult(
                5L, 1, 0, 0, 1, LocalDateTime.of(2026, 9, 8, 10, 0), 10L, null, true, 0);
        when(brokerOrderImportApprovalWriter.approve(10L, 20L, 5L, true)).thenReturn(result);

        BrokerOrderImportApprovalWriter.ApprovalResult actual = service.approve(10L, 20L, 5L, true);

        assertThat(actual.coverageAcknowledged()).isTrue();
        verify(brokerOrderImportApprovalWriter).approve(10L, 20L, 5L, true);
    }

    @Test
    void revokeDeletesLedgerTransactionsAndMarksLinksRevokedWithoutDeletingTheirRows() {
        when(portfolioRepository.findByMember_IdAndId(10L, 20L)).thenReturn(Optional.of(portfolio));
        BrokerOrderImportRun run = mock(BrokerOrderImportRun.class);
        when(run.getId()).thenReturn(5L);
        when(brokerOrderImportRunRepository.findByPortfolio_IdAndId(20L, 5L)).thenReturn(Optional.of(run));

        BrokerOrderLedgerLink linkOne = mock(BrokerOrderLedgerLink.class);
        when(linkOne.getTradeTransactionId()).thenReturn(900L);
        BrokerOrderLedgerLink linkTwo = mock(BrokerOrderLedgerLink.class);
        when(linkTwo.getTradeTransactionId()).thenReturn(901L);
        when(brokerOrderLedgerLinkRepository.findAllByRun_IdAndStatus(5L, BrokerOrderLedgerLinkStatus.ACTIVE))
                .thenReturn(List.of(linkOne, linkTwo));

        TradeTransaction approvedTransactionOne = mock(TradeTransaction.class);
        when(approvedTransactionOne.getId()).thenReturn(900L);
        TradeTransaction approvedTransactionTwo = mock(TradeTransaction.class);
        when(approvedTransactionTwo.getId()).thenReturn(901L);
        TradeTransaction unrelatedManualTransaction = mock(TradeTransaction.class);
        when(unrelatedManualTransaction.getId()).thenReturn(700L);
        when(tradeTransactionRepository.findAllByPortfolio_IdOrderByTradedAtAsc(20L))
                .thenReturn(List.of(unrelatedManualTransaction, approvedTransactionOne, approvedTransactionTwo));

        service.revoke(10L, 20L, 5L);

        verify(holdingCalculator).calculate(argThat(list -> list.equals(List.of(unrelatedManualTransaction))));
        verify(tradeTransactionRepository).delete(approvedTransactionOne);
        verify(tradeTransactionRepository).delete(approvedTransactionTwo);
        verify(tradeTransactionRepository, never()).delete(unrelatedManualTransaction);
        verify(linkOne).revoke(LocalDateTime.of(2026, 9, 8, 10, 0));
        verify(linkTwo).revoke(LocalDateTime.of(2026, 9, 8, 10, 0));
        // 감사 이력은 행 자체를 지우지 않고 상태만 바꾼다. 링크 저장소의 delete는 절대 호출되지 않는다.
        verify(brokerOrderLedgerLinkRepository, never()).delete(org.mockito.ArgumentMatchers.any());
        verify(brokerOrderLedgerLinkRepository, never()).deleteById(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsRevokeWhenRunHasNoActiveApprovalToRevoke() {
        when(portfolioRepository.findByMember_IdAndId(10L, 20L)).thenReturn(Optional.of(portfolio));
        BrokerOrderImportRun run = mock(BrokerOrderImportRun.class);
        when(run.getId()).thenReturn(5L);
        when(brokerOrderImportRunRepository.findByPortfolio_IdAndId(20L, 5L)).thenReturn(Optional.of(run));
        when(brokerOrderLedgerLinkRepository.findAllByRun_IdAndStatus(5L, BrokerOrderLedgerLinkStatus.ACTIVE))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.revoke(10L, 20L, 5L))
                .isInstanceOf(BrokerOrderImportNotFoundException.class);

        verify(tradeTransactionRepository, never()).delete(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsRevokeWhenPortfolioIsNotOwnedByMember() {
        when(portfolioRepository.findByMember_IdAndId(99L, 20L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.revoke(99L, 20L, 5L))
                .isInstanceOf(PortfolioNotFoundException.class);

        verify(brokerOrderLedgerLinkRepository, never()).findAllByRun_IdAndStatus(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsRevokeWhenRunDoesNotExistForPortfolio() {
        when(portfolioRepository.findByMember_IdAndId(10L, 20L)).thenReturn(Optional.of(portfolio));
        when(brokerOrderImportRunRepository.findByPortfolio_IdAndId(20L, 5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.revoke(10L, 20L, 5L))
                .isInstanceOf(BrokerOrderImportNotFoundException.class);
    }
}
