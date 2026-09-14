package com.tradeguide.service.broker;

import com.tradeguide.domain.broker.BrokerHolding;
import com.tradeguide.domain.broker.BrokerHoldingPreview;
import com.tradeguide.domain.broker.BrokerHoldingPreviewItem;
import com.tradeguide.domain.broker.BrokerHoldingSnapshot;
import com.tradeguide.domain.broker.PortfolioBrokerHoldingSnapshot;
import com.tradeguide.domain.broker.PortfolioBrokerHoldingSnapshotItem;
import com.tradeguide.domain.holding.Holding;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.exception.BrokerHoldingSnapshotNotFoundException;
import com.tradeguide.exception.PortfolioNotFoundException;
import com.tradeguide.repository.broker.PortfolioBrokerHoldingSnapshotRepository;
import com.tradeguide.repository.portfolio.PortfolioRepository;
import com.tradeguide.service.holding.HoldingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 포트폴리오에 연결된 검증된 증권사 계좌의 보유 종목을 갱신해 읽기 전용 스냅샷으로
 * 저장하고, 저장된 최근 스냅샷을 외부 증권사 호출 없이 조회한다.
 *
 * <p>이 서비스는 어떤 경우에도 {@code TradeTransaction}이나 파생 {@code Holding}을
 * 생성·수정하지 않는다.
 */
@Service
public class PortfolioBrokerHoldingSnapshotService {

    private final PortfolioRepository portfolioRepository;
    private final PortfolioBrokerHoldingSnapshotRepository portfolioBrokerHoldingSnapshotRepository;
    private final BrokerHoldingContextLoader brokerHoldingContextLoader;
    private final PortfolioBrokerHoldingSnapshotWriter portfolioBrokerHoldingSnapshotWriter;
    private final HoldingService holdingService;
    private final BrokerHoldingPreviewCalculator brokerHoldingPreviewCalculator;
    private final BrokerDuplicateCallGuard brokerDuplicateCallGuard;
    private final Clock clock;

    public PortfolioBrokerHoldingSnapshotService(
            PortfolioRepository portfolioRepository,
            PortfolioBrokerHoldingSnapshotRepository portfolioBrokerHoldingSnapshotRepository,
            BrokerHoldingContextLoader brokerHoldingContextLoader,
            PortfolioBrokerHoldingSnapshotWriter portfolioBrokerHoldingSnapshotWriter,
            HoldingService holdingService,
            BrokerHoldingPreviewCalculator brokerHoldingPreviewCalculator,
            BrokerDuplicateCallGuard brokerDuplicateCallGuard,
            Clock clock
    ) {
        this.portfolioRepository = portfolioRepository;
        this.portfolioBrokerHoldingSnapshotRepository = portfolioBrokerHoldingSnapshotRepository;
        this.brokerHoldingContextLoader = brokerHoldingContextLoader;
        this.portfolioBrokerHoldingSnapshotWriter = portfolioBrokerHoldingSnapshotWriter;
        this.holdingService = holdingService;
        this.brokerHoldingPreviewCalculator = brokerHoldingPreviewCalculator;
        this.brokerDuplicateCallGuard = brokerDuplicateCallGuard;
        this.clock = clock;
    }

