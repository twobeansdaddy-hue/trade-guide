package com.tradeguide.service.backtest;

import com.tradeguide.domain.backtest.BacktestResult;
import com.tradeguide.domain.backtest.PortfolioAssetBacktest;
import com.tradeguide.domain.market.CandleInterval;
import com.tradeguide.domain.market.MarketCandle;
import com.tradeguide.domain.strategy.AssetProfile;
import com.tradeguide.domain.strategy.InvestmentTrack;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.exception.AssetProfileNotFoundException;
import com.tradeguide.exception.PortfolioNotFoundException;
import com.tradeguide.repository.portfolio.PortfolioRepository;
import com.tradeguide.repository.strategy.AssetProfileRepository;
import com.tradeguide.service.market.CompletedWeeklyCandleCache;
import com.tradeguide.service.market.CompletedWeeklyCandleFilter;
import com.tradeguide.service.market.MarketHistoryService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 포트폴리오 소유권과 TRACK_A 자산 범위를 검증한 뒤, 완료된 주봉 이력으로
 * {@link WeeklyMaCrossoverBacktestEngine}을 실행하는 읽기 전용 조회 서비스다.
 * DB에 결과를 저장하거나 주문을 생성하지 않는다.
 */
@Service
public class PortfolioAssetBacktestService {

    // 10/40주 이동평균 계산과 충분한 교차 이력 확보를 위해 최근 260주(약 5년) 이력을 조회한다.
    private static final int WEEKLY_CANDLE_OUTPUT_SIZE = 260;

    private final PortfolioRepository portfolioRepository;
    private final AssetProfileRepository assetProfileRepository;
    private final MarketHistoryService marketHistoryService;
    private final CompletedWeeklyCandleFilter completedWeeklyCandleFilter;
    private final CompletedWeeklyCandleCache completedWeeklyCandleCache;
    private final WeeklyMaCrossoverBacktestEngine backtestEngine;

    public PortfolioAssetBacktestService(
            PortfolioRepository portfolioRepository,
            AssetProfileRepository assetProfileRepository,
            MarketHistoryService marketHistoryService,
            CompletedWeeklyCandleFilter completedWeeklyCandleFilter,
            CompletedWeeklyCandleCache completedWeeklyCandleCache,
            WeeklyMaCrossoverBacktestEngine backtestEngine
    ) {
        this.portfolioRepository = portfolioRepository;
        this.assetProfileRepository = assetProfileRepository;
        this.marketHistoryService = marketHistoryService;
        this.completedWeeklyCandleFilter = completedWeeklyCandleFilter;
        this.completedWeeklyCandleCache = completedWeeklyCandleCache;
        this.backtestEngine = backtestEngine;
    }

    public PortfolioAssetBacktest getBacktest(
            Long memberId,
            Long portfolioId,
            Market market,
            String ticker,
            BigDecimal initialCash
    ) {
        if (initialCash == null || initialCash.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("초기 자산은 0보다 커야 합니다.");
        }

        requirePortfolioOwnership(memberId, portfolioId);

        AssetProfile assetProfile = assetProfileRepository
                .findByMarketAndTicker(market, ticker)
                .orElseThrow(() -> new AssetProfileNotFoundException(market, ticker));

        if (assetProfile.getInvestmentTrack() != InvestmentTrack.TRACK_A) {
            throw new IllegalArgumentException(
                    "TRACK_A 종목만 주봉 교차 백테스트를 실행할 수 있습니다."
            );
        }

        List<MarketCandle> candles = completedWeeklyCandleCache.getOrLoad(
                market,
                ticker,
                WEEKLY_CANDLE_OUTPUT_SIZE,
                () -> marketHistoryService.getCandles(
                        market,
                        ticker,
                        CandleInterval.WEEKLY,
                        WEEKLY_CANDLE_OUTPUT_SIZE
                )
        );

        List<MarketCandle> completedCandles = completedWeeklyCandleFilter.filter(candles);

        BacktestResult result = backtestEngine.run(completedCandles, initialCash);

        LocalDate dataAsOfDate = completedCandles.get(completedCandles.size() - 1)
                .getTradingDate();

        return new PortfolioAssetBacktest(
                market,
                ticker,
                dataAsOfDate,
                result
        );
    }

    private void requirePortfolioOwnership(Long memberId, Long portfolioId) {
        portfolioRepository
                .findByMember_IdAndId(memberId, portfolioId)
                .orElseThrow(() -> new PortfolioNotFoundException("포트폴리오를 찾을 수 없습니다."));
    }
}
