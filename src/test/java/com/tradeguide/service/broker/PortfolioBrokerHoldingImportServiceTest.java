package com.tradeguide.service.broker;

import com.tradeguide.domain.broker.BrokerHistoryPage;
import com.tradeguide.domain.broker.BrokerHistoryPageRequest;
import com.tradeguide.domain.broker.BrokerOpeningBalanceBatchResult;
import com.tradeguide.domain.broker.PortfolioBrokerHoldingImport;
import com.tradeguide.domain.broker.PortfolioBrokerHoldingImportStatus;
import com.tradeguide.domain.member.Member;
import com.tradeguide.domain.portfolio.Portfolio;
import com.tradeguide.domain.trade.TradeTransaction;
import com.tradeguide.exception.BrokerHoldingImportConflictException;
import com.tradeguide.exception.PortfolioBrokerHoldingImportNotFoundException;
import com.tradeguide.exception.PortfolioNotFoundException;
import com.tradeguide.repository.broker.PortfolioBrokerHoldingImportRepository;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
class PortfolioBrokerHoldingImportServiceTest {

    @Mock
    private PortfolioRepository portfolioRepository;

    @Mock
    private PortfolioBrokerHoldingImportRepository portfolioBrokerHoldingImportRepository;

    @Mock
    private TradeTransactionRepository tradeTransactionRepository;

    @Mock
    private PortfolioBrokerHoldingImportWriter portfolioBrokerHoldingImportWriter;

    @Mock
    private PortfolioBrokerOpeningBalanceBatchWriter portfolioBrokerOpeningBalanceBatchWriter;

    @Mock
    private HoldingCalculator holdingCalculator;

    private PortfolioBrokerHoldingImportService service;
    private Portfolio portfolio;

    @BeforeEach
    void setUp() {
        service = new PortfolioBrokerHoldingImportService(
                portfolioRepository,
                portfolioBrokerHoldingImportRepository,
                tradeTransactionRepository,
                portfolioBrokerHoldingImportWriter,
                portfolioBrokerOpeningBalanceBatchWriter,
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
        PortfolioBrokerHoldingImport created = importRecord();
        when(portfolioBrokerHoldingImportRepository.findBySnapshotItem_Id(55L)).thenReturn(Optional.empty());
        when(portfolioBrokerHoldingImportWriter.createOpeningBalanceImport(10L, 20L, 55L, 10L))
                .thenReturn(created);

        PortfolioBrokerHoldingImportService.ApprovalResult result =
                service.approveOpeningBalance(10L, 20L, 55L);

        assertThat(result.created()).isTrue();
        assertThat(result.importRecord()).isSameAs(created);
    }

    @Test
    void returnsExistingApprovalIdempotentlyWithoutCallingWriter() {
        PortfolioBrokerHoldingImport existing = importRecord();
        when(existing.getPortfolio()).thenReturn(portfolio);
        when(portfolioBrokerHoldingImportRepository.findBySnapshotItem_Id(55L))
                .thenReturn(Optional.of(existing));

        PortfolioBrokerHoldingImportService.ApprovalResult result =
                service.approveOpeningBalance(10L, 20L, 55L);

        assertThat(result.created()).isFalse();
        assertThat(result.importRecord()).isSameAs(existing);
        verifyNoInteractions(portfolioBrokerHoldingImportWriter);
    }

    @Test
    void recoversIdempotentlyWhenConcurrentRequestWinsTheUniqueConstraintRace() {
        PortfolioBrokerHoldingImport winner = importRecord();
        when(winner.getPortfolio()).thenReturn(portfolio);
        when(portfolioBrokerHoldingImportRepository.findBySnapshotItem_Id(55L))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winner));
        when(portfolioBrokerHoldingImportWriter.createOpeningBalanceImport(10L, 20L, 55L, 10L))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        PortfolioBrokerHoldingImportService.ApprovalResult result =
                service.approveOpeningBalance(10L, 20L, 55L);

        assertThat(result.created()).isFalse();
        assertThat(result.importRecord()).isSameAs(winner);
    }

    @Test
    void rejectsApprovalWhenPortfolioIsNotOwnedByMember() {
        when(portfolioRepository.findByMember_IdAndId(99L, 20L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.approveOpeningBalance(99L, 20L, 55L))
                .isInstanceOf(PortfolioNotFoundException.class)
                .hasMessage("포트폴리오를 찾을 수 없습니다.");

        verifyNoInteractions(portfolioBrokerHoldingImportWriter);
    }

    @Test
    void revokesActiveImportAndDeletesLedgerRow() {
        PortfolioBrokerHoldingImport importRecord = importRecord();
        when(importRecord.getTradeTransactionId()).thenReturn(900L);
        when(importRecord.getStatus()).thenReturn(PortfolioBrokerHoldingImportStatus.ACTIVE);
        TradeTransaction transaction = mock(TradeTransaction.class);
        when(transaction.getId()).thenReturn(900L);

        when(portfolioBrokerHoldingImportRepository.findByPortfolio_IdAndId(20L, 1L))
                .thenReturn(Optional.of(importRecord));
        when(tradeTransactionRepository.findByPortfolio_IdAndId(20L, 900L))
                .thenReturn(Optional.of(transaction));
        when(tradeTransactionRepository.findAllByPortfolio_IdOrderByTradedAtAsc(20L))
                .thenReturn(List.of(transaction));

        service.revokeOpeningBalance(10L, 20L, 1L);

        verify(holdingCalculator).calculate(argThat(List::isEmpty));
        verify(tradeTransactionRepository).delete(transaction);
        verify(importRecord).revoke();
    }

    @Test
    void rejectsRevokeWhenImportIsAlreadyRevoked() {
        PortfolioBrokerHoldingImport revoked = importRecord();
        when(revoked.getStatus()).thenReturn(PortfolioBrokerHoldingImportStatus.REVOKED);
        when(portfolioBrokerHoldingImportRepository.findByPortfolio_IdAndId(20L, 1L))
                .thenReturn(Optional.of(revoked));

        assertThatThrownBy(() -> service.revokeOpeningBalance(10L, 20L, 1L))
                .isInstanceOf(PortfolioBrokerHoldingImportNotFoundException.class);

        verify(tradeTransactionRepository, never()).delete(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsRevokeWhenImportDoesNotExistForPortfolio() {
        when(portfolioBrokerHoldingImportRepository.findByPortfolio_IdAndId(20L, 1L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.revokeOpeningBalance(10L, 20L, 1L))
                .isInstanceOf(PortfolioBrokerHoldingImportNotFoundException.class);
    }

    @Test
    void listsImportHistoryOnePageAtATimeNewestFirst() {
        PortfolioBrokerHoldingImport importRecord = importRecord();
        when(portfolioBrokerHoldingImportRepository.findAllByPortfolio_Id(eq(20L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(importRecord), PageRequest.of(0, 2), 5));

        BrokerHistoryPage<PortfolioBrokerHoldingImport> history =
                service.getImportHistory(10L, 20L, new BrokerHistoryPageRequest(0, 2));

        assertThat(history.items()).containsExactly(importRecord);
        assertThat(history.page()).isZero();
        assertThat(history.size()).isEqualTo(2);
        assertThat(history.totalElements()).isEqualTo(5);
        assertThat(history.hasNext()).isTrue();

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(portfolioBrokerHoldingImportRepository).findAllByPortfolio_Id(eq(20L), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(2);
        // 일괄 반영은 여러 건이 같은 승인 시각을 가지므로 id 동점 기준이 함께 있어야 한다.
        assertThat(pageable.getValue().getSort()).isEqualTo(
                Sort.by(Sort.Order.desc("approvedAt"), Sort.Order.desc("id")));
    }

    @Test
    void reportsLastPageWithoutNextPageFlag() {
        when(portfolioBrokerHoldingImportRepository.findAllByPortfolio_Id(eq(20L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(importRecord()), PageRequest.of(2, 2), 5));

        BrokerHistoryPage<PortfolioBrokerHoldingImport> history =
                service.getImportHistory(10L, 20L, new BrokerHistoryPageRequest(2, 2));

        assertThat(history.hasNext()).isFalse();
        assertThat(history.totalElements()).isEqualTo(5);
    }

    @Test
    void rejectsHistoryPageSizeAboveTheServerLimit() {
        assertThatThrownBy(() -> new BrokerHistoryPageRequest(0, BrokerHistoryPageRequest.MAX_SIZE + 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("페이지 크기");

        verifyNoInteractions(portfolioBrokerHoldingImportRepository);
    }

    @Test
    void keepsDetachedRevokedRecordInImportHistory() {
        // 증권사 연결이 삭제되면 파생 스냅샷 항목이 사라지고 취소 이력의 참조만 끊긴다.
        // 조회는 이런 이력을 걸러내지 않고 저장된 값 그대로 돌려줘야 한다.
        PortfolioBrokerHoldingImport detached = detachedRevokedImportRecord();
        PortfolioBrokerHoldingImport active = importRecord();
        when(portfolioBrokerHoldingImportRepository.findAllByPortfolio_Id(eq(20L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(detached, active), PageRequest.of(0, 20), 2));

        List<PortfolioBrokerHoldingImport> history =
                service.getImportHistory(10L, 20L, new BrokerHistoryPageRequest(0, 20)).items();

        assertThat(history).containsExactly(detached, active);
        assertThat(history.getFirst().getSnapshotItem()).isNull();
        assertThat(history.getFirst().getTicker()).isEqualTo("SOXL");
        assertThat(history.getFirst().getQuantity()).isEqualByComparingTo("30");
        assertThat(history.getFirst().getAveragePurchasePrice()).isEqualByComparingTo("20.00");
        assertThat(history.getFirst().getTradeTransactionId()).isEqualTo(900L);
        assertThat(history.getFirst().getStatus()).isEqualTo(PortfolioBrokerHoldingImportStatus.REVOKED);
    }

    @Test
    void delegatesBatchOpeningBalanceApprovalToTheAtomicWriter() {
        BrokerOpeningBalanceBatchResult expected = new BrokerOpeningBalanceBatchResult(
                77L, LocalDateTime.of(2026, 9, 4, 9, 30), List.of(), List.of());
        when(portfolioBrokerOpeningBalanceBatchWriter.approveAll(10L, 20L, 77L, 10L)).thenReturn(expected);

        assertThat(service.approveOpeningBalanceBatch(10L, 20L, 77L)).isSameAs(expected);
    }

    @Test
    void rejectsBatchApprovalWithoutASnapshotId() {
        assertThatThrownBy(() -> service.approveOpeningBalanceBatch(10L, 20L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("스냅샷 ID는 필수입니다.");

        verifyNoInteractions(portfolioBrokerOpeningBalanceBatchWriter);
    }

    @Test
    void rejectsBatchApprovalForAnotherMembersPortfolio() {
        when(portfolioRepository.findByMember_IdAndId(99L, 20L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.approveOpeningBalanceBatch(99L, 20L, 77L))
                .isInstanceOf(PortfolioNotFoundException.class)
                .hasMessage("포트폴리오를 찾을 수 없습니다.");

        verifyNoInteractions(portfolioBrokerOpeningBalanceBatchWriter);
    }

    /**
     * 단건 승인과 달리 일괄 반영은 제약 위반을 조용히 흡수하지 않는다. 동시에 다른 요청이
     * 같은 종목을 반영했다면 이 요청이 판단한 결과가 더는 사실이 아니고, 절반만 반영된 상태를
     * 남기지 않으려면 트랜잭션 전체가 롤백된 사실을 충돌로 알려야 한다.
     */
    @Test
    void reportsConflictWhenAConcurrentRequestWinsDuringBatchApproval() {
        when(portfolioBrokerOpeningBalanceBatchWriter.approveAll(10L, 20L, 77L, 10L))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> service.approveOpeningBalanceBatch(10L, 20L, 77L))
                .isInstanceOf(BrokerHoldingImportConflictException.class);
    }

    private PortfolioBrokerHoldingImport importRecord() {
        return mock(PortfolioBrokerHoldingImport.class);
    }

    /** 증권사 연결 삭제로 스냅샷 항목 참조만 끊긴 취소 이력이다. */
    private PortfolioBrokerHoldingImport detachedRevokedImportRecord() {
        // getSnapshotItem()은 스텁하지 않는다. 목의 기본값 널이 곧 참조가 끊긴 상태다.
        PortfolioBrokerHoldingImport importRecord = mock(PortfolioBrokerHoldingImport.class);
        when(importRecord.getTicker()).thenReturn("SOXL");
        when(importRecord.getQuantity()).thenReturn(new BigDecimal("30"));
        when(importRecord.getAveragePurchasePrice()).thenReturn(new BigDecimal("20.00"));
        when(importRecord.getTradeTransactionId()).thenReturn(900L);
        when(importRecord.getStatus()).thenReturn(PortfolioBrokerHoldingImportStatus.REVOKED);
        return importRecord;
    }
}