    /**
     * 증권사 보유 종목을 조회해 스냅샷 한 건으로 저장한다.
     *
     * <p><b>이 메서드에는 트랜잭션이 없다.</b> 증권사 호출은 트랜잭션 밖에서 한다. 응답을
     * 기다리는 동안 DB 트랜잭션을 붙잡고 있으면 증권사가 느려질 때 커넥션 풀이 먼저 고갈된다.
     * 읽기는 {@link BrokerHoldingContextLoader}, 쓰기는
     * {@link PortfolioBrokerHoldingSnapshotWriter}가 각자의 트랜잭션에서 처리한다.
     *
     * <p>조회가 실패하면 쓰기 트랜잭션은 시작조차 하지 않으므로 부분 영속화가 남지 않는다.
     * 조회 시각({@code syncedAt})은 <b>조회가 끝난 직후</b>를 찍는다. 저장 시점이 아니라
     * 증권사가 그 값을 보고한 시점이 스냅샷의 기준이기 때문이다.
     */
    public PortfolioBrokerHoldingSnapshot refreshSnapshot(Long memberId, Long portfolioId) {
        brokerDuplicateCallGuard.acquire(portfolioId, BrokerCallType.HOLDING_SNAPSHOT);
        try {
            LocalDateTime lastSyncedAt = portfolioBrokerHoldingSnapshotRepository
                    .findFirstByPortfolio_IdOrderBySyncedAtDesc(portfolioId)
                    .map(PortfolioBrokerHoldingSnapshot::getSyncedAt)
                    .orElse(null);
            brokerDuplicateCallGuard.checkCooldown(BrokerCallType.HOLDING_SNAPSHOT, lastSyncedAt, clock);

            BrokerHoldingContextLoader.HoldingContext context =
                    brokerHoldingContextLoader.load(memberId, portfolioId);

            BrokerHoldingSnapshot fetched = context.fetchHoldings();

            return portfolioBrokerHoldingSnapshotWriter.save(
                    new PortfolioBrokerHoldingSnapshotWriter.SaveRequest(
                            memberId,
                            portfolioId,
                            context.brokerConnectionId(),
                            context.brokerAccountId(),
                            LocalDateTime.now(clock),
                            fetched
                    )
            );
        } finally {
            brokerDuplicateCallGuard.release(portfolioId, BrokerCallType.HOLDING_SNAPSHOT);
        }
    }

    @Transactional(readOnly = true)
    public PortfolioBrokerHoldingSnapshot getLatestSnapshot(Long memberId, Long portfolioId) {
        requirePortfolio(memberId, portfolioId);

        return portfolioBrokerHoldingSnapshotRepository
                .findFirstByPortfolio_IdOrderBySyncedAtDesc(portfolioId)
                .orElseThrow(() -> new BrokerHoldingSnapshotNotFoundException(
                        "저장된 증권사 보유 종목 스냅샷이 없습니다."
                ));
    }

    @Transactional(readOnly = true)
    public BrokerHoldingPreview getLatestSnapshotComparison(Long memberId, Long portfolioId) {
        PortfolioBrokerHoldingSnapshot snapshot = getLatestSnapshot(memberId, portfolioId);
        List<Holding> tradeGuideHoldings = holdingService.getHoldings(memberId, portfolioId);

        List<BrokerHolding> brokerHoldings = snapshot.getItems().stream()
                .map(this::toBrokerHolding)
                .toList();

        Map<AssetIdentity, Long> snapshotItemIdsByAsset = new HashMap<>();
        snapshot.getItems().forEach(item -> snapshotItemIdsByAsset.put(
                new AssetIdentity(item.getMarket(), item.getTicker()),
                item.getId()
        ));

        List<BrokerHoldingPreviewItem> items = brokerHoldingPreviewCalculator
                .compare(brokerHoldings, tradeGuideHoldings)
                .stream()
                .map(item -> item.withSnapshotItemId(
                        snapshotItemIdsByAsset.get(new AssetIdentity(item.market(), item.ticker()))
                ))
                .toList();

        return new BrokerHoldingPreview(
                snapshot.getBrokerConnection().getProvider(),
                snapshot.getBrokerConnection().getId(),
                snapshot.getBrokerAccount().getMaskedAccountNumber(),
                snapshot.getSyncedAt(),
                items,
                snapshot.getUnsupportedMarketCount()
        );
    }

    private record AssetIdentity(Market market, String ticker) {
    }

    private BrokerHolding toBrokerHolding(PortfolioBrokerHoldingSnapshotItem item) {
        return new BrokerHolding(
                item.getMarket(),
                item.getTicker(),
                item.getDisplayName(),
                item.getQuantity(),
                item.getAveragePurchasePrice()
        );
    }

    private void requirePortfolio(Long memberId, Long portfolioId) {
        portfolioRepository.findByMember_IdAndId(memberId, portfolioId)
                .orElseThrow(() -> new PortfolioNotFoundException("포트폴리오를 찾을 수 없습니다."));
    }
}
