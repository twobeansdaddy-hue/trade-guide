package com.tradeguide.service.strategy;

import com.tradeguide.domain.market.MarketDataProvider;
import com.tradeguide.domain.market.CandleInterval;
import com.tradeguide.domain.market.MarketCandle;
import com.tradeguide.domain.portfolio.Portfolio;
import com.tradeguide.domain.strategy.EmptyHoldingsGuidance;
import com.tradeguide.domain.strategy.PremarketGuideItem;
import com.tradeguide.domain.strategy.PremarketGuideItemStatus;
import com.tradeguide.domain.strategy.PremarketGuideScope;
import com.tradeguide.domain.strategy.PremarketGuideSnapshot;
import com.tradeguide.domain.strategy.PremarketGuideCandleEvidence;
import com.tradeguide.domain.strategy.PremarketGuideStatus;
import com.tradeguide.domain.strategy.StrategyGuideBatch;
import com.tradeguide.dto.strategy.PremarketGuideResponse;
import com.tradeguide.exception.PortfolioNotFoundException;
import com.tradeguide.repository.portfolio.PortfolioRepository;
import com.tradeguide.repository.strategy.PremarketGuideSnapshotRepository;
import com.tradeguide.service.asset.AssetDisplayNameResolver;
import com.tradeguide.service.market.UsEquityTradingCalendar;
import com.tradeguide.service.market.CompletedWeeklyCandleCache;
import com.tradeguide.service.market.CompletedWeeklyCandleFilter;
import com.tradeguide.service.market.MarketCandleDigest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Service
public class PremarketGuideService {

    private static final ZoneId MARKET_ZONE = ZoneId.of("America/New_York");

    private final Clock clock;
    private final PortfolioRepository portfolioRepository;
    private final PremarketGuideSnapshotRepository snapshotRepository;
    private final PortfolioStrategyGuideService portfolioStrategyGuideService;
    private final PortfolioCandidateStrategyGuideService portfolioCandidateStrategyGuideService;
    private final UsEquityTradingCalendar tradingCalendar;
    private final AssetDisplayNameResolver displayNameResolver;
    private final CompletedWeeklyCandleCache completedWeeklyCandleCache;
    private final CompletedWeeklyCandleFilter completedWeeklyCandleFilter;
    private final MarketCandleDigest marketCandleDigest;
    private final PortfolioDecisionInputsReader decisionInputsReader;

    public PremarketGuideService(
            Clock clock,
            PortfolioRepository portfolioRepository,
            PremarketGuideSnapshotRepository snapshotRepository,
            PortfolioStrategyGuideService portfolioStrategyGuideService,
            PortfolioCandidateStrategyGuideService portfolioCandidateStrategyGuideService,
            UsEquityTradingCalendar tradingCalendar,
            AssetDisplayNameResolver displayNameResolver,
            CompletedWeeklyCandleCache completedWeeklyCandleCache,
            CompletedWeeklyCandleFilter completedWeeklyCandleFilter,
            MarketCandleDigest marketCandleDigest,
            PortfolioDecisionInputsReader decisionInputsReader
    ) {
        this.decisionInputsReader = decisionInputsReader;
        this.clock = clock;
        this.portfolioRepository = portfolioRepository;
        this.snapshotRepository = snapshotRepository;
        this.portfolioStrategyGuideService = portfolioStrategyGuideService;
        this.portfolioCandidateStrategyGuideService = portfolioCandidateStrategyGuideService;
        this.tradingCalendar = tradingCalendar;
        this.displayNameResolver = displayNameResolver;
        this.completedWeeklyCandleCache = completedWeeklyCandleCache;
        this.completedWeeklyCandleFilter = completedWeeklyCandleFilter;
        this.marketCandleDigest = marketCandleDigest;
    }

