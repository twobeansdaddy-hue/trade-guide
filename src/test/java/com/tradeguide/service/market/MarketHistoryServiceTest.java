package com.tradeguide.service.market;

import com.tradeguide.domain.market.CandleInterval;
import com.tradeguide.domain.market.MarketCandle;
import com.tradeguide.domain.trade.Market;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketHistoryServiceTest {

    @Mock
    private MarketHistoryProvider marketHistoryProvider;

    @InjectMocks
    private MarketHistoryService marketHistoryService;

    @Test
    void getsDailyCandlesFromProvider() {
        // Given
        List<MarketCandle> expectedCandles = List.of();

        when(marketHistoryProvider.getCandles(
                Market.US,
                "AAPL",
                CandleInterval.DAILY,
                200
        )).thenReturn(expectedCandles);

        // When
        List<MarketCandle> result =
                marketHistoryService.getCandles(
                        Market.US,
                        "AAPL",
                        CandleInterval.DAILY,
                        200
                );

        // Then
        assertThat(result).isSameAs(expectedCandles);

        verify(marketHistoryProvider).getCandles(
                Market.US,
                "AAPL",
                CandleInterval.DAILY,
                200
        );
    }
}