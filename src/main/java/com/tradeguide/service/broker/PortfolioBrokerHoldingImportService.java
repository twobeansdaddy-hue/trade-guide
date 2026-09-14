package com.tradeguide.service.broker;

import com.tradeguide.domain.broker.BrokerHistoryPage;
import com.tradeguide.domain.broker.BrokerHistoryPageRequest;
import com.tradeguide.domain.broker.BrokerOpeningBalanceBatchResult;
import com.tradeguide.domain.broker.PortfolioBrokerHoldingImport;
import com.tradeguide.domain.broker.PortfolioBrokerHoldingImportStatus;
import com.tradeguide.domain.portfolio.Portfolio;
import com.tradeguide.domain.trade.TradeTransaction;
import com.tradeguide.exception.BrokerHoldingImportConflictException;
import com.tradeguide.exception.PortfolioBrokerHoldingImportNotFoundException;
import com.tradeguide.exception.PortfolioNotFoundException;
import com.tradeguide.repository.broker.PortfolioBrokerHoldingImportRepository;
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
 * 증권사 개시 잔고 승인·조회·취소를 처리한다. 실제 승인 생성은 트랜잭션 경계가
 * 분리된 {@link PortfolioBrokerHoldingImportWriter}(단건)와
 * {@link PortfolioBrokerOpeningBalanceBatchWriter}(일괄)에 위임한다.
 */
@Service
public class PortfolioBrokerHoldingImportService {

    /**
     * 승인 시각만으로는 순서가 정해지지 않는다. 일괄 반영은 여러 건이 같은 승인 시각을 갖기
     * 때문에, id를 동점 기준으로 함께 두어야 페이지 경계에서 항목이 중복되거나 빠지지 않는다.
     */
    private static final Sort HISTORY_SORT = Sort.by(
            Sort.Order.desc("approvedAt"),
            Sort.Order.desc("id")
    );

    private final PortfolioRepository portfolioRepository;
    private final PortfolioBrokerHoldingImportRepository portfolioBrokerHoldingImportRepository;
    private final TradeTransactionRepository tradeTransactionRepository;
    private final PortfolioBrokerHoldingImportWriter portfolioBrokerHoldingImportWriter;
    private final PortfolioBrokerOpeningBalanceBatchWriter portfolioBrokerOpeningBalanceBatchWriter;
    private final HoldingCalculator holdingCalculator;

    public PortfolioBrokerHoldingImportService(
            PortfolioRepository portfolioRepository,
            PortfolioBrokerHoldingImportRepository portfolioBrokerHoldingImportRepository,
            TradeTransactionRepository tradeTransactionRepository,
            PortfolioBrokerHoldingImportWriter portfolioBrokerHoldingImportWriter,
            PortfolioBrokerOpeningBalanceBatchWriter portfolioBrokerOpeningBalanceBatchWriter,
            HoldingCalculator holdingCalculator
    ) {
        this.portfolioRepository = portfolioRepository;
        this.portfolioBrokerHoldingImportRepository = portfolioBrokerHoldingImportRepository;
        this.tradeTransactionRepository = tradeTransactionRepository;
        this.portfolioBrokerHoldingImportWriter = portfolioBrokerHoldingImportWriter;
        this.portfolioBrokerOpeningBalanceBatchWriter = portfolioBrokerOpeningBalanceBatchWriter;
        this.holdingCalculator = holdingCalculator;
    }

    public ApprovalResult approveOpeningBalance(Long memberId, Long portfolioId, Long snapshotItemId) {
        if (snapshotItemId == null) {
            throw new IllegalArgumentException("스냅샷 항목 ID는 필수입니다.");
        }

        requirePortfolio(memberId, portfolioId);

        Optional<PortfolioBrokerHoldingImport> existing = findExistingForPortfolio(portfolioId, snapshotItemId);
        if (existing.isPresent()) {
            return new ApprovalResult(existing.get(), false);
        }

        try {
            PortfolioBrokerHoldingImport created = portfolioBrokerHoldingImportWriter
                    .createOpeningBalanceImport(memberId, portfolioId, snapshotItemId, memberId);
            return new ApprovalResult(created, true);
        } catch (DataIntegrityViolationException exception) {
            return new ApprovalResult(
                    findExistingForPortfolio(portfolioId, snapshotItemId).orElseThrow(() -> exception),
                    false
            );
        }
    }

