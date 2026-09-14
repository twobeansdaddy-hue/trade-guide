package com.tradeguide.service.broker;

import com.tradeguide.domain.broker.BrokerHistoryPage;
import com.tradeguide.domain.broker.BrokerHistoryPageRequest;
import com.tradeguide.domain.broker.BrokerHoldingPreview;
import com.tradeguide.domain.broker.BrokerOrderImportItem;
import com.tradeguide.domain.broker.BrokerOrderImportRun;
import com.tradeguide.domain.broker.BrokerOrderImportRunStatus;
import com.tradeguide.domain.broker.BrokerOrderLedgerLink;
import com.tradeguide.domain.broker.BrokerOrderLedgerLinkStatus;
import com.tradeguide.domain.broker.BrokerProvider;
import com.tradeguide.domain.broker.PortfolioBrokerHoldingImport;
import com.tradeguide.domain.broker.PortfolioBrokerHoldingImportStatus;
import com.tradeguide.domain.broker.PortfolioBrokerHoldingSnapshot;
import com.tradeguide.domain.broker.BrokerReconciliationLineValue;
import com.tradeguide.domain.broker.BrokerReconciliationRun;
import com.tradeguide.domain.portfolio.Portfolio;
import com.tradeguide.exception.BrokerReconciliationRunNotFoundException;
import com.tradeguide.exception.PortfolioNotFoundException;
import com.tradeguide.repository.broker.BrokerOrderImportRunRepository;
import com.tradeguide.repository.broker.BrokerOrderLedgerLinkRepository;
import com.tradeguide.repository.broker.BrokerReconciliationRunRepository;
import com.tradeguide.repository.broker.PortfolioBrokerHoldingImportRepository;
import com.tradeguide.repository.portfolio.PortfolioRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 사용자가 직접 요청하는 원장 정합성 점검이다.
 *
 * <p>이 서비스가 하지 않는 일을 먼저 적는다. 증권사 API를 호출하지 않고, {@code TradeTransaction}이나
 * 파생 {@code Holding}을 만들거나 고치지 않는다. 비교에 쓰는 것은 이미 저장된 최신 보유 종목
 * 스냅샷({@link PortfolioBrokerHoldingSnapshotService})과 현재 매매 원장뿐이다. 최신 상태를
 * 보려면 사용자가 스냅샷을 먼저 갱신해야 한다.
 */
@Service
public class BrokerReconciliationService {

    private static final Sort RUN_SORT = Sort.by(Sort.Order.desc("executedAt"), Sort.Order.desc("id"));

    private final PortfolioRepository portfolioRepository;
    private final PortfolioBrokerHoldingSnapshotService portfolioBrokerHoldingSnapshotService;
    private final PortfolioBrokerHoldingImportRepository portfolioBrokerHoldingImportRepository;
    private final BrokerOrderImportRunRepository brokerOrderImportRunRepository;
    private final BrokerOrderLedgerLinkRepository brokerOrderLedgerLinkRepository;
    private final BrokerReconciliationRunRepository brokerReconciliationRunRepository;
    private final BrokerReconciliationReasonResolver brokerReconciliationReasonResolver;
    private final Clock clock;

    public BrokerReconciliationService(
            PortfolioRepository portfolioRepository,
            PortfolioBrokerHoldingSnapshotService portfolioBrokerHoldingSnapshotService,
            PortfolioBrokerHoldingImportRepository portfolioBrokerHoldingImportRepository,
            BrokerOrderImportRunRepository brokerOrderImportRunRepository,
            BrokerOrderLedgerLinkRepository brokerOrderLedgerLinkRepository,
            BrokerReconciliationRunRepository brokerReconciliationRunRepository,
            BrokerReconciliationReasonResolver brokerReconciliationReasonResolver,
            Clock clock
    ) {
        this.portfolioRepository = portfolioRepository;
        this.portfolioBrokerHoldingSnapshotService = portfolioBrokerHoldingSnapshotService;
        this.portfolioBrokerHoldingImportRepository = portfolioBrokerHoldingImportRepository;
        this.brokerOrderImportRunRepository = brokerOrderImportRunRepository;
        this.brokerOrderLedgerLinkRepository = brokerOrderLedgerLinkRepository;
        this.brokerReconciliationRunRepository = brokerReconciliationRunRepository;
        this.brokerReconciliationReasonResolver = brokerReconciliationReasonResolver;
        this.clock = clock;
    }

