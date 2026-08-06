package com.tradeguide.controller.market;

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

        when(marketHistoryService.getDailyCandles(
                Market.US,
                "AAPL",
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

        verify(marketHistoryService).getDailyCandles(
                Market.US,
                "AAPL",
                200
        );
    }

    @Test
    void getsDailyCandlesWithSpecifiedOutputSize() throws Exception {
        // Given
        when(marketHistoryService.getDailyCandles(
                Market.US,
                "AAPL",
                30
        )).thenReturn(List.of());

        // When & Then
        mockMvc.perform(
                        get("/api/markets/US/stocks/AAPL/candles/daily")
                                .param("outputSize", "30")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        verify(marketHistoryService).getDailyCandles(
                Market.US,
                "AAPL",
                30
        );
    }
}