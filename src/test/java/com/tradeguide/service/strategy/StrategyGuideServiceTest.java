package com.tradeguide.service.strategy;

import com.tradeguide.domain.market.CandleInterval;
import com.tradeguide.domain.market.MarketCandle;
import com.tradeguide.domain.strategy.AssetProfile;
import com.tradeguide.domain.strategy.InvestmentTrack;
import com.tradeguide.domain.strategy.StrategyAction;
import com.tradeguide.domain.strategy.StrategyDecision;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.exception.AssetProfileNotFoundException;
import com.tradeguide.repository.strategy.AssetProfileRepository;
import com.tradeguide.service.market.MarketHistoryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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

    @InjectMocks
    private StrategyGuideService strategyGuideService;

    @Test
    void getsStrategyDecision() {
        AssetProfile assetProfile = new AssetProfile(
                Market.US,
                "SOXL",
                InvestmentTrack.TRACK_A
        );
        List<MarketCandle> candles = List.of();
        StrategyDecision expected = new StrategyDecision(
                StrategyAction.BUY,
                new BigDecimal("120"),
                "테스트 전략 판단"
        );

        when(assetProfileRepository.findByMarketAndTicker(
                Market.US,
                "SOXL"
        )).thenReturn(Optional.of(assetProfile));

        when(marketHistoryService.getCandles(
                Market.US,
                "SOXL",
                CandleInterval.WEEKLY,
                100
        )).thenReturn(candles);

        when(strategySelector.select(InvestmentTrack.TRACK_A))
                .thenReturn(tradingStrategy);

        when(tradingStrategy.decide(assetProfile, candles))
                .thenReturn(expected);

        StrategyDecision result =
                strategyGuideService.getStrategyDecision(
                        Market.US,
                        "SOXL"
                );

        assertThat(result).isSameAs(expected);

        verify(assetProfileRepository)
                .findByMarketAndTicker(Market.US, "SOXL");
        verify(marketHistoryService).getCandles(
                Market.US,
                "SOXL",
                CandleInterval.WEEKLY,
                100
        );
        verify(strategySelector).select(InvestmentTrack.TRACK_A);
        verify(tradingStrategy).decide(assetProfile, candles);
    }

    @Test
    void throwsExceptionWhenAssetProfileDoesNotExist() {
        when(assetProfileRepository.findByMarketAndTicker(
                Market.US,
                "AAPL"
        )).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                strategyGuideService.getStrategyDecision(
                        Market.US,
                        "AAPL"
                ))
                .isInstanceOf(AssetProfileNotFoundException.class)
                .hasMessage(
                        "전략 프로필을 찾을 수 없습니다: US / AAPL"
                );

        verifyNoInteractions(
                marketHistoryService,
                strategySelector,
                tradingStrategy
        );
    }
}