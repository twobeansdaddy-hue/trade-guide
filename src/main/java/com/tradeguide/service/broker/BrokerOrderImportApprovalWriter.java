package com.tradeguide.service.broker;

import com.tradeguide.domain.broker.BrokerOrderImportItem;
import com.tradeguide.domain.broker.BrokerOrderImportItemOverride;
import com.tradeguide.domain.broker.BrokerOrderImportRun;
import com.tradeguide.domain.broker.BrokerOrderLedgerLink;
import com.tradeguide.domain.broker.BrokerOrderLedgerLinkStatus;
import com.tradeguide.domain.broker.BrokerOrderReconciliationStatus;
import com.tradeguide.domain.broker.BrokerOrderSide;
import com.tradeguide.domain.broker.BrokerOrderStagingStatus;
import com.tradeguide.domain.broker.PortfolioBrokerHoldingImport;
import com.tradeguide.domain.broker.PortfolioBrokerHoldingImportStatus;
import com.tradeguide.domain.member.Member;
import com.tradeguide.domain.portfolio.Portfolio;
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
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 증권사 주문 이력 실행 한 건을 매매 원장에 반영하는 트랜잭션 경계다.
 *
 * <p>{@link BrokerOrderImportApprovalService}와 별도 빈으로 분리한 이유는
 * {@link com.tradeguide.service.broker.PortfolioBrokerHoldingImportWriter}와 같다.
 * PostgreSQL은 트랜잭션 안에서 문장 하나가 실패하면 롤백 전까지 이후 문장을 모두 거부하므로,
 * 동시 승인 요청이 유니크 제약에서 경합해 실패하면 이 트랜잭션 전체가 롤백된 뒤 호출자가
 * 새 트랜잭션에서 최신 상태를 기준으로 다시 시도해야 한다.
 */
@Component
public class BrokerOrderImportApprovalWriter {

    private final PortfolioRepository portfolioRepository;
    private final BrokerOrderImportRunRepository brokerOrderImportRunRepository;
    private final BrokerOrderLedgerLinkRepository brokerOrderLedgerLinkRepository;
    private final BrokerOrderImportItemOverrideRepository brokerOrderImportItemOverrideRepository;
    private final PortfolioBrokerHoldingImportRepository portfolioBrokerHoldingImportRepository;
    private final TradeTransactionRepository tradeTransactionRepository;
    private final AssetListingService assetListingService;
    private final HoldingCalculator holdingCalculator;
    private final Clock clock;

    public BrokerOrderImportApprovalWriter(
            PortfolioRepository portfolioRepository,
            BrokerOrderImportRunRepository brokerOrderImportRunRepository,
            BrokerOrderLedgerLinkRepository brokerOrderLedgerLinkRepository,
            BrokerOrderImportItemOverrideRepository brokerOrderImportItemOverrideRepository,
            PortfolioBrokerHoldingImportRepository portfolioBrokerHoldingImportRepository,
            TradeTransactionRepository tradeTransactionRepository,
            AssetListingService assetListingService,
            HoldingCalculator holdingCalculator,
            Clock clock
    ) {
        this.portfolioRepository = portfolioRepository;
        this.brokerOrderImportRunRepository = brokerOrderImportRunRepository;
        this.brokerOrderLedgerLinkRepository = brokerOrderLedgerLinkRepository;
        this.brokerOrderImportItemOverrideRepository = brokerOrderImportItemOverrideRepository;
        this.portfolioBrokerHoldingImportRepository = portfolioBrokerHoldingImportRepository;
        this.tradeTransactionRepository = tradeTransactionRepository;
        this.assetListingService = assetListingService;
        this.holdingCalculator = holdingCalculator;
        this.clock = clock;
    }