    /**
     * 최신 저장 스냅샷의 {@code ONLY_IN_BROKER} 종목을 한 번에 개시 잔고로 반영한다.
     *
     * <p>단건 승인과 달리 재시도를 조용히 흡수하지 않는다. 일괄 반영에서 유니크 제약이 걸렸다면
     * 같은 스냅샷 항목을 다른 요청이 동시에 승인했다는 뜻이고, 그 시점의 결과는 이 요청이 판단한
     * 것과 다르다. 절반만 반영된 상태를 만들지 않기 위해 트랜잭션 전체를 롤백하고 충돌로 알린다.
     * 사용자는 스냅샷 비교를 다시 읽고 남은 종목만 반영하면 된다.
     */
    public BrokerOpeningBalanceBatchResult approveOpeningBalanceBatch(
            Long memberId,
            Long portfolioId,
            Long snapshotId
    ) {
        if (snapshotId == null) {
            throw new IllegalArgumentException("스냅샷 ID는 필수입니다.");
        }

        requirePortfolio(memberId, portfolioId);

        try {
            return portfolioBrokerOpeningBalanceBatchWriter
                    .approveAll(memberId, portfolioId, snapshotId, memberId);
        } catch (DataIntegrityViolationException exception) {
            throw new BrokerHoldingImportConflictException(
                    "다른 요청이 같은 종목을 먼저 반영했습니다. 스냅샷 비교를 다시 조회한 뒤 반영하세요."
            );
        }
    }

    /**
     * 감사 이력을 최신순 한 페이지만 읽는다. 이력은 계속 쌓이므로 과거 전체를 한 번에
     * 돌려주지 않는다.
     */
    @Transactional(readOnly = true)
    public BrokerHistoryPage<PortfolioBrokerHoldingImport> getImportHistory(
            Long memberId,
            Long portfolioId,
            BrokerHistoryPageRequest pageRequest
    ) {
        requirePortfolio(memberId, portfolioId);

        Page<PortfolioBrokerHoldingImport> page = portfolioBrokerHoldingImportRepository.findAllByPortfolio_Id(
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
    public void revokeOpeningBalance(Long memberId, Long portfolioId, Long importId) {
        requirePortfolio(memberId, portfolioId);

        PortfolioBrokerHoldingImport importRecord = portfolioBrokerHoldingImportRepository
                .findByPortfolio_IdAndId(portfolioId, importId)
                .filter(candidate -> candidate.getStatus() == PortfolioBrokerHoldingImportStatus.ACTIVE)
                .orElseThrow(() -> new PortfolioBrokerHoldingImportNotFoundException(
                        "취소할 수 있는 개시 잔고 승인 이력을 찾을 수 없습니다."
                ));

        TradeTransaction transaction = tradeTransactionRepository
                .findByPortfolio_IdAndId(portfolioId, importRecord.getTradeTransactionId())
                .orElseThrow(() -> new IllegalStateException("개시 잔고 매매 기록을 찾을 수 없습니다."));

        List<TradeTransaction> remainingTransactions = new ArrayList<>(
                tradeTransactionRepository.findAllByPortfolio_IdOrderByTradedAtAsc(portfolioId)
        );
        remainingTransactions.removeIf(candidate -> candidate.getId().equals(transaction.getId()));

        holdingCalculator.calculate(remainingTransactions);

        tradeTransactionRepository.delete(transaction);
        importRecord.revoke();
    }

    private Optional<PortfolioBrokerHoldingImport> findExistingForPortfolio(Long portfolioId, Long snapshotItemId) {
        return portfolioBrokerHoldingImportRepository.findBySnapshotItem_Id(snapshotItemId)
                .filter(importRecord -> importRecord.getPortfolio().getId().equals(portfolioId));
    }

    private Portfolio requirePortfolio(Long memberId, Long portfolioId) {
        return portfolioRepository.findByMember_IdAndId(memberId, portfolioId)
                .orElseThrow(() -> new PortfolioNotFoundException("포트폴리오를 찾을 수 없습니다."));
    }

    public record ApprovalResult(PortfolioBrokerHoldingImport importRecord, boolean created) {
    }
}
