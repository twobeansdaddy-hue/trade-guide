package com.tradeguide.service.backtest;

import com.tradeguide.domain.backtest.BacktestAssumptions;
import com.tradeguide.domain.backtest.BacktestResult;
import com.tradeguide.domain.backtest.PortfolioAssetBacktest;
import com.tradeguide.domain.market.CandleInterval;
import com.tradeguide.domain.market.MarketCandle;
import com.tradeguide.domain.portfolio.Portfolio;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioAssetBacktestServiceTest {

    @Mock
    private PortfolioRepository portfolioRepository;
    @Mock
    private AssetProfileRepository assetProfileRepository;
    @Mock
    private MarketHistoryService marketHistoryService;
    @Mock
    private CompletedWeeklyCandleFilter completedWeeklyCandleFilter;
    @Mock
    private CompletedWeeklyCandleCache completedWeeklyCandleCache;
    @Mock
    private WeeklyMaCrossoverBacktestEngine backtestEngine;

    @InjectMocks
    private PortfolioAssetBacktestService portfolioAssetBacktestService;

    @Test
    void throwsIllegalArgumentExceptionWhenInitialCashIsNotPositive() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> portfolioAssetBacktestService.getBacktest(
                        10L,
                        100L,
                        Market.US,
                        "SOXL",
                        BigDecimal.ZERO
                ))
                .withMessage("초기 자산은 0보다 커야 합니다.");

        verifyNoInteractions(portfolioRepository);
    }

    @Test
    void throwsPortfolioNotFoundExceptionWhenPortfolioIsNotOwnedByMember() {
        when(portfolioRepository.findByMember_IdAndId(10L, 100L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> portfolioAssetBacktestService.getBacktest(
                10L,
                100L,
                Market.US,
                "SOXL",
                new BigDecimal("1000")
        )).isInstanceOf(PortfolioNotFoundException.class)
                .hasMessage("포트폴리오를 찾을 수 없습니다.");

        verifyNoInteractions(assetProfileRepository, marketHistoryService, completedWeeklyCandleCache);
    }

    @Test
    void throwsAssetProfileNotFoundExceptionWhenAssetProfileDoesNotExist() {
        when(portfolioRepository.findByMember_IdAndId(10L, 100L))
                .thenReturn(Optional.of(mock(Portfolio.class)));
        when(assetProfileRepository.findByMarketAndTicker(Market.US, "UNKNOWN"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> portfolioAssetBacktestService.getBacktest(
                10L,
                100L,
                Market.US,
                "UNKNOWN",
                new BigDecimal("1000")
        )).isInstanceOf(AssetProfileNotFoundException.class);

        verifyNoInteractions(marketHistoryService, completedWeeklyCandleCache);
    }

    @Test
    void throwsIllegalArgumentExceptionWhenAssetProfileIsNotTrackA() {
        AssetProfile trackBProfile = new AssetProfile(Market.US, "AAPL", InvestmentTrack.TRACK_B);

        when(portfolioRepository.findByMember_IdAndId(10L, 100L))
                .thenReturn(Optional.of(mock(Portfolio.class)));
        when(assetProfileRepository.findByMarketAndTicker(Market.US, "AAPL"))
                .thenReturn(Optional.of(trackBProfile));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> portfolioAssetBacktestService.getBacktest(
                        10L,
                        100L,
                        Market.US,
                        "AAPL",
                        new BigDecimal("1000")
                ))
                .withMessage("TRACK_A 종목만 주봉 교차 백테스트를 실행할 수 있습니다.");

        verifyNoInteractions(marketHistoryService, completedWeeklyCandleCache);
    }

    @Test
    void reusesCompletedWeeklyCandleCacheAndExcludesIncompleteCandlesBeforeRunningBacktest() {
        AssetProfile trackAProfile = new AssetProfile(Market.US, "SOXL", InvestmentTrack.TRACK_A);

        List<MarketCandle> fetchedCandles = List.of(
                candle(LocalDate.of(2026, 3, 6), "200"),
                candle(LocalDate.of(2026, 3, 13), "210")
        );
        List<MarketCandle> completedCandles = List.of(
                candle(LocalDate.of(2026, 3, 6), "200")
        );

        BacktestResult expectedResult = new BacktestResult(
                LocalDate.of(2025, 4, 4),
                LocalDate.of(2026, 3, 6),
                new BigDecimal("1000"),
                new BigDecimal("1200"),
                new BigDecimal("20"),
                new BigDecimal("5"),
                1,
                List.of(),
                BacktestAssumptions.zeroCost()
        );

        when(portfolioRepository.findByMember_IdAndId(10L, 100L))
                .thenReturn(Optional.of(mock(Portfolio.class)));
        when(assetProfileRepository.findByMarketAndTicker(Market.US, "SOXL"))
                .thenReturn(Optional.of(trackAProfile));

        when(completedWeeklyCandleCache.getOrLoad(
                eq(Market.US),
                eq("SOXL"),
                eq(260),
                any()
        )).thenAnswer(invocation -> invocation
                .<Supplier<List<MarketCandle>>>getArgument(3)
                .get()
        );

        when(marketHistoryService.getCandles(
                Market.US,
                "SOXL",
                CandleInterval.WEEKLY,
                260
        )).thenReturn(fetchedCandles);

        when(completedWeeklyCandleFilter.filter(fetchedCandles))
                .thenReturn(completedCandles);

        when(backtestEngine.run(completedCandles, new BigDecimal("1000")))
                .thenReturn(expectedResult);

        PortfolioAssetBacktest backtest = portfolioAssetBacktestService.getBacktest(
                10L,
                100L,
                Market.US,
                "SOXL",
                new BigDecimal("1000")
        );

        assertThat(backtest.getMarket()).isEqualTo(Market.US);
        assertThat(backtest.getTicker()).isEqualTo("SOXL");
        assertThat(backtest.getDataAsOfDate()).isEqualTo(LocalDate.of(2026, 3, 6));
        assertThat(backtest.getResult()).isSameAs(expectedResult);

        verify(completedWeeklyCandleCache).getOrLoad(
                eq(Market.US),
                eq("SOXL"),
                eq(260),
                any()
        );
        verify(marketHistoryService).getCandles(
                Market.US,
                "SOXL",
                CandleInterval.WEEKLY,
                260
        );
        verify(completedWeeklyCandleFilter).filter(fetchedCandles);
        verify(backtestEngine).run(completedCandles, new BigDecimal("1000"));
    }

    private MarketCandle candle(LocalDate tradingDate, String close) {
        BigDecimal price = new BigDecimal(close);

        return new MarketCandle(
                Market.US,
                "SOXL",
                tradingDate,
                price,
                price,
                price,
                price,
                1_000L
        );
    }
}