    @Transactional
    public ApprovalResult approve(
            Long memberId, Long portfolioId, Long runId, boolean acknowledgeIncompleteCoverage) {
        Portfolio portfolio = requirePortfolio(memberId, portfolioId);
        BrokerOrderImportRun run = requireRun(portfolio, runId);

        if (run.getReconciliationStatus() == BrokerOrderReconciliationStatus.MISMATCHED
                || run.getReconciliationStatus() == BrokerOrderReconciliationStatus.REPLAY_FAILED) {
            throw new BrokerOrderApprovalConflictException(
                    "이 실행의 보유 수량 대조 결과가 일치하지 않아 승인할 수 없습니다: "
                            + run.getReconciliationStatus(),
                    ApiErrorCode.RECONCILIATION_MISMATCH);
        }

        // 유효 상태가 STAGED인 항목만 반영 후보다. 분류기가 처음부터 STAGED로 본 항목과, 의심
        // 항목 중 사용자가 반영 허용으로 재판정한 항목이 여기에 든다. 재판정이 없거나 제외 유지인
        // 의심 항목은 들어오지 않는다. 스테이징 항목 자체는 바꾸지 않는다.
        Map<Long, BrokerOrderImportItemOverride> overridesByItemId = new HashMap<>();
        brokerOrderImportItemOverrideRepository.findAllByRun_Id(run.getId())
                .forEach(override -> overridesByItemId.put(override.getItem().getId(), override));

        List<BrokerOrderImportItem> candidateItems = run.getItems().stream()
                .filter(item -> BrokerOrderImportItemOverride.effectiveStatusOf(
                        item, overridesByItemId.get(item.getId())) == BrokerOrderStagingStatus.STAGED)
                .toList();
        int overrideAllowedCount = (int) candidateItems.stream()
                .filter(item -> item.getStagingStatus() != BrokerOrderStagingStatus.STAGED)
                .count();

        Instant baselineInstant = resolveBaseline(portfolioId);

        List<BrokerOrderImportItem> eligibleItems = candidateItems.stream()
                .filter(item -> baselineInstant == null || item.getFilledAt().isAfter(baselineInstant))
                .toList();

        if (eligibleItems.isEmpty()) {
            throw baselineInstant == null
                    ? new BrokerOrderApprovalConflictException("이 실행에는 원장에 반영할 주문이 없습니다.")
                    : new BrokerOrderApprovalConflictException(
                            "활성 개시 잔고 기준 시각 이후에 체결된 주문이 없어 반영할 수 없습니다.",
                            ApiErrorCode.BASELINE_EXCLUDED);
        }

        // 불완전 이력(부분 커버) 확인 검사다. 요청 구간을 끝까지 커버했거나 보유 수량 대조가
        // MATCHED면 이력 누락 가능성을 이미 배제할 수 있으므로 건너뛴다. 이미 확인된 실행도
        // 다시 묻지 않는다 — 확인은 실행 단위로 한 번만 성립하는 사실이다.
        boolean coverageAcknowledgementNeeded = !run.isFullyCovered()
                && run.getReconciliationStatus() != BrokerOrderReconciliationStatus.MATCHED
                && run.getCoverageAcknowledgedAt() == null;
        if (coverageAcknowledgementNeeded && !acknowledgeIncompleteCoverage) {
            throw new BrokerOrderApprovalConflictException(
                    "이 실행은 " + run.getCoveredOrderedTo() + "까지만 이력을 가져왔습니다. "
                            + "불완전한 상태로 반영하려면 확인이 필요합니다.",
                    ApiErrorCode.ORDER_IMPORT_COVERAGE_ACKNOWLEDGEMENT_REQUIRED);
        }

        Set<String> alreadyLinkedOrderIds = brokerOrderLedgerLinkRepository
                .findAllByBrokerAccount_IdAndStatus(run.getBrokerAccount().getId(), BrokerOrderLedgerLinkStatus.ACTIVE)
                .stream()
                .map(BrokerOrderLedgerLink::getExternalOrderId)
                .collect(Collectors.toUnmodifiableSet());

        List<BrokerOrderImportItem> itemsToWrite = eligibleItems.stream()
                .filter(item -> !alreadyLinkedOrderIds.contains(item.getExternalOrderId()))
                .toList();

        int baselineExcludedCount = candidateItems.size() - eligibleItems.size();
        int alreadyLinkedCount = eligibleItems.size() - itemsToWrite.size();

        for (BrokerOrderImportItem item : itemsToWrite) {
            try {
                assetListingService.ensureActiveListingFromBrokerSnapshot(
                        item.getMarket(), item.getTicker(), item.getDisplayName());
            } catch (IllegalArgumentException exception) {
                throw new BrokerOrderApprovalConflictException(exception.getMessage());
            }
        }

        // 승인 직전에 전체 원장(기존 반영분 + 이번에 새로 쓸 후보)을 다시 재생해 검증한다.
        // 스테이징 시점의 대조는 그때의 원장 기준이었고, 그 뒤 원장이 바뀌었을 수 있다.
        // 반영 허용으로 재판정한 항목도 똑같이 이 검증을 지난다. 재판정은 검증을 건너뛰는 근거가 아니다.
        List<TradeTransaction> replay = new ArrayList<>(
                tradeTransactionRepository.findAllByPortfolio_IdOrderByTradedAtAsc(portfolioId));
        itemsToWrite.forEach(item -> replay.add(toReplayTransaction(item)));

        try {
            holdingCalculator.calculate(replay);
        } catch (IllegalArgumentException exception) {
            throw new BrokerOrderApprovalConflictException(
                    "승인 직전 재생 검증에 실패했습니다: 초과 매도나 수량 불일치가 있습니다.",
                    ApiErrorCode.REPLAY_VALIDATION_FAILED);
        }

        Instant approvedAtInstant = Instant.now(clock);
        LocalDateTime approvedAt = LocalDateTime.ofInstant(approvedAtInstant, clock.getZone());
        Member approvingMember = portfolio.getMember();

        if (coverageAcknowledgementNeeded) {
            run.acknowledgeCoverageIfNeeded(approvedAt, approvingMember);
        }

        List<BrokerOrderLedgerLink> newLinks = new ArrayList<>();
        for (BrokerOrderImportItem item : itemsToWrite) {
            TradeTransaction transaction = tradeTransactionRepository.save(new TradeTransaction(
                    portfolio,
                    item.getMarket(),
                    item.getTicker(),
                    item.getOrderSide() == BrokerOrderSide.BUY ? TradeType.BUY : TradeType.SELL,
                    item.getFilledQuantity(),
                    item.getAverageFilledPrice(),
                    item.getCommission() == null ? BigDecimal.ZERO : item.getCommission(),
                    item.getFilledAt(),
                    TradeTransactionSource.BROKER_ORDER_HISTORY
            ));

            BrokerOrderLedgerLink link = new BrokerOrderLedgerLink(
                    run.getBrokerAccount(), run, item, transaction.getId(), approvingMember, approvedAt,
                    overridesByItemId.get(item.getId()));
            newLinks.add(brokerOrderLedgerLinkRepository.saveAndFlush(link));
        }

        return new ApprovalResult(
                run.getId(),
                eligibleItems.size(),
                baselineExcludedCount,
                alreadyLinkedCount,
                newLinks.size(),
                approvedAt,
                approvingMember.getId(),
                baselineInstant == null ? null : LocalDateTime.ofInstant(baselineInstant, clock.getZone()),
                run.getCoverageAcknowledgedAt() != null,
                overrideAllowedCount
        );
    }

