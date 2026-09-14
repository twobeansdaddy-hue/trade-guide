package com.tradeguide.service.broker;

import com.tradeguide.domain.broker.BrokerHoldingComparison;
import com.tradeguide.domain.broker.BrokerHoldingPreview;
import com.tradeguide.domain.broker.BrokerHoldingPreviewItem;
import com.tradeguide.domain.broker.PortfolioBrokerHoldingImport;
import com.tradeguide.domain.broker.PortfolioBrokerHoldingSnapshot;
import com.tradeguide.domain.broker.PortfolioBrokerHoldingSnapshotItem;
import com.tradeguide.domain.portfolio.Portfolio;
import com.tradeguide.domain.trade.TradeTransaction;
import com.tradeguide.domain.trade.TradeTransactionSource;
import com.tradeguide.domain.trade.TradeType;
import com.tradeguide.exception.BrokerHoldingImportConflictException;
import com.tradeguide.exception.BrokerHoldingImportUnprocessableException;
import com.tradeguide.exception.BrokerHoldingSnapshotItemNotFoundException;
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

/**
 * 증권사 개시 잔고 승인 한 건을 실제로 생성하는 트랜잭션 경계다.
 *
 * <p>{@link PortfolioBrokerHoldingImportService}와 별도의 빈으로 분리한 이유는
 * 동시 요청으로 인한 유니크 제약 위반을 안전하게 복구하기 위해서다. PostgreSQL은
 * 트랜잭션 안에서 한 번 실패한 문장이 있으면 롤백 전까지 같은 트랜잭션의 이후 모든
 * 문장을 거부한다. 따라서 제약 위반 이후 같은 트랜잭션 안에서 보정 조회를 시도할
 * 수 없고, 이 클래스의 트랜잭션 전체가 롤백된 뒤 호출자가 새 트랜잭션에서 기존
 * 승인 결과를 다시 조회해야 한다. 같은 빈 안에서 자기 자신을 호출하면 프록시를
 * 거치지 않아 이 트랜잭션 경계가 성립하지 않으므로, 별도 빈으로 분리했다.
 */
@Component
public class PortfolioBrokerHoldingImportWriter {

    private final PortfolioRepository portfolioRepository;
    private final PortfolioBrokerHoldingSnapshotService portfolioBrokerHoldingSnapshotService;
    private final BrokerProviderRegistry brokerProviderRegistry;
    private final AssetListingService assetListingService;
    private final TradeTransactionRepository tradeTransactionRepository;
    private final PortfolioBrokerHoldingImportRepository portfolioBrokerHoldingImportRepository;
    private final Clock clock;

    public PortfolioBrokerHoldingImportWriter(
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

    @Transactional
    public PortfolioBrokerHoldingImport createOpeningBalanceImport(
            Long memberId,
            Long portfolioId,
            Long snapshotItemId,
            Long approvingMemberId
    ) {
        Portfolio portfolio = portfolioRepository.findByMember_IdAndId(memberId, portfolioId)
                .orElseThrow(() -> new PortfolioNotFoundException("포트폴리오를 찾을 수 없습니다."));

        PortfolioBrokerHoldingSnapshot latestSnapshot =
                portfolioBrokerHoldingSnapshotService.getLatestSnapshot(memberId, portfolioId);

        PortfolioBrokerHoldingSnapshotItem item = latestSnapshot.getItems().stream()
                .filter(candidate -> candidate.getId().equals(snapshotItemId))
                .findFirst()
                .orElseThrow(() -> new BrokerHoldingSnapshotItemNotFoundException(
                        "최신 스냅샷에서 해당 증권사 보유 종목을 찾을 수 없습니다."
                ));

        BrokerHoldingPreview comparison =
                portfolioBrokerHoldingSnapshotService.getLatestSnapshotComparison(memberId, portfolioId);
        BrokerHoldingComparison itemComparison = comparison.items().stream()
                .filter(previewItem -> snapshotItemId.equals(previewItem.snapshotItemId()))
                .map(BrokerHoldingPreviewItem::comparison)
                .findFirst()
                .orElseThrow(() -> new BrokerHoldingSnapshotItemNotFoundException(
                        "최신 스냅샷에서 해당 증권사 보유 종목을 찾을 수 없습니다."
                ));

        if (itemComparison != BrokerHoldingComparison.ONLY_IN_BROKER) {
            throw new BrokerHoldingImportConflictException(
                    "증권사에만 있는 종목만 개시 잔고로 반영할 수 있습니다: " + itemComparison
            );
        }

        // 원장 반영 시장 관문이다. 스냅샷에 담기는 시장(제공자가 조회할 수 있는 시장)과
        // 원장에 쓸 수 있는 시장은 다른 집합이며, 자산 카탈로그 등록보다 먼저 막아야
        // 반영할 수 없는 시장의 상장이 새로 만들어지지 않는다.
        brokerProviderRegistry.requireLedgerWritableMarket(
                latestSnapshot.getBrokerConnection().getProvider(), item.getMarket()
        );

        try {
            assetListingService.ensureActiveListingFromBrokerSnapshot(
                    item.getMarket(), item.getTicker(), item.getDisplayName()
            );
        } catch (IllegalArgumentException exception) {
            throw new BrokerHoldingImportUnprocessableException(exception.getMessage());
        }

        Instant approvedAtInstant = Instant.now(clock);
        LocalDateTime approvedAt = LocalDateTime.ofInstant(approvedAtInstant, clock.getZone());

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

        PortfolioBrokerHoldingImport importRecord = new PortfolioBrokerHoldingImport(
                portfolio,
                item,
                latestSnapshot.getSyncedAt(),
                transaction.getId(),
                approvingMemberId,
                approvedAt
        );

        return portfolioBrokerHoldingImportRepository.saveAndFlush(importRecord);
    }
}
