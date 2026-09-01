package com.tradeguide.service.strategy;

import com.tradeguide.domain.market.CandleInterval;
import com.tradeguide.domain.market.MarketCandle;
import com.tradeguide.domain.strategy.*;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.exception.AssetProfileNotFoundException;
import com.tradeguide.exception.StaleMarketDataException;
import com.tradeguide.repository.strategy.AssetProfileRepository;
import com.tradeguide.service.market.CompletedWeeklyCandleFilter;
import com.tradeguide.service.market.MarketHistoryService;
import com.tradeguide.service.market.WeeklyCandleFreshnessValidator;
import com.tradeguide.service.market.CompletedWeeklyCandleCache;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
class StrategyGuideServiceTest {

    @Mock
    private AssetProfileRepository assetProfileRepository;
    @Mock
    private MarketHistoryService marketHistoryService;
    @Mock
    private StrategySelector strategySelector;
    @Mock
    private TradingStrategy tradingStrategy;
    @Mock
    private CompletedWeeklyCandleFilter completedWeeklyCandleFilter;
    @Mock
    private WeeklyCandleFreshnessValidator weeklyCandleFreshnessValidator;
    @Mock
    private CompletedWeeklyCandleCache completedWeeklyCandleCache;

    @InjectMocks
    private StrategyGuideService strategyGuideService;

    @Test
    void getsStrategySignal() {
        AssetProfile assetProfile = new AssetProfile(
                Market.US,
                "SOXL",
                InvestmentTrack.TRACK_A
        );

        List<MarketCandle> fetchedCandles = List.of();
        List<MarketCandle> completedCandles = List.of();

        StrategySignal expected = new StrategySignal(
                new BigDecimal("120"),
                "테스트 전략 신호",
                new StrategyMetadata(
                        "test-strategy",
                        "test-v1",
                        LocalDate.of(2026, 8, 7)
                ),
                StrategyTrend.ABOVE_LONG_AVERAGE,
                StrategySignalEvent.CROSS_UP,
                0
        );

        when(assetProfileRepository.findByMarketAndTicker(
                Market.US,
                "SOXL"
        )).thenReturn(Optional.of(assetProfile));

        when(marketHistoryService.getCandles(
                Market.US,
                "SOXL",
                CandleInterval.WEEKLY,
                101
        )).thenReturn(fetchedCandles);

        when(completedWeeklyCandleCache.getOrLoad(
                eq(Market.US),
                eq("SOXL"),
                eq(101),
                any()
        )).thenAnswer(invocation -> invocation
                .<Supplier<List<MarketCandle>>>getArgument(3)
                .get()
        );

        when(completedWeeklyCandleFilter.filter(fetchedCandles))
                .thenReturn(completedCandles);

        when(strategySelector.select(InvestmentTrack.TRACK_A))
                .thenReturn(tradingStrategy);

        when(tradingStrategy.decide(assetProfile, completedCandles))
                .thenReturn(expected);



        StrategySignal result =
                strategyGuideService.getStrategySignal(
                        Market.US,
                        "SOXL"
                );

        assertThat(result).isSameAs(expected);

        verify(assetProfileRepository)
                .findByMarketAndTicker(Market.US, "SOXL");
        verify(completedWeeklyCandleCache).getOrLoad(
                eq(Market.US),
                eq("SOXL"),
                eq(101),
                any()
        );
        verify(marketHistoryService).getCandles(
                Market.US,
                "SOXL",
                CandleInterval.WEEKLY,
                101
        );
        verify(strategySelector).select(InvestmentTrack.TRACK_A);
        verify(completedWeeklyCandleFilter).filter(fetchedCandles);
        verify(tradingStrategy).decide(assetProfile, completedCandles);
        verify(weeklyCandleFreshnessValidator).validate(completedCandles);
    }

    @Test
    void throwsExceptionWhenAssetProfileDoesNotExist() {
        when(assetProfileRepository.findByMarketAndTicker(
                Market.US,
                "AAPL"
        )).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                strategyGuideService.getStrategySignal(
                        Market.US,
                        "AAPL"
                ))
                .isInstanceOf(AssetProfileNotFoundException.class)
                .hasMessage(
                        "전략 프로필을 찾을 수 없습니다: US / AAPL"
                );

        verifyNoInteractions(
                marketHistoryService,
                completedWeeklyCandleCache,
                completedWeeklyCandleFilter,
                strategySelector,
                tradingStrategy,
                weeklyCandleFreshnessValidator
        );
    }

    @Test
    void doesNotSelectStrategyWhenCompletedCandlesAreStale() {
        AssetProfile assetProfile = new AssetProfile(
                Market.US,
                "SOXL",
                InvestmentTrack.TRACK_A
        );

        List<MarketCandle> fetchedCandles = List.of();
        List<MarketCandle> completedCandles = List.of();

        when(assetProfileRepository.findByMarketAndTicker(
                Market.US,
                "SOXL"
        )).thenReturn(Optional.of(assetProfile));

        when(marketHistoryService.getCandles(
                Market.US,
                "SOXL",
                CandleInterval.WEEKLY,
                101
        )).thenReturn(fetchedCandles);

        when(completedWeeklyCandleCache.getOrLoad(
                eq(Market.US),
                eq("SOXL"),
                eq(101),
                any()
        )).thenAnswer(invocation -> invocation
                .<Supplier<List<MarketCandle>>>getArgument(3)
                .get()
        );

        when(completedWeeklyCandleFilter.filter(fetchedCandles))
                .thenReturn(completedCandles);

        doThrow(new StaleMarketDataException(
                "최신 완료 주봉 데이터가 오래되었습니다."
        )).when(weeklyCandleFreshnessValidator)
                .validate(completedCandles);

        assertThatThrownBy(() ->
                strategyGuideService.getStrategySignal(Market.US, "SOXL")
        )
                .isInstanceOf(StaleMarketDataException.class)
                .hasMessage("최신 완료 주봉 데이터가 오래되었습니다.");

        verifyNoInteractions(strategySelector, tradingStrategy);
        verify(completedWeeklyCandleCache).getOrLoad(
                eq(Market.US),
                eq("SOXL"),
                eq(101),
                any()
        );
        verify(marketHistoryService).getCandles(
                Market.US,
                "SOXL",
                CandleInterval.WEEKLY,
                101
        );
    }
}