    /**
     * 재생용 임시 매매 기록이다. 포트폴리오를 붙이지 않는 것은 실수가 아니라 안전장치다.
     * {@link com.tradeguide.service.broker.BrokerOrderReconciler}의 같은 패턴을 따른다.
     */
    private TradeTransaction toReplayTransaction(BrokerOrderImportItem item) {
        return new TradeTransaction(
                null,
                item.getMarket(),
                item.getTicker(),
                item.getOrderSide() == BrokerOrderSide.BUY ? TradeType.BUY : TradeType.SELL,
                item.getFilledQuantity(),
                item.getAverageFilledPrice(),
                item.getCommission() == null ? BigDecimal.ZERO : item.getCommission(),
                item.getFilledAt()
        );
    }

    /**
     * 활성 개시 잔고가 있으면 그 중 가장 최근 승인 시각을 기준점으로 돌려준다. 없으면
     * {@code null}이며, 이때는 어떤 주문도 기준점 때문에 걸러지지 않는다.
     */
    private Instant resolveBaseline(Long portfolioId) {
        return portfolioBrokerHoldingImportRepository
                .findFirstByPortfolio_IdAndStatusOrderByApprovedAtDesc(
                        portfolioId, PortfolioBrokerHoldingImportStatus.ACTIVE)
                .map(PortfolioBrokerHoldingImport::getApprovedAt)
                .map(approvedAt -> approvedAt.atZone(clock.getZone()).toInstant())
                .orElse(null);
    }

    private Portfolio requirePortfolio(Long memberId, Long portfolioId) {
        return portfolioRepository.findByMember_IdAndId(memberId, portfolioId)
                .orElseThrow(() -> new PortfolioNotFoundException("포트폴리오를 찾을 수 없습니다."));
    }

    /** 같은 실행의 재판정 기록과 겹치지 않도록 실행 행을 쓰기 잠금으로 읽는다. */
    private BrokerOrderImportRun requireRun(Portfolio portfolio, Long runId) {
        return brokerOrderImportRunRepository.findWithLockByPortfolioIdAndId(portfolio.getId(), runId)
                .orElseThrow(() -> new BrokerOrderImportNotFoundException(
                        "요청한 주문 이력 가져오기 실행을 찾을 수 없습니다."));
    }

    /**
     * 승인 한 번의 결과다. 안전하게 노출할 수 있는 건수·시각만 담으며, 자격 증명·계좌
     * 일련번호·증권사 원문 오류는 어디에도 담지 않는다.
     *
     * @param overrideAllowedCount 반영 후보 가운데 반영 허용 재판정으로 들어온 의심 항목 수(기준 시각 필터 전)
     */
    public record ApprovalResult(
            Long runId,
            int eligibleCount,
            int baselineExcludedCount,
            int alreadyLinkedCount,
            int writtenCount,
            LocalDateTime approvedAt,
            Long approvedByMemberId,
            LocalDateTime baselineAt,
            boolean coverageAcknowledged,
            int overrideAllowedCount
    ) {
    }
}
