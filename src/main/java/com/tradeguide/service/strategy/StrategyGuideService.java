package com.tradeguide.service.strategy;

import com.tradeguide.domain.market.CandleInterval;
import com.tradeguide.domain.market.MarketCandle;
import com.tradeguide.domain.market.MarketDataProvider;
import com.tradeguide.domain.strategy.AssetProfile;
import com.tradeguide.domain.strategy.InvestmentTrack;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.domain.strategy.StrategySignal;
import com.tradeguide.exception.AssetProfileNotFoundException;
import com.tradeguide.repository.strategy.AssetProfileRepository;
import com.tradeguide.repository.portfolio.PortfolioRepository;
import com.tradeguide.exception.PortfolioNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import com.tradeguide.service.market.CompletedWeeklyCandleCache;
import com.tradeguide.service.market.CompletedWeeklyCandleFilter;
import com.tradeguide.service.market.MarketHistoryService;
import com.tradeguide.service.market.WeeklyCandleFreshnessValidator;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class StrategyGuideService {

    // 완료 처리 중인 이번 주 봉을 제외해도, 40주 이동평균 계산에 필요한 이력을 확보한다.
    static final int WEEKLY_CANDLE_OUTPUT_SIZE = 101;

    private final AssetProfileRepository assetProfileRepository;
    private final MarketHistoryService marketHistoryService;
    private final StrategySelector strategySelector;
    private final CompletedWeeklyCandleFilter completedWeeklyCandleFilter;
    private final WeeklyCandleFreshnessValidator weeklyCandleFreshnessValidator;
    private final CompletedWeeklyCandleCache completedWeeklyCandleCache;
    private final PortfolioRepository portfolioRepository;

    @Autowired
    public StrategyGuideService(
            AssetProfileRepository assetProfileRepository,
            MarketHistoryService marketHistoryService,
            StrategySelector strategySelector,
            CompletedWeeklyCandleFilter completedWeeklyCandleFilter,
            WeeklyCandleFreshnessValidator weeklyCandleFreshnessValidator,
            CompletedWeeklyCandleCache completedWeeklyCandleCache,
            PortfolioRepository portfolioRepository
    ) {
        this.assetProfileRepository = assetProfileRepository;
        this.marketHistoryService = marketHistoryService;
        this.strategySelector = strategySelector;
        this.completedWeeklyCandleFilter = completedWeeklyCandleFilter;
        this.weeklyCandleFreshnessValidator = weeklyCandleFreshnessValidator;
        this.completedWeeklyCandleCache = completedWeeklyCandleCache;
        this.portfolioRepository = portfolioRepository;
    }

    /** 기존 단위/통합 테스트와 컨텍스트 없는 공용 전략 엔드포인트를 위한 생성자다. */
    public StrategyGuideService(
            AssetProfileRepository assetProfileRepository,
            MarketHistoryService marketHistoryService,
            StrategySelector strategySelector,
            CompletedWeeklyCandleFilter completedWeeklyCandleFilter,
            WeeklyCandleFreshnessValidator weeklyCandleFreshnessValidator,
            CompletedWeeklyCandleCache completedWeeklyCandleCache
    ) {
        this(
                assetProfileRepository,
                marketHistoryService,
                strategySelector,
                completedWeeklyCandleFilter,
                weeklyCandleFreshnessValidator,
                completedWeeklyCandleCache,
                null
        );
    }

    public StrategySignal getStrategySignal(
            Market market,
            String ticker
    ) {

        AssetProfile assetProfile = assetProfileRepository
                .findByMarketAndTicker(market, ticker)
                .orElseThrow(() -> new AssetProfileNotFoundException(market, ticker));

        return resolveSignal(assetProfile);
    }

    /**
     * 전역 {@link AssetProfile} 조회 없이 명시적 {@link InvestmentTrack}으로 전략 신호를
     * 계산한다. 포트폴리오 범위 재정의처럼 전역 카탈로그에 없거나 전역 값과 다른 트랙을
     * 적용해야 하는 호출자를 위한 진입점이다.
     */
    public StrategySignal getStrategySignal(
            Market market,
            String ticker,
            InvestmentTrack investmentTrack
    ) {
        AssetProfile assetProfile = new AssetProfile(market, ticker, investmentTrack);

        return resolveSignal(assetProfile);
    }

    /** 포트폴리오의 캔들 제공자 설정을 적용해 전략 신호를 계산한다. */
    public StrategySignal getStrategySignal(
            Long portfolioId,
            Market market,
            String ticker
    ) {
        AssetProfile assetProfile = assetProfileRepository
                .findByMarketAndTicker(market, ticker)
                .orElseThrow(() -> new AssetProfileNotFoundException(market, ticker));

        return resolveSignalForPortfolio(portfolioId, assetProfile);
    }

    /** 전역 프로필 없이 포트폴리오 범위에서 명시한 트랙으로 신호를 계산한다. */
    public StrategySignal getStrategySignal(
            Long portfolioId,
            Market market,
            String ticker,
            InvestmentTrack investmentTrack
    ) {
        return resolveSignalForPortfolio(
                portfolioId,
                new AssetProfile(market, ticker, investmentTrack)
        );
    }

    private StrategySignal resolveSignal(AssetProfile assetProfile) {
        List<MarketCandle> candles = completedWeeklyCandleCache.getOrLoad(
                assetProfile.getMarket(),
                assetProfile.getTicker(),
                WEEKLY_CANDLE_OUTPUT_SIZE,
                () -> marketHistoryService.getCandles(
                        assetProfile.getMarket(),
                        assetProfile.getTicker(),
                        CandleInterval.WEEKLY,
                        WEEKLY_CANDLE_OUTPUT_SIZE
                )
        );

        List<MarketCandle> completedCandles = completedWeeklyCandleFilter.filter(candles);

        weeklyCandleFreshnessValidator.validate(completedCandles);

        TradingStrategy strategy = strategySelector.select(
                assetProfile.getInvestmentTrack()
        );

        return strategy.decide(assetProfile, completedCandles);
    }

    private StrategySignal resolveSignalForPortfolio(
            Long portfolioId,
            AssetProfile assetProfile
    ) {
        MarketDataProvider candleProvider = portfolioRepository.findById(portfolioId)
                .map(portfolio -> portfolio.getMarketDataPreference().getCandleProvider())
                .orElseThrow(() -> new PortfolioNotFoundException("포트폴리오를 찾을 수 없습니다."));

        String providerKey = CompletedWeeklyCandleCache.providerKey(candleProvider, portfolioId);
        List<MarketCandle> candles = completedWeeklyCandleCache.getOrLoadObserved(
                providerKey, assetProfile.getMarket(), assetProfile.getTicker(),
                WEEKLY_CANDLE_OUTPUT_SIZE,
                () -> marketHistoryService.getObservedCandles(
                        candleProvider, portfolioId, assetProfile.getMarket(),
                        assetProfile.getTicker(), CandleInterval.WEEKLY,
                        WEEKLY_CANDLE_OUTPUT_SIZE));

        List<MarketCandle> completedCandles = completedWeeklyCandleFilter.filter(candles);
        weeklyCandleFreshnessValidator.validate(completedCandles);

        TradingStrategy strategy = strategySelector.select(assetProfile.getInvestmentTrack());
        return strategy.decide(assetProfile, completedCandles);
    }
}
