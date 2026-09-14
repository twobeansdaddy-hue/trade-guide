package com.tradeguide.service.broker;

import com.tradeguide.domain.broker.BrokerHistoryPage;
import com.tradeguide.domain.broker.BrokerHistoryPageRequest;
import com.tradeguide.domain.broker.PortfolioBrokerHoldingAdjustment;
import com.tradeguide.domain.broker.PortfolioBrokerHoldingAdjustmentStatus;
import com.tradeguide.domain.portfolio.Portfolio;
import com.tradeguide.domain.trade.TradeTransaction;
import com.tradeguide.exception.PortfolioBrokerHoldingAdjustmentNotFoundException;
import com.tradeguide.exception.PortfolioNotFoundException;
import com.tradeguide.repository.broker.PortfolioBrokerHoldingAdjustmentRepository;
import com.tradeguide.repository.portfolio.PortfolioRepository;
import com.tradeguide.repository.trade.TradeTransactionRepository;
import com.tradeguide.service.holding.HoldingCalculator;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 증권사 잔고 조정 승인·조회·취소를 처리한다. 실제 승인 생성은 트랜잭션 경계가
 * 분리된 {@link PortfolioBrokerHoldingAdjustmentWriter}에 위임한다.
 */
@Service
public class PortfolioBrokerHoldingAdjustmentService {

    /**
     * 승인 시각만으로는 순서가 정해지지 않는다. id를 동점 기준으로 함께 두어야
     * 페이지 경계에서 항목이 중복되거나 빠지지 않는다.
     */
    private static final Sort HISTORY_SORT = Sort.by(
            Sort.Order.desc("approvedAt"),
            Sort.Order.desc("id")
    );

    private final PortfolioRepository portfolioRepository;
    private final PortfolioBrokerHoldingAdjustmentRepository portfolioBrokerHoldingAdjustmentRepository;
    private final TradeTransactionRepository tradeTransactionRepository;
    private final PortfolioBrokerHoldingAdjustmentWriter portfolioBrokerHoldingAdjustmentWriter;
    private final HoldingCalculator holdingCalculator;

    public PortfolioBrokerHoldingAdjustmentService(
            PortfolioRepository portfolioRepository,
            PortfolioBrokerHoldingAdjustmentRepository portfolioBrokerHoldingAdjustmentRepository,
            TradeTransactionRepository tradeTransactionRepository,
            PortfolioBrokerHoldingAdjustmentWriter portfolioBrokerHoldingAdjustmentWriter,
            HoldingCalculator holdingCalculator
    ) {
        this.portfolioRepository = portfolioRepository;
        this.portfolioBrokerHoldingAdjustmentRepository = portfolioBrokerHoldingAdjustmentRepository;
        this.tradeTransactionRepository = tradeTransactionRepository;
        this.portfolioBrokerHoldingAdjustmentWriter = portfolioBrokerHoldingAdjustmentWriter;
        this.holdingCalculator = holdingCalculator;
    }

    /**
     * 같은 스냅샷 항목에 대해 이미 승인(또는 취소)된 이력이 있으면 새 거래를 만들지
     * 않고 그 결과를 그대로 돌려준다. 재승인이 새 조정을 만들지 않도록 하기 위해서다.
     */
    public ApprovalResult approveAdjustment(Long memberId, Long portfolioId, Long snapshotItemId) {
        if (snapshotItemId == null) {
            throw new IllegalArgumentException("스냅샷 항목 ID는 필수입니다.");
        }

        requirePortfolio(memberId, portfolioId);

        Optional<PortfolioBrokerHoldingAdjustment> existing = findExistingForPortfolio(portfolioId, snapshotItemId);
        if (existing.isPresent()) {
            return new ApprovalResult(existing.get(), false);
        }

        try {
            PortfolioBrokerHoldingAdjustment created = portfolioBrokerHoldingAdjustmentWriter
                    .createAdjustment(memberId, portfolioId, snapshotItemId, memberId);
            return new ApprovalResult(created, true);
        } catch (DataIntegrityViolationException exception) {
            return new ApprovalResult(
                    findExistingForPortfolio(portfolioId, snapshotItemId).orElseThrow(() -> exception),
                    false
            );
        }
    }

    /**
     * 감사 이력을 최신순 한 페이지만 읽는다. 이력은 계속 쌓이므로 과거 전체를 한 번에
     * 돌려주지 않는다.
     */
    @Transactional(readOnly = true)
    public BrokerHistoryPage<PortfolioBrokerHoldingAdjustment> getAdjustmentHistory(
            Long memberId,
            Long portfolioId,
            BrokerHistoryPageRequest pageRequest
    ) {
        requirePortfolio(memberId, portfolioId);

        Page<PortfolioBrokerHoldingAdjustment> page = portfolioBrokerHoldingAdjustmentRepository.findAllByPortfolio_Id(
                portfolioId,
                PageRequest.of(pageRequest.page(), pageRequest.size(), HISTORY_SORT)
        );

        return new BrokerHistoryPage<>(
                page.getContent(),
                pageRequest.page(),
                pageRequest.size(),
                page.getTotalElements(),
                page.hasNext()
        );
    }

    @Transactional
    public void revokeAdjustment(Long memberId, Long portfolioId, Long adjustmentId) {
        requirePortfolio(memberId, portfolioId);

        PortfolioBrokerHoldingAdjustment adjustment = portfolioBrokerHoldingAdjustmentRepository
                .findByPortfolio_IdAndId(portfolioId, adjustmentId)
                .filter(candidate -> candidate.getStatus() == PortfolioBrokerHoldingAdjustmentStatus.ACTIVE)
                .orElseThrow(() -> new PortfolioBrokerHoldingAdjustmentNotFoundException(
                        "취소할 수 있는 잔고 조정 승인 이력을 찾을 수 없습니다."
                ));

        TradeTransaction transaction = tradeTransactionRepository
                .findByPortfolio_IdAndId(portfolioId, adjustment.getTradeTransactionId())
                .orElseThrow(() -> new IllegalStateException("잔고 조정 매매 기록을 찾을 수 없습니다."));

        List<TradeTransaction> remainingTransactions = new ArrayList<>(
                tradeTransactionRepository.findAllByPortfolio_IdOrderByTradedAtAsc(portfolioId)
        );
        remainingTransactions.removeIf(candidate -> candidate.getId().equals(transaction.getId()));

        holdingCalculator.calculate(remainingTransactions);

        tradeTransactionRepository.delete(transaction);
        adjustment.revoke();
    }

    private Optional<PortfolioBrokerHoldingAdjustment> findExistingForPortfolio(Long portfolioId, Long snapshotItemId) {
        return portfolioBrokerHoldingAdjustmentRepository.findBySnapshotItem_Id(snapshotItemId)
                .filter(adjustment -> adjustment.getPortfolio().getId().equals(portfolioId));
    }

    private Portfolio requirePortfolio(Long memberId, Long portfolioId) {
        return portfolioRepository.findByMember_IdAndId(memberId, portfolioId)
                .orElseThrow(() -> new PortfolioNotFoundException("포트폴리오를 찾을 수 없습니다."));
    }

    public record ApprovalResult(PortfolioBrokerHoldingAdjustment adjustment, boolean created) {
    }
}
