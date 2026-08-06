package com.tradeguide.controller.market;

import com.tradeguide.domain.market.CandleInterval;
import com.tradeguide.domain.market.MarketCandle;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.service.market.MarketHistoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MarketHistoryController.class)
class MarketHistoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MarketHistoryService marketHistoryService;

    @Test
    void getsDailyCandlesWithDefaultOutputSize() throws Exception {
        // Given
        List<MarketCandle> candles = List.of(
                new MarketCandle(
                        Market.US,
                        "AAPL",
                        LocalDate.of(2026, 8, 4),
                        new BigDecimal("200.10"),
                        new BigDecimal("203.00"),
                        new BigDecimal("199.50"),
                        new BigDecimal("202.50"),
                        1_000_000L
                )
        );

        when(marketHistoryService.getCandles(
                Market.US,
                "AAPL",
                CandleInterval.DAILY,
                200
        )).thenReturn(candles);

        // When & Then
        mockMvc.perform(
                        get("/api/markets/US/stocks/AAPL/candles/daily")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].market").value("US"))
                .andExpect(jsonPath("$[0].ticker").value("AAPL"))
                .andExpect(jsonPath("$[0].tradingDate")
                        .value("2026-08-04"))
                .andExpect(jsonPath("$[0].open").value(200.10))
                .andExpect(jsonPath("$[0].high").value(203.00))
                .andExpect(jsonPath("$[0].low").value(199.50))
                .andExpect(jsonPath("$[0].close").value(202.50))
                .andExpect(jsonPath("$[0].volume").value(1_000_000));

        verify(marketHistoryService).getCandles(
                Market.US,
                "AAPL",
                CandleInterval.DAILY,
                200
        );
    }

    @Test
    void getsDailyCandlesWithSpecifiedOutputSize() throws Exception {
        // Given
        when(marketHistoryService.getCandles(
                Market.US,
                "AAPL",
                CandleInterval.DAILY,
                30
        )).thenReturn(List.of());

        // When & Then
        mockMvc.perform(
                        get("/api/markets/US/stocks/AAPL/candles/daily")
                                .param("outputSize", "30")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        verify(marketHistoryService).getCandles(
                Market.US,
                "AAPL",
                CandleInterval.DAILY,
                30
        );
    }

    @Test
    void getsWeeklyCandlesWithSpecifiedOutputSize() throws Exception {
        // Given
        when(marketHistoryService.getCandles(
                Market.US,
                "SOXL",
                CandleInterval.WEEKLY,
                50
        )).thenReturn(List.of());

        // When & Then
        mockMvc.perform(
                        get("/api/markets/US/stocks/SOXL/candles/weekly")
                                .param("outputSize", "50")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        verify(marketHistoryService).getCandles(
                Market.US,
                "SOXL",
                CandleInterval.WEEKLY,
                50
        );
    }
}