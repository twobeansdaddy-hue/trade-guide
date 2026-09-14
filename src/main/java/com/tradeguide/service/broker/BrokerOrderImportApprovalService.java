package com.tradeguide.service.broker;

import com.tradeguide.domain.broker.BrokerOrderImportRun;
import com.tradeguide.domain.broker.BrokerOrderLedgerLink;
import com.tradeguide.domain.broker.BrokerOrderLedgerLinkStatus;
import com.tradeguide.domain.portfolio.Portfolio;
import com.tradeguide.domain.trade.TradeTransaction;
import com.tradeguide.exception.BrokerOrderImportNotFoundException;
import com.tradeguide.exception.PortfolioNotFoundException;
import com.tradeguide.repository.broker.BrokerOrderImportRunRepository;
import com.tradeguide.repository.broker.BrokerOrderLedgerLinkRepository;
import com.tradeguide.repository.portfolio.PortfolioRepository;
import com.tradeguide.repository.trade.TradeTransactionRepository;
import com.tradeguide.service.holding.HoldingCalculator;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 증권사 주문 이력 실행의 승인·취소를 처리한다. 실제 승인 생성은 트랜잭션 경계가 분리된
 * {@link BrokerOrderImportApprovalWriter}에 위임한다.
 */
@Service
public class BrokerOrderImportApprovalService {

    private final PortfolioRepository portfolioRepository;
    private final BrokerOrderImportRunRepository brokerOrderImportRunRepository;
    private final BrokerOrderLedgerLinkRepository brokerOrderLedgerLinkRepository;
    private final TradeTransactionRepository tradeTransactionRepository;
    private final BrokerOrderImportApprovalWriter brokerOrderImportApprovalWriter;
    private final HoldingCalculator holdingCalculator;
    private final Clock clock;

    public BrokerOrderImportApprovalService(
            PortfolioRepository portfolioRepository,
            BrokerOrderImportRunRepository brokerOrderImportRunRepository,
            BrokerOrderLedgerLinkRepository brokerOrderLedgerLinkRepository,
            TradeTransactionRepository tradeTransactionRepository,
            BrokerOrderImportApprovalWriter brokerOrderImportApprovalWriter,
            HoldingCalculator holdingCalculator,
            Clock clock
    ) {
        this.portfolioRepository = portfolioRepository;
        this.brokerOrderImportRunRepository = brokerOrderImportRunRepository;
        this.brokerOrderLedgerLinkRepository = brokerOrderLedgerLinkRepository;
        this.tradeTransactionRepository = tradeTransactionRepository;
        this.brokerOrderImportApprovalWriter = brokerOrderImportApprovalWriter;
        this.holdingCalculator = holdingCalculator;
        this.clock = clock;
    }

    /**
     * 실행 한 건을 승인한다. 동시에 같은 주문을 반영하려는 요청이 유니크 제약에서 경합하면
     * 이 트랜잭션은 전부 롤백되므로, 이긴 쪽이 남긴 최신 상태를 기준으로 딱 한 번만 다시
     * 시도한다. 재시도에서도 실패하면 그대로 알린다.
     */
    public BrokerOrderImportApprovalWriter.ApprovalResult approve(
            Long memberId, Long portfolioId, Long runId, boolean acknowledgeIncompleteCoverage) {
        try {
            return brokerOrderImportApprovalWriter.approve(
                    memberId, portfolioId, runId, acknowledgeIncompleteCoverage);
        } catch (DataIntegrityViolationException exception) {
            return brokerOrderImportApprovalWriter.approve(
                    memberId, portfolioId, runId, acknowledgeIncompleteCoverage);
        }
    }

    @Transactional
    public void revoke(Long memberId, Long portfolioId, Long runId) {
        Portfolio portfolio = requirePortfolio(memberId, portfolioId);
        BrokerOrderImportRun run = requireRun(portfolio, runId);

        List<BrokerOrderLedgerLink> activeLinks = brokerOrderLedgerLinkRepository
                .findAllByRun_IdAndStatus(run.getId(), BrokerOrderLedgerLinkStatus.ACTIVE);

        if (activeLinks.isEmpty()) {
            throw new BrokerOrderImportNotFoundException(
                    "취소할 수 있는 주문 이력 반영 승인을 찾을 수 없습니다.");
        }

        Set<Long> transactionIdsToRemove = activeLinks.stream()
                .map(BrokerOrderLedgerLink::getTradeTransactionId)
                .collect(Collectors.toUnmodifiableSet());

        List<TradeTransaction> remainingTransactions = new ArrayList<>(
                tradeTransactionRepository.findAllByPortfolio_IdOrderByTradedAtAsc(portfolioId));
        List<TradeTransaction> transactionsToDelete = remainingTransactions.stream()
                .filter(transaction -> transactionIdsToRemove.contains(transaction.getId()))
                .toList();
        remainingTransactions.removeIf(transaction -> transactionIdsToRemove.contains(transaction.getId()));

        holdingCalculator.calculate(remainingTransactions);

        transactionsToDelete.forEach(tradeTransactionRepository::delete);

        LocalDateTime revokedAt = LocalDateTime.now(clock);
        activeLinks.forEach(link -> link.revoke(revokedAt));
    }

    private Portfolio requirePortfolio(Long memberId, Long portfolioId) {
        return portfolioRepository.findByMember_IdAndId(memberId, portfolioId)
                .orElseThrow(() -> new PortfolioNotFoundException("포트폴리오를 찾을 수 없습니다."));
    }

    private BrokerOrderImportRun requireRun(Portfolio portfolio, Long runId) {
        return brokerOrderImportRunRepository.findByPortfolio_IdAndId(portfolio.getId(), runId)
                .orElseThrow(() -> new BrokerOrderImportNotFoundException(
                        "요청한 주문 이력 가져오기 실행을 찾을 수 없습니다."));
    }
}
