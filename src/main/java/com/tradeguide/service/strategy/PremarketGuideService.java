package com.tradeguide.service.strategy;

import com.tradeguide.domain.market.MarketDataProvider;
import com.tradeguide.domain.portfolio.Portfolio;
import com.tradeguide.domain.strategy.EmptyHoldingsGuidance;
import com.tradeguide.domain.strategy.PremarketGuideItem;
import com.tradeguide.domain.strategy.PremarketGuideItemStatus;
import com.tradeguide.domain.strategy.PremarketGuideScope;
import com.tradeguide.domain.strategy.PremarketGuideSnapshot;
import com.tradeguide.domain.strategy.PremarketGuideStatus;
import com.tradeguide.domain.strategy.StrategyGuideBatch;
import com.tradeguide.dto.strategy.PremarketGuideResponse;
import com.tradeguide.exception.PortfolioNotFoundException;
import com.tradeguide.repository.portfolio.PortfolioRepository;
import com.tradeguide.repository.strategy.PremarketGuideSnapshotRepository;
import com.tradeguide.service.market.UsEquityTradingCalendar;
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

    public PremarketGuideService(
            Clock clock,
            PortfolioRepository portfolioRepository,
            PremarketGuideSnapshotRepository snapshotRepository,
            PortfolioStrategyGuideService portfolioStrategyGuideService,
            PortfolioCandidateStrategyGuideService portfolioCandidateStrategyGuideService,
            UsEquityTradingCalendar tradingCalendar
    ) {
        this.clock = clock;
        this.portfolioRepository = portfolioRepository;
        this.snapshotRepository = snapshotRepository;
        this.portfolioStrategyGuideService = portfolioStrategyGuideService;
        this.portfolioCandidateStrategyGuideService = portfolioCandidateStrategyGuideService;
        this.tradingCalendar = tradingCalendar;
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
            return PremarketGuideResponse.from(snapshot);
        }

        StrategyGuideBatch heldBatch = portfolioStrategyGuideService
                .getPortfolioStrategyGuides(memberId, portfolioId);
        StrategyGuideBatch candidateBatch = portfolioCandidateStrategyGuideService
                .getCandidateStrategyGuides(memberId, portfolioId);

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

        return PremarketGuideResponse.from(snapshotRepository.save(snapshot));
    }

    @Transactional(readOnly = true)
    public PremarketGuideResponse getToday(Long memberId, Long portfolioId) {
        portfolioRepository.findByMember_IdAndId(memberId, portfolioId)
                .orElseThrow(() -> new PortfolioNotFoundException("포트폴리오를 찾을 수 없습니다."));

        LocalDate guideDate = currentGuideDate();
        return snapshotRepository.findByPortfolio_IdAndGuideDate(portfolioId, guideDate)
                .map(PremarketGuideResponse::from)
                .orElseGet(() -> PremarketGuideResponse.notGenerated(guideDate));
    }

    LocalDate currentGuideDate() {
        return tradingCalendar.nextTradingDayOrSame(
                clock.instant().atZone(MARKET_ZONE).toLocalDate()
        );
    }
}
