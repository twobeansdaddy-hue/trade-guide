package com.tradeguide.service.broker;

import com.tradeguide.domain.broker.BrokerAccount;
import com.tradeguide.domain.broker.BrokerConnection;
import com.tradeguide.domain.broker.BrokerOrderImportCounts;
import com.tradeguide.domain.broker.BrokerOrderImportRun;
import com.tradeguide.domain.broker.BrokerOrderImportRunStatus;
import com.tradeguide.domain.broker.BrokerOrderLedgerLink;
import com.tradeguide.domain.broker.BrokerOrderLedgerLinkStatus;
import com.tradeguide.domain.broker.BrokerOrderRecord;
import com.tradeguide.domain.broker.PortfolioBrokerHoldingSnapshot;
import com.tradeguide.domain.portfolio.Portfolio;
import com.tradeguide.domain.portfolio.PortfolioBrokerLink;
import com.tradeguide.domain.trade.TradeTransaction;
import com.tradeguide.domain.trade.TradeTransactionSource;
import com.tradeguide.exception.PortfolioBrokerLinkNotFoundException;
import com.tradeguide.exception.PortfolioNotFoundException;
import com.tradeguide.repository.asset.AssetListingRepository;
import com.tradeguide.repository.broker.BrokerOrderImportRunRepository;
import com.tradeguide.repository.broker.BrokerOrderLedgerLinkRepository;
import com.tradeguide.repository.broker.PortfolioBrokerHoldingSnapshotRepository;
import com.tradeguide.repository.broker.PortfolioBrokerLinkRepository;
import com.tradeguide.repository.portfolio.PortfolioRepository;
import com.tradeguide.repository.trade.TradeTransactionRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 조회가 끝난 주문을 분류·대조해 실행 한 건으로 저장한다.
 *
 * <p>트랜잭션 경계를 {@link BrokerOrderImportService}와 나눈 이유가 둘 있다.
 *
 * <ol>
 *   <li><b>증권사 호출을 트랜잭션 안에 두지 않기 위해서다.</b> 외부 HTTP 응답을 기다리는 동안
 *       DB 커넥션과 트랜잭션을 붙잡고 있으면, 증권사가 느려질 때 커넥션 풀이 먼저 말라붙는다.</li>
 *   <li><b>실패를 남기기 위해서다.</b> 조회가 실패하면 호출 쪽 흐름은 예외로 끝나는데,
 *       그 예외로 트랜잭션이 함께 말려 들어가면 실패 기록까지 사라진다.
 *       {@link Propagation#REQUIRES_NEW}로 실패 기록만 따로 커밋한다.</li>
 * </ol>
 *
 * <p>이 클래스는 매매 원장에 어떤 행도 만들지 않는다. {@code TradeTransaction}은 읽기만 하며,
 * 읽는 목적은 수동 기록 중복 의심 감지와 보유 수량 재구성 두 가지뿐이다.
 */
@Component
public class BrokerOrderImportRunWriter {

    private final PortfolioRepository portfolioRepository;
    private final PortfolioBrokerLinkRepository portfolioBrokerLinkRepository;
    private final BrokerOrderImportRunRepository brokerOrderImportRunRepository;
    private final BrokerOrderLedgerLinkRepository brokerOrderLedgerLinkRepository;
    private final PortfolioBrokerHoldingSnapshotRepository portfolioBrokerHoldingSnapshotRepository;
    private final TradeTransactionRepository tradeTransactionRepository;
    private final AssetListingRepository assetListingRepository;
    private final BrokerOrderStagingClassifier brokerOrderStagingClassifier;
    private final BrokerOrderReconciler brokerOrderReconciler;

    public BrokerOrderImportRunWriter(
            PortfolioRepository portfolioRepository,
            PortfolioBrokerLinkRepository portfolioBrokerLinkRepository,
            BrokerOrderImportRunRepository brokerOrderImportRunRepository,
            BrokerOrderLedgerLinkRepository brokerOrderLedgerLinkRepository,
            PortfolioBrokerHoldingSnapshotRepository portfolioBrokerHoldingSnapshotRepository,
            TradeTransactionRepository tradeTransactionRepository,
            AssetListingRepository assetListingRepository,
            BrokerOrderStagingClassifier brokerOrderStagingClassifier,
            BrokerOrderReconciler brokerOrderReconciler
    ) {
        this.portfolioRepository = portfolioRepository;
        this.portfolioBrokerLinkRepository = portfolioBrokerLinkRepository;
        this.brokerOrderImportRunRepository = brokerOrderImportRunRepository;
        this.brokerOrderLedgerLinkRepository = brokerOrderLedgerLinkRepository;
        this.portfolioBrokerHoldingSnapshotRepository = portfolioBrokerHoldingSnapshotRepository;
        this.tradeTransactionRepository = tradeTransactionRepository;
        this.assetListingRepository = assetListingRepository;
        this.brokerOrderStagingClassifier = brokerOrderStagingClassifier;
        this.brokerOrderReconciler = brokerOrderReconciler;
    }

    @Transactional
    public BrokerOrderImportRun stage(StagingRequest request) {
        Portfolio portfolio = requirePortfolio(request.memberId(), request.portfolioId());
        PortfolioBrokerLink link = requireLink(portfolio, request.brokerConnectionId(), request.brokerAccountId());
        BrokerConnection connection = link.getBrokerConnection();
        BrokerAccount account = link.getBrokerAccount();

        BrokerOrderStagingClassifier.StagingContext context = buildContext(portfolio, account, request.records());
        BrokerOrderStagingClassifier.Result classified =
                brokerOrderStagingClassifier.classify(request.records(), context);

        BrokerOrderImportCounts counts = classified.toCounts(
                request.unsupportedMarketCount(),
                request.unsupportedCurrencyCount(),
                request.unknownEnumCount(),
                request.duplicateFetchCount(),
                request.openOrderCount()
        );

        Optional<PortfolioBrokerHoldingSnapshot> snapshot = portfolioBrokerHoldingSnapshotRepository
                .findFirstByPortfolio_IdOrderBySyncedAtDesc(portfolio.getId());

        BrokerOrderReconciler.Result reconciliation = brokerOrderReconciler.reconcile(
                tradeTransactionRepository.findAllByPortfolio_IdOrderByTradedAtAsc(portfolio.getId()),
                classified.stagedOrders(),
                snapshot.map(this::toSnapshotQuantities).orElse(null)
        );

        BrokerOrderImportRun run = BrokerOrderImportRun.staged(
                portfolio,
                connection,
                account,
                portfolio.getMember(),
                request.requestedOrderedFrom(),
                request.requestedOrderedTo(),
                request.queriedOrderedFrom(),
                request.queriedOrderedTo(),
                request.startedAt(),
                request.finishedAt(),
                counts,
                reconciliation.status(),
                snapshot.map(PortfolioBrokerHoldingSnapshot::getSyncedAt).orElse(null),
                classified.stagedOrders(),
                reconciliation.lines(),
                request.coveredOrderedTo()
        );

        return brokerOrderImportRunRepository.save(run);
    }

    /**
     * 실패한 실행을 별도 트랜잭션으로 남긴다. 호출 쪽은 이 뒤에 예외를 그대로 던지므로
     * 같은 트랜잭션을 쓰면 이 기록도 함께 롤백된다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BrokerOrderImportRun recordFailure(FailureRequest request) {
        Portfolio portfolio = requirePortfolio(request.memberId(), request.portfolioId());
        PortfolioBrokerLink link = requireLink(portfolio, request.brokerConnectionId(), request.brokerAccountId());

        return brokerOrderImportRunRepository.save(BrokerOrderImportRun.failed(
                portfolio,
                link.getBrokerConnection(),
                link.getBrokerAccount(),
                portfolio.getMember(),
                request.requestedOrderedFrom(),
                request.requestedOrderedTo(),
                request.queriedOrderedFrom(),
                request.queriedOrderedTo(),
                request.startedAt(),
                request.finishedAt(),
                request.failureCode(),
                request.providerRequestId()
        ));
    }

    private BrokerOrderStagingClassifier.StagingContext buildContext(
            Portfolio portfolio,
            BrokerAccount account,
            List<BrokerOrderRecord> records
    ) {
        List<BrokerOrderLedgerLink> activeLinks = brokerOrderLedgerLinkRepository
                .findAllByBrokerAccount_IdAndStatus(account.getId(), BrokerOrderLedgerLinkStatus.ACTIVE);

        Set<String> alreadyImportedOrderIds = activeLinks.stream()
                .map(BrokerOrderLedgerLink::getExternalOrderId)
                .collect(Collectors.toUnmodifiableSet());

        return new BrokerOrderStagingClassifier.StagingContext(
                alreadyImportedOrderIds,
                buildKnownFingerprints(account, activeLinks),
                buildManualTrades(portfolio),
                buildDisplayNames(records)
        );
    }

    /**
     * 이미 아는 주문의 지문 → 주문 식별자 맵이다. 두 출처를 합친다.
     *
     * <ul>
     *   <li><b>반영된 항목</b> — 원장에 이미 들어간 주문. 다른 식별자로 같은 내용이 또 오면
     *       그대로 승인했을 때 원장이 중복된다.</li>
     *   <li><b>직전 실행의 항목</b> — 원장 반영 전이라도, 같은 구간을 두 번 조회했을 때 식별자가
     *       바뀌었다면 이 제공자의 주문 식별자를 멱등성 키로 믿을 수 없다는 뜻이다.
     *       원장을 건드리지 않는 이 단계에서 그 사실을 관측하는 것이 대조의 목적 중 하나다.</li>
     * </ul>
     *
     * <p>같은 지문이 양쪽에 있으면 반영된 쪽을 남긴다. 사용자에게 보여 줄 때 "이미 원장에 있는
     * 그 거래"라고 말할 수 있는 쪽이 더 유용하다.
     */
    private Map<String, String> buildKnownFingerprints(BrokerAccount account, List<BrokerOrderLedgerLink> activeLinks) {
        Map<String, String> orderIdByFingerprint = new HashMap<>();

        brokerOrderImportRunRepository
                .findFirstByBrokerAccount_IdAndStatusOrderByStartedAtDesc(
                        account.getId(), BrokerOrderImportRunStatus.STAGED)
                .ifPresent(previousRun -> previousRun.getItems().forEach(item ->
                        orderIdByFingerprint.put(item.getContentFingerprint(), item.getExternalOrderId())));

        activeLinks.stream()
                .map(BrokerOrderLedgerLink::getItem)
                .forEach(item -> orderIdByFingerprint.put(item.getContentFingerprint(), item.getExternalOrderId()));

        return orderIdByFingerprint;
    }

    private List<BrokerOrderStagingClassifier.ManualTrade> buildManualTrades(Portfolio portfolio) {
        return tradeTransactionRepository.findAllByPortfolio_IdOrderByTradedAtAsc(portfolio.getId()).stream()
                .filter(transaction -> transaction.getSource() == TradeTransactionSource.MANUAL)
                .map(this::toManualTrade)
                .toList();
    }

    private BrokerOrderStagingClassifier.ManualTrade toManualTrade(TradeTransaction transaction) {
        return new BrokerOrderStagingClassifier.ManualTrade(
                transaction.getMarket(),
                transaction.getTicker(),
                transaction.getTradeType(),
                transaction.getQuantity(),
                transaction.getTradedAt()
        );
    }

    /**
     * 주문 응답에는 종목명이 없다. 자산 카탈로그에 있으면 채우고, 없으면 종목 코드를 그대로 쓴다.
     * 카탈로그에 없다는 이유로 항목을 버리지 않는다. 체결은 실재하기 때문이다.
     */
    private Map<BrokerOrderStagingClassifier.AssetKey, String> buildDisplayNames(List<BrokerOrderRecord> records) {
        Map<BrokerOrderStagingClassifier.AssetKey, String> displayNames = new LinkedHashMap<>();

        records.stream()
                .map(record -> new BrokerOrderStagingClassifier.AssetKey(record.market(), record.ticker()))
                .distinct()
                .forEach(key -> assetListingRepository.findByMarketAndTicker(key.market(), key.ticker())
                        .ifPresent(listing -> displayNames.put(key, listing.getDisplayName())));

        return displayNames;
    }

    private Map<BrokerOrderReconciler.AssetKey, BigDecimal> toSnapshotQuantities(
            PortfolioBrokerHoldingSnapshot snapshot) {
        Map<BrokerOrderReconciler.AssetKey, BigDecimal> quantities = new LinkedHashMap<>();
        snapshot.getItems().forEach(item -> quantities.put(
                new BrokerOrderReconciler.AssetKey(item.getMarket(), item.getTicker()),
                item.getQuantity()
        ));
        return quantities;
    }

    private Portfolio requirePortfolio(Long memberId, Long portfolioId) {
        return portfolioRepository.findByMember_IdAndId(memberId, portfolioId)
                .orElseThrow(() -> new PortfolioNotFoundException("포트폴리오를 찾을 수 없습니다."));
    }

    /**
     * 조회를 시작할 때 본 연결·계좌가 저장 시점에도 그대로인지 확인한다.
     * 조회 도중 사용자가 링크를 바꿨다면 이 실행이 가져온 주문은 다른 계좌의 것이므로,
     * 엉뚱한 계좌 이력으로 저장하는 대신 실패시킨다.
     */
    private PortfolioBrokerLink requireLink(Portfolio portfolio, Long brokerConnectionId, Long brokerAccountId) {
        PortfolioBrokerLink link = portfolioBrokerLinkRepository.findByPortfolio_Id(portfolio.getId())
                .orElseThrow(() -> new PortfolioBrokerLinkNotFoundException("포트폴리오에 연결된 증권사 계좌가 없습니다."));

        if (!link.getBrokerConnection().getId().equals(brokerConnectionId)
                || !link.getBrokerAccount().getId().equals(brokerAccountId)) {
            throw new IllegalArgumentException("조회 중 연결된 증권사 계좌가 바뀌었습니다. 다시 시도하세요.");
        }
        return link;
    }

    /** 저장에 필요한 값 묶음. 인자 열몇 개를 순서로 넘기면 두 개만 바꿔 껴도 컴파일이 통과한다. */
    public record StagingRequest(
            Long memberId,
            Long portfolioId,
            Long brokerConnectionId,
            Long brokerAccountId,
            LocalDate requestedOrderedFrom,
            LocalDate requestedOrderedTo,
            LocalDate queriedOrderedFrom,
            LocalDate queriedOrderedTo,
            LocalDateTime startedAt,
            LocalDateTime finishedAt,
            List<BrokerOrderRecord> records,
            int unsupportedMarketCount,
            int unsupportedCurrencyCount,
            int unknownEnumCount,
            int duplicateFetchCount,
            int openOrderCount,
            LocalDate coveredOrderedTo
    ) {
    }

    /** 실패 기록에 필요한 값 묶음. 정제된 코드와 불투명한 상관 id 외에는 담지 않는다. */
    public record FailureRequest(
            Long memberId,
            Long portfolioId,
            Long brokerConnectionId,
            Long brokerAccountId,
            LocalDate requestedOrderedFrom,
            LocalDate requestedOrderedTo,
            LocalDate queriedOrderedFrom,
            LocalDate queriedOrderedTo,
            LocalDateTime startedAt,
            LocalDateTime finishedAt,
            String failureCode,
            String providerRequestId
    ) {
    }
}
