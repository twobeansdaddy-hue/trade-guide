package com.tradeguide.service.broker;

import com.tradeguide.domain.broker.BrokerHoldingComparison;
import com.tradeguide.domain.broker.BrokerHoldingPreview;
import com.tradeguide.domain.broker.BrokerHoldingPreviewItem;
import com.tradeguide.domain.broker.BrokerOpeningBalanceBatchResult;
import com.tradeguide.domain.broker.BrokerOpeningBalanceSkip;
import com.tradeguide.domain.broker.BrokerOpeningBalanceSkipReason;
import com.tradeguide.domain.broker.BrokerProvider;
import com.tradeguide.domain.broker.PortfolioBrokerHoldingImport;
import com.tradeguide.domain.broker.PortfolioBrokerHoldingImportStatus;
import com.tradeguide.domain.broker.PortfolioBrokerHoldingSnapshot;
import com.tradeguide.domain.broker.PortfolioBrokerHoldingSnapshotItem;
import com.tradeguide.domain.portfolio.Portfolio;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.domain.trade.TradeTransaction;
import com.tradeguide.domain.trade.TradeTransactionSource;
import com.tradeguide.domain.trade.TradeType;
import com.tradeguide.exception.BrokerHoldingImportConflictException;
import com.tradeguide.exception.PortfolioNotFoundException;
import com.tradeguide.repository.broker.PortfolioBrokerHoldingImportRepository;
import com.tradeguide.repository.portfolio.PortfolioRepository;
import com.tradeguide.repository.trade.TradeTransactionRepository;
import com.tradeguide.service.asset.AssetListingService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 최신 저장 스냅샷의 {@code ONLY_IN_BROKER} 종목을 포트폴리오 단위로 한 번에 개시 잔고로
 * 반영하는 트랜잭션 경계다.
 *
 * <p>이 클래스가 하지 않는 일을 먼저 적는다. 증권사 API를 호출하지 않고, 자격 증명을
 * 복호화하지 않고, 외부 종목 검색을 하지 않고, <b>어떤 경우에도 실제 증권사 주문을 내지
 * 않는다</b>. 여기서 쓰는 자산 정보는 이미 저장된 증권사 스냅샷이 보고한 시장·티커·표시명뿐이다.
 * 사전 등록되지 않은 종목은 그 값으로 자산 카탈로그에 등록된다
 * ({@link AssetListingService#ensureActiveListingFromBrokerSnapshot}).
 *
 * <p>일괄 반영은 원자적이다. 한 종목이라도 원장 반영에 실패하면 이 트랜잭션 전체가 롤백되어
 * 절반만 반영된 상태가 남지 않는다. 반대로 "반영할 수 없다고 <b>판정</b>된 종목"은 실패가
 * 아니라 사유가 붙은 제외로 결과에 담긴다. 판정과 실패를 섞으면, 종목 하나가 상장 폐지됐다는
 * 이유로 나머지 전부를 반영하지 못하게 된다.
 *
 * <p>{@link PortfolioBrokerHoldingImportService}와 별도 빈으로 둔 이유는 단건 승인 경로와
 * 같다. 유니크 제약 위반은 PostgreSQL에서 트랜잭션 전체를 무효화하므로, 위반 이후의 보정은
 * 이 트랜잭션이 롤백된 뒤 호출자 쪽에서 이뤄져야 한다.
 */
@Component
public class PortfolioBrokerOpeningBalanceBatchWriter {

    private final PortfolioRepository portfolioRepository;
    private final PortfolioBrokerHoldingSnapshotService portfolioBrokerHoldingSnapshotService;
    private final BrokerProviderRegistry brokerProviderRegistry;
    private final AssetListingService assetListingService;
    private final TradeTransactionRepository tradeTransactionRepository;
    private final PortfolioBrokerHoldingImportRepository portfolioBrokerHoldingImportRepository;
    private final Clock clock;

    public PortfolioBrokerOpeningBalanceBatchWriter(
            PortfolioRepository portfolioRepository,
            PortfolioBrokerHoldingSnapshotService portfolioBrokerHoldingSnapshotService,
            BrokerProviderRegistry brokerProviderRegistry,
            AssetListingService assetListingService,
            TradeTransactionRepository tradeTransactionRepository,
            PortfolioBrokerHoldingImportRepository portfolioBrokerHoldingImportRepository,
            Clock clock
    ) {
        this.portfolioRepository = portfolioRepository;
        this.portfolioBrokerHoldingSnapshotService = portfolioBrokerHoldingSnapshotService;
        this.brokerProviderRegistry = brokerProviderRegistry;
        this.assetListingService = assetListingService;
        this.tradeTransactionRepository = tradeTransactionRepository;
        this.portfolioBrokerHoldingImportRepository = portfolioBrokerHoldingImportRepository;
        this.clock = clock;
    }

    /**
     * @param snapshotId 사용자가 화면에서 검토한 스냅샷이다. 최신 스냅샷과 다르면
     *                   {@code 409}로 막는다. 검토 이후 스냅샷이 갱신됐다면 사용자가 보지 않은
     *                   종목까지 반영될 수 있고, 그것은 명시적 승인이 아니다.
     */
    @Transactional
    public BrokerOpeningBalanceBatchResult approveAll(
            Long memberId,
            Long portfolioId,
            Long snapshotId,
            Long approvingMemberId
    ) {
        Portfolio portfolio = portfolioRepository.findByMember_IdAndId(memberId, portfolioId)
                .orElseThrow(() -> new PortfolioNotFoundException("포트폴리오를 찾을 수 없습니다."));

        PortfolioBrokerHoldingSnapshot latestSnapshot =
                portfolioBrokerHoldingSnapshotService.getLatestSnapshot(memberId, portfolioId);

        if (!latestSnapshot.getId().equals(snapshotId)) {
            throw new BrokerHoldingImportConflictException(
                    "검토한 스냅샷이 최신이 아닙니다. 스냅샷을 다시 조회한 뒤 반영하세요."
            );
        }

        Map<AssetKey, PortfolioBrokerHoldingSnapshotItem> itemsByAsset = new LinkedHashMap<>();
        latestSnapshot.getItems().forEach(item ->
                itemsByAsset.put(toKey(item.getMarket(), item.getTicker()), item));

        BrokerHoldingPreview comparison =
                portfolioBrokerHoldingSnapshotService.getLatestSnapshotComparison(memberId, portfolioId);

        BrokerProvider provider = latestSnapshot.getBrokerConnection().getProvider();
        Map<AssetKey, PortfolioBrokerHoldingImportStatus> existingHistory =
                loadExistingHistory(portfolioId, itemsByAsset.values());
        Set<AssetKey> ledgerAssets = loadLedgerAssets(portfolioId);

        Instant approvedAtInstant = Instant.now(clock);
        LocalDateTime approvedAt = LocalDateTime.ofInstant(approvedAtInstant, clock.getZone());

        List<PortfolioBrokerHoldingImport> approved = new ArrayList<>();
        List<BrokerOpeningBalanceSkip> skipped = new ArrayList<>();

        for (BrokerHoldingPreviewItem previewItem : comparison.items()) {
            AssetKey key = toKey(previewItem.market(), previewItem.ticker());
            PortfolioBrokerHoldingSnapshotItem item = itemsByAsset.get(key);

            if (item == null) {
                // Trade Guide 원장에만 있는 종목이다. 스냅샷 항목이 없으므로 개시 잔고 대상이 아니다.
                continue;
            }

            BrokerOpeningBalanceSkipReason reason = decideSkipReason(
                    previewItem.comparison(), key, provider, existingHistory, ledgerAssets);
            if (reason != null) {
                skipped.add(toSkip(item, reason));
                continue;
            }

            try {
                assetListingService.ensureActiveListingFromBrokerSnapshot(
                        item.getMarket(), item.getTicker(), item.getDisplayName());
            } catch (IllegalArgumentException exception) {
                // 상장 상태가 활성이 아니라는 판정이다. 실패한 SQL이 아니므로 이 트랜잭션은
                // 그대로 살아 있고, 나머지 종목의 반영을 막지 않는다.
                skipped.add(toSkip(item, BrokerOpeningBalanceSkipReason.INACTIVE_LISTING));
                continue;
            }

            approved.add(createImport(portfolio, item, latestSnapshot, approvingMemberId,
                    approvedAtInstant, approvedAt));
        }

        return new BrokerOpeningBalanceBatchResult(
                latestSnapshot.getId(),
                latestSnapshot.getSyncedAt(),
                approved,
                skipped
        );
    }

    /**
     * 제외 사유를 하나로 정한다. 여러 사유가 겹칠 때는 사용자가 다음에 할 행동을 가장 잘
     * 설명하는 쪽을 남긴다. 승인·취소 이력은 사용자가 이미 내린 결정이므로 원장 충돌보다 앞선다.
     */
    private BrokerOpeningBalanceSkipReason decideSkipReason(
            BrokerHoldingComparison comparison,
            AssetKey key,
            BrokerProvider provider,
            Map<AssetKey, PortfolioBrokerHoldingImportStatus> existingHistory,
            Set<AssetKey> ledgerAssets
    ) {
        if (comparison != BrokerHoldingComparison.ONLY_IN_BROKER) {
            return BrokerOpeningBalanceSkipReason.NOT_ONLY_IN_BROKER;
        }
        // 단건 승인과 같은 관문을 쓰되, 일괄에서는 거부가 아니라 사유 있는 제외로 다룬다.
        // 시장 하나 때문에 나머지 종목의 반영을 막지 않는다.
        if (!brokerProviderRegistry.isLedgerWritableMarket(provider, key.market())) {
            return BrokerOpeningBalanceSkipReason.UNSUPPORTED_MARKET;
        }

        PortfolioBrokerHoldingImportStatus status = existingHistory.get(key);
        if (status == PortfolioBrokerHoldingImportStatus.ACTIVE) {
            return BrokerOpeningBalanceSkipReason.ALREADY_APPROVED;
        }
        if (status == PortfolioBrokerHoldingImportStatus.REVOKED) {
            return BrokerOpeningBalanceSkipReason.PREVIOUSLY_REVOKED;
        }

        if (ledgerAssets.contains(key)) {
            return BrokerOpeningBalanceSkipReason.LEDGER_CONFLICT;
        }
        return null;
    }

    private PortfolioBrokerHoldingImport createImport(
            Portfolio portfolio,
            PortfolioBrokerHoldingSnapshotItem item,
            PortfolioBrokerHoldingSnapshot snapshot,
            Long approvingMemberId,
            Instant approvedAtInstant,
            LocalDateTime approvedAt
    ) {
        // 단건 승인과 같은 원장 계약이다. 매수 한 건, 수수료 0, 반영 시각은 승인 시각이며
        // 실제 체결 시각으로 표시하지 않는다.
        TradeTransaction transaction = tradeTransactionRepository.save(new TradeTransaction(
                portfolio,
                item.getMarket(),
                item.getTicker(),
                TradeType.BUY,
                item.getQuantity(),
                item.getAveragePurchasePrice(),
                BigDecimal.ZERO,
                approvedAtInstant,
                TradeTransactionSource.BROKER_OPENING_BALANCE
        ));

        return portfolioBrokerHoldingImportRepository.saveAndFlush(new PortfolioBrokerHoldingImport(
                portfolio,
                item,
                snapshot.getSyncedAt(),
                transaction.getId(),
                approvingMemberId,
                approvedAt
        ));
    }

    /**
     * 후보 종목의 승인·취소 이력을 한 번에 읽는다. 같은 종목에 여러 이력이 있으면
     * {@code ACTIVE}가 우선한다. 활성 이력이 하나라도 있으면 그 종목은 이미 반영된 상태다.
     */
    private Map<AssetKey, PortfolioBrokerHoldingImportStatus> loadExistingHistory(
            Long portfolioId,
            Iterable<PortfolioBrokerHoldingSnapshotItem> items
    ) {
        Set<String> tickers = new LinkedHashSet<>();
        items.forEach(item -> tickers.add(item.getTicker()));
        if (tickers.isEmpty()) {
            return Map.of();
        }

        Map<AssetKey, PortfolioBrokerHoldingImportStatus> statuses = new HashMap<>();
        for (PortfolioBrokerHoldingImport importRecord
                : portfolioBrokerHoldingImportRepository.findAllByPortfolio_IdAndTickerIn(portfolioId, tickers)) {
            AssetKey key = toKey(importRecord.getMarket(), importRecord.getTicker());
            statuses.merge(key, importRecord.getStatus(), (existing, candidate) ->
                    existing == PortfolioBrokerHoldingImportStatus.ACTIVE
                            || candidate == PortfolioBrokerHoldingImportStatus.ACTIVE
                            ? PortfolioBrokerHoldingImportStatus.ACTIVE
                            : existing);
        }
        return statuses;
    }

    /**
     * 원장에 기록이 남아 있는 종목이다. 보유 수량이 0이라 비교에서는 {@code ONLY_IN_BROKER}로
     * 보이지만, 매수·매도 이력이 이미 있는 종목에 개시 잔고를 얹으면 그 이력과 충돌한다.
     */
    private Set<AssetKey> loadLedgerAssets(Long portfolioId) {
        Set<AssetKey> assets = new HashSet<>();
        tradeTransactionRepository.findAllByPortfolio_IdOrderByTradedAtAsc(portfolioId)
                .forEach(transaction -> assets.add(toKey(transaction.getMarket(), transaction.getTicker())));
        return assets;
    }

    private BrokerOpeningBalanceSkip toSkip(
            PortfolioBrokerHoldingSnapshotItem item,
            BrokerOpeningBalanceSkipReason reason
    ) {
        return new BrokerOpeningBalanceSkip(
                item.getId(),
                item.getMarket(),
                item.getTicker(),
                item.getDisplayName(),
                reason
        );
    }

    private AssetKey toKey(Market market, String ticker) {
        return new AssetKey(market, ticker.trim().toUpperCase(Locale.ROOT));
    }

    private record AssetKey(Market market, String ticker) {
    }
}