    /**
     * 저장된 최신 스냅샷과 현재 원장을 비교해 정합성 점검 한 건을 만든다.
     *
     * <p>저장된 스냅샷이 전혀 없으면 {@code BrokerHoldingSnapshotNotFoundException}으로 거부한다
     * ({@link PortfolioBrokerHoldingSnapshotService#getLatestSnapshot}). 증권사는 호출하지 않는다.
     */
    @Transactional
    public BrokerReconciliationRun createReconciliation(Long memberId, Long portfolioId) {
        Portfolio portfolio = requirePortfolio(memberId, portfolioId);

        PortfolioBrokerHoldingSnapshot snapshot =
                portfolioBrokerHoldingSnapshotService.getLatestSnapshot(memberId, portfolioId);
        BrokerHoldingPreview comparison =
                portfolioBrokerHoldingSnapshotService.getLatestSnapshotComparison(memberId, portfolioId);

        BrokerProvider provider = snapshot.getBrokerConnection().getProvider();
        Long brokerAccountId = snapshot.getBrokerAccount().getId();

        Instant baselineInstant = resolveBaseline(portfolioId);

        Optional<BrokerOrderImportRun> latestStagedRun = brokerOrderImportRunRepository
                .findFirstByBrokerAccount_IdAndStatusOrderByStartedAtDesc(
                        brokerAccountId, BrokerOrderImportRunStatus.STAGED);
        List<BrokerOrderImportItem> latestStagedRunItems = latestStagedRun
                .map(BrokerOrderImportRun::getItems)
                .orElse(List.of());
        boolean hasCoverageGap = latestStagedRun
                .map(run -> run.getQueriedOrderedTo().isBefore(snapshot.getSyncedAt().toLocalDate()))
                .orElse(true);

        Set<String> linkedExternalOrderIds = brokerOrderLedgerLinkRepository
                .findAllByBrokerAccount_IdAndStatus(brokerAccountId, BrokerOrderLedgerLinkStatus.ACTIVE)
                .stream()
                .map(BrokerOrderLedgerLink::getExternalOrderId)
                .collect(Collectors.toUnmodifiableSet());

        List<BrokerReconciliationLineValue> lineValues = comparison.items().stream()
                .map(item -> new BrokerReconciliationLineValue(
                        item.market(),
                        item.ticker(),
                        item.displayName(),
                        item.brokerQuantity(),
                        item.tradeGuideQuantity(),
                        item.comparison(),
                        brokerReconciliationReasonResolver.resolve(
                                item,
                                provider,
                                baselineInstant,
                                latestStagedRunItems,
                                linkedExternalOrderIds,
                                hasCoverageGap
                        )
                ))
                .toList();

        BrokerReconciliationRun run = BrokerReconciliationRun.of(
                portfolio,
                snapshot.getBrokerConnection(),
                snapshot.getBrokerAccount(),
                snapshot.getId(),
                snapshot.getSyncedAt(),
                portfolio.getMember(),
                LocalDateTime.now(clock),
                lineValues
        );

        return brokerReconciliationRunRepository.saveAndFlush(run);
    }

    /**
     * 점검 실행 이력을 최신순 한 페이지만 돌려준다. 실행은 조회할 때마다 쌓이므로 과거 전체를
     * 한 번에 반환하지 않는다.
     */
    @Transactional(readOnly = true)
    public BrokerHistoryPage<BrokerReconciliationRun> getRuns(
            Long memberId,
            Long portfolioId,
            BrokerHistoryPageRequest pageRequest
    ) {
        requirePortfolio(memberId, portfolioId);

        Page<BrokerReconciliationRun> page = brokerReconciliationRunRepository.findAllByPortfolio_Id(
                portfolioId,
                PageRequest.of(pageRequest.page(), pageRequest.size(), RUN_SORT)
        );

        return new BrokerHistoryPage<>(
                page.getContent(),
                pageRequest.page(),
                pageRequest.size(),
                page.getTotalElements(),
                page.hasNext()
        );
    }

    @Transactional(readOnly = true)
    public BrokerReconciliationRun getRun(Long memberId, Long portfolioId, Long runId) {
        requirePortfolio(memberId, portfolioId);

        return brokerReconciliationRunRepository.findByPortfolio_IdAndId(portfolioId, runId)
                .orElseThrow(() -> new BrokerReconciliationRunNotFoundException(
                        "요청한 정합성 점검 실행을 찾을 수 없습니다."));
    }

    /**
     * 활성 개시 잔고가 있으면 그 중 가장 최근 승인 시각을 기준점으로 돌려준다. 없으면
     * {@code null}이며, 이때는 어떤 주문도 기준점 때문에 걸러지지 않는다.
     *
     * <p>{@link BrokerOrderImportApprovalWriter#resolveBaseline}과 같은 계산이다. 여기서
     * 다시 정의한 이유는, 그 클래스는 원장에 쓰는 승인 경계이고 이 클래스는 어떤 것도 쓰지
     * 않는 조회 경계라서 같은 빈에 묶을 이유가 없기 때문이다.
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
}
