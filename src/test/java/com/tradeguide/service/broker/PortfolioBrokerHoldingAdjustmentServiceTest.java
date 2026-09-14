package com.tradeguide.service.broker;

import com.tradeguide.domain.broker.BrokerHistoryPage;
import com.tradeguide.domain.broker.BrokerHistoryPageRequest;
import com.tradeguide.domain.broker.PortfolioBrokerHoldingAdjustment;
import com.tradeguide.domain.broker.PortfolioBrokerHoldingAdjustmentStatus;
import com.tradeguide.domain.member.Member;
import com.tradeguide.domain.portfolio.Portfolio;
import com.tradeguide.domain.trade.TradeTransaction;
import com.tradeguide.exception.PortfolioBrokerHoldingAdjustmentNotFoundException;
import com.tradeguide.exception.PortfolioNotFoundException;
import com.tradeguide.repository.broker.PortfolioBrokerHoldingAdjustmentRepository;
import com.tradeguide.repository.portfolio.PortfolioRepository;
import com.tradeguide.repository.trade.TradeTransactionRepository;
import com.tradeguide.service.holding.HoldingCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioBrokerHoldingAdjustmentServiceTest {

    @Mock
    private PortfolioRepository portfolioRepository;

    @Mock
    private PortfolioBrokerHoldingAdjustmentRepository portfolioBrokerHoldingAdjustmentRepository;

    @Mock
    private TradeTransactionRepository tradeTransactionRepository;

    @Mock
    private PortfolioBrokerHoldingAdjustmentWriter portfolioBrokerHoldingAdjustmentWriter;

    @Mock
    private HoldingCalculator holdingCalculator;

    private PortfolioBrokerHoldingAdjustmentService service;
    private Portfolio portfolio;

    @BeforeEach
    void setUp() {
        service = new PortfolioBrokerHoldingAdjustmentService(
                portfolioRepository,
                portfolioBrokerHoldingAdjustmentRepository,
                tradeTransactionRepository,
                portfolioBrokerHoldingAdjustmentWriter,
                holdingCalculator
        );

        Member member = new Member("broker@example.com", "broker-user");
        ReflectionTestUtils.setField(member, "id", 10L);
        portfolio = new Portfolio(member, "성장 포트폴리오");
        ReflectionTestUtils.setField(portfolio, "id", 20L);

        lenient().when(portfolioRepository.findByMember_IdAndId(10L, 20L)).thenReturn(Optional.of(portfolio));
    }

    @Test
    void createsNewApprovalWhenNoneExistsYet() {
        PortfolioBrokerHoldingAdjustment created = adjustment();
        when(portfolioBrokerHoldingAdjustmentRepository.findBySnapshotItem_Id(55L)).thenReturn(Optional.empty());
        when(portfolioBrokerHoldingAdjustmentWriter.createAdjustment(10L, 20L, 55L, 10L))
                .thenReturn(created);

        PortfolioBrokerHoldingAdjustmentService.ApprovalResult result =
                service.approveAdjustment(10L, 20L, 55L);

        assertThat(result.created()).isTrue();
        assertThat(result.adjustment()).isSameAs(created);
    }

    /** 같은 스냅샷 항목에 대한 재승인은 새 거래를 만들지 않고 기존 결과를 그대로 돌려준다. */
    @Test
    void returnsExistingApprovalIdempotentlyWithoutCallingWriter() {
        PortfolioBrokerHoldingAdjustment existing = adjustment();
        when(existing.getPortfolio()).thenReturn(portfolio);
        when(portfolioBrokerHoldingAdjustmentRepository.findBySnapshotItem_Id(55L))
                .thenReturn(Optional.of(existing));

        PortfolioBrokerHoldingAdjustmentService.ApprovalResult result =
                service.approveAdjustment(10L, 20L, 55L);

        assertThat(result.created()).isFalse();
        assertThat(result.adjustment()).isSameAs(existing);
        verifyNoInteractions(portfolioBrokerHoldingAdjustmentWriter);
    }

    @Test
    void recoversIdempotentlyWhenConcurrentRequestWinsTheUniqueConstraintRace() {
        PortfolioBrokerHoldingAdjustment winner = adjustment();
        when(winner.getPortfolio()).thenReturn(portfolio);
        when(portfolioBrokerHoldingAdjustmentRepository.findBySnapshotItem_Id(55L))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winner));
        when(portfolioBrokerHoldingAdjustmentWriter.createAdjustment(10L, 20L, 55L, 10L))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        PortfolioBrokerHoldingAdjustmentService.ApprovalResult result =
                service.approveAdjustment(10L, 20L, 55L);

        assertThat(result.created()).isFalse();
        assertThat(result.adjustment()).isSameAs(winner);
    }

    @Test
    void rejectsApprovalWhenPortfolioIsNotOwnedByMember() {
        when(portfolioRepository.findByMember_IdAndId(99L, 20L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.approveAdjustment(99L, 20L, 55L))
                .isInstanceOf(PortfolioNotFoundException.class)
                .hasMessage("포트폴리오를 찾을 수 없습니다.");

        verifyNoInteractions(portfolioBrokerHoldingAdjustmentWriter);
    }

    @Test
    void rejectsApprovalWithoutASnapshotItemId() {
        assertThatThrownBy(() -> service.approveAdjustment(10L, 20L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("스냅샷 항목 ID는 필수입니다.");

        verifyNoInteractions(portfolioBrokerHoldingAdjustmentWriter);
    }

    @Test
    void revokesActiveAdjustmentAndDeletesLedgerRow() {
        PortfolioBrokerHoldingAdjustment adjustment = adjustment();
        when(adjustment.getTradeTransactionId()).thenReturn(900L);
        when(adjustment.getStatus()).thenReturn(PortfolioBrokerHoldingAdjustmentStatus.ACTIVE);
        TradeTransaction transaction = mock(TradeTransaction.class);
        when(transaction.getId()).thenReturn(900L);

        when(portfolioBrokerHoldingAdjustmentRepository.findByPortfolio_IdAndId(20L, 1L))
                .thenReturn(Optional.of(adjustment));
        when(tradeTransactionRepository.findByPortfolio_IdAndId(20L, 900L))
                .thenReturn(Optional.of(transaction));
        when(tradeTransactionRepository.findAllByPortfolio_IdOrderByTradedAtAsc(20L))
                .thenReturn(List.of(transaction));

        service.revokeAdjustment(10L, 20L, 1L);

        // "복구": 조정 매수를 제외한 나머지 이력만으로 재계산해 초과 매도가 되지 않는지 검증한다.
        verify(holdingCalculator).calculate(argThat(List::isEmpty));
        verify(tradeTransactionRepository).delete(transaction);
        verify(adjustment).revoke();
    }

    @Test
    void rejectsRevokeWhenAdjustmentIsAlreadyRevoked() {
        PortfolioBrokerHoldingAdjustment revoked = adjustment();
        when(revoked.getStatus()).thenReturn(PortfolioBrokerHoldingAdjustmentStatus.REVOKED);
        when(portfolioBrokerHoldingAdjustmentRepository.findByPortfolio_IdAndId(20L, 1L))
                .thenReturn(Optional.of(revoked));

        assertThatThrownBy(() -> service.revokeAdjustment(10L, 20L, 1L))
                .isInstanceOf(PortfolioBrokerHoldingAdjustmentNotFoundException.class);

        verify(tradeTransactionRepository, never()).delete(any());
    }

    @Test
    void rejectsRevokeWhenAdjustmentDoesNotExistForPortfolio() {
        when(portfolioBrokerHoldingAdjustmentRepository.findByPortfolio_IdAndId(20L, 1L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.revokeAdjustment(10L, 20L, 1L))
                .isInstanceOf(PortfolioBrokerHoldingAdjustmentNotFoundException.class);
    }

    @Test
    void rejectsRevokeForAnotherMembersPortfolio() {
        when(portfolioRepository.findByMember_IdAndId(99L, 20L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.revokeAdjustment(99L, 20L, 1L))
                .isInstanceOf(PortfolioNotFoundException.class);

        verifyNoInteractions(portfolioBrokerHoldingAdjustmentRepository);
    }

    @Test
    void listsAdjustmentHistoryOnePageAtATimeNewestFirst() {
        PortfolioBrokerHoldingAdjustment adjustment = adjustment();
        when(portfolioBrokerHoldingAdjustmentRepository.findAllByPortfolio_Id(eq(20L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(adjustment), PageRequest.of(0, 2), 5));

        BrokerHistoryPage<PortfolioBrokerHoldingAdjustment> history =
                service.getAdjustmentHistory(10L, 20L, new BrokerHistoryPageRequest(0, 2));

        assertThat(history.items()).containsExactly(adjustment);
        assertThat(history.page()).isZero();
        assertThat(history.size()).isEqualTo(2);
        assertThat(history.totalElements()).isEqualTo(5);
        assertThat(history.hasNext()).isTrue();

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(portfolioBrokerHoldingAdjustmentRepository).findAllByPortfolio_Id(eq(20L), pageable.capture());
        assertThat(pageable.getValue().getSort()).isEqualTo(
                Sort.by(Sort.Order.desc("approvedAt"), Sort.Order.desc("id")));
    }

    @Test
    void reportsLastPageWithoutNextPageFlag() {
        when(portfolioBrokerHoldingAdjustmentRepository.findAllByPortfolio_Id(eq(20L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(adjustment()), PageRequest.of(2, 2), 5));

        BrokerHistoryPage<PortfolioBrokerHoldingAdjustment> history =
                service.getAdjustmentHistory(10L, 20L, new BrokerHistoryPageRequest(2, 2));

        assertThat(history.hasNext()).isFalse();
        assertThat(history.totalElements()).isEqualTo(5);
    }

    private PortfolioBrokerHoldingAdjustment adjustment() {
        return mock(PortfolioBrokerHoldingAdjustment.class);
    }
}