    @Transactional
    public PremarketGuideResponse generateToday(
            Long memberId,
            Long portfolioId,
            boolean force
    ) {
        Portfolio portfolio = portfolioRepository.findByMember_IdAndId(memberId, portfolioId)
                .orElseThrow(() -> new PortfolioNotFoundException("포트폴리오를 찾을 수 없습니다."));
        LocalDate guideDate = currentGuideDate();
        LocalDateTime generatedAt = LocalDateTime.now(clock);

        PremarketGuideSnapshot snapshot = snapshotRepository
                .findByPortfolio_IdAndGuideDate(portfolioId, guideDate)
                .orElseGet(() -> new PremarketGuideSnapshot(portfolio, guideDate, generatedAt));

        if (!force && snapshot.getId() != null) {
            return PremarketGuideResponse.from(snapshot, displayNameResolver);
        }

        // 보유·후보 배치가 같은 시점의 포트폴리오 상태를 보도록 한 번만 읽어 공유한다.
        PortfolioDecisionInputs inputs = decisionInputsReader.read(memberId, portfolioId);
        StrategyGuideBatch heldBatch = portfolioStrategyGuideService.getPortfolioStrategyGuides(inputs);
        StrategyGuideBatch candidateBatch = portfolioCandidateStrategyGuideService.getCandidateStrategyGuides(inputs);

        List<PremarketGuideItem> items = new ArrayList<>();
        heldBatch.getGuides().forEach(guide -> items.add(
                PremarketGuideItem.available(PremarketGuideScope.HELD, guide)));
        heldBatch.getUnavailableAssets().forEach(asset -> items.add(
                PremarketGuideItem.unavailable(PremarketGuideScope.HELD, asset)));
        candidateBatch.getGuides().forEach(guide -> items.add(
                PremarketGuideItem.available(PremarketGuideScope.CANDIDATE, guide)));
        candidateBatch.getUnavailableAssets().forEach(asset -> items.add(
                PremarketGuideItem.unavailable(PremarketGuideScope.CANDIDATE, asset)));

        PremarketGuideStatus status = items.stream()
                .anyMatch(item -> item.getStatus() == PremarketGuideItemStatus.UNAVAILABLE)
                ? PremarketGuideStatus.PARTIAL
                : PremarketGuideStatus.COMPLETED;
        EmptyHoldingsGuidance emptyGuidance = heldBatch.getEmptyHoldingsGuidance();
        MarketDataProvider marketDataProvider = portfolio.getMarketDataPreference().getCandleProvider();
        snapshot.replaceResults(status, items, emptyGuidance, generatedAt, marketDataProvider);
        snapshot.replaceCandleEvidence(captureCandleEvidence(items, marketDataProvider, portfolioId));
        // 외부 시세 조회 동안 포트폴리오가 바뀌었는지 저장 직전에 다시 읽어 대조한다. 재시도는 외부 재호출 비용 때문에 하지 않는다.
        boolean changedDuringGeneration = !decisionInputsReader.read(memberId, portfolioId)
                .digests().stateSha256().equals(inputs.digests().stateSha256());
        snapshot.recordUnverifiedInputs(clock.instant(), marketDataProvider, inputs.digests(), changedDuringGeneration);

        return PremarketGuideResponse.from(snapshotRepository.save(snapshot), displayNameResolver);
    }

    private List<PremarketGuideCandleEvidence> captureCandleEvidence(
            List<PremarketGuideItem> items, MarketDataProvider provider, Long portfolioId
    ) {
        List<PremarketGuideCandleEvidence> observations = new ArrayList<>();
        for (PremarketGuideItem item : items) {
            if (item.getStatus() != PremarketGuideItemStatus.AVAILABLE || item.getDataAsOf() == null) {
                continue;
            }
            completedWeeklyCandleCache.findObservation(
                    CompletedWeeklyCandleCache.providerKey(provider, portfolioId), item.getMarket(), item.getTicker(),
                    StrategyGuideService.WEEKLY_CANDLE_OUTPUT_SIZE
            ).ifPresent(cached -> {
                List<MarketCandle> completed = completedWeeklyCandleFilter.filter(cached.candles());
                if (completed.isEmpty() || !completed.get(completed.size() - 1).getTradingDate()
                        .equals(item.getDataAsOf())) {
                    return;
                }
                var sourceReceipt = cached.sourceReceipt();
                observations.add(new PremarketGuideCandleEvidence(
                        item.getScope(), item.getMarket(), item.getTicker(), provider,
                        cached.loadCompletedAt(),
                        marketCandleDigest.sha256(provider, CandleInterval.WEEKLY, completed),
                        completed.size(), item.getDataAsOf(),
                        sourceReceipt.map(receipt -> receipt.pageReceivedAt()).orElse(List.of()),
                        sourceReceipt.map(receipt -> receipt.adjustedRequested()).orElse(null)));
            });
        }
        return observations;
    }

    @Transactional(readOnly = true)
    public PremarketGuideResponse getToday(Long memberId, Long portfolioId) {
        portfolioRepository.findByMember_IdAndId(memberId, portfolioId)
                .orElseThrow(() -> new PortfolioNotFoundException("포트폴리오를 찾을 수 없습니다."));

        LocalDate guideDate = currentGuideDate();
        return snapshotRepository.findByPortfolio_IdAndGuideDate(portfolioId, guideDate)
                .map(snapshot -> PremarketGuideResponse.from(snapshot, displayNameResolver))
                .orElseGet(() -> PremarketGuideResponse.notGenerated(guideDate));
    }

    LocalDate currentGuideDate() {
        return tradingCalendar.nextTradingDayOrSame(
                clock.instant().atZone(MARKET_ZONE).toLocalDate()
        );
    }
}
