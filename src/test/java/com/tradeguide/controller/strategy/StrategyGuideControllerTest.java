package com.tradeguide.controller.strategy;

import com.tradeguide.domain.strategy.StrategyMetadata;
import com.tradeguide.domain.strategy.StrategySignal;
import com.tradeguide.domain.strategy.StrategySignalEvent;
import com.tradeguide.domain.strategy.StrategyTrend;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.exception.AssetProfileNotFoundException;
import com.tradeguide.exception.StaleMarketDataException;
import com.tradeguide.service.strategy.StrategyGuideService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StrategyGuideController.class)
@AutoConfigureMockMvc(addFilters = false)
class StrategyGuideControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StrategyGuideService strategyGuideService;

    @Test
    void getsStrategyGuide() throws Exception {
        StrategySignal strategySignal = new StrategySignal(
                new BigDecimal("120.25"),
                "10주 이동평균이 40주 이동평균을 상향 돌파했습니다.",
                new StrategyMetadata(
                        "test-strategy",
                        "test-v1",
                        LocalDate.of(2026, 8, 7)
                ),
                StrategyTrend.ABOVE_LONG_AVERAGE,
                StrategySignalEvent.CROSS_UP,
                0
        );

        when(strategyGuideService.getStrategySignal(
                Market.US,
                "SOXL"
        )).thenReturn(strategySignal);

        mockMvc.perform(
                        get("/api/markets/US/stocks/SOXL/strategy-guide")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.referencePrice").value(120.25))
                .andExpect(jsonPath("$.reason").value(
                        "10주 이동평균이 40주 이동평균을 상향 돌파했습니다."
                ))
                .andExpect(jsonPath("$.metadata.strategyId")
                        .value("test-strategy"))
                .andExpect(jsonPath("$.metadata.strategyVersion")
                        .value("test-v1"))
                .andExpect(jsonPath("$.metadata.dataAsOf")
                        .value("2026-08-07"))
                .andExpect(jsonPath("$.trend").value("ABOVE_LONG_AVERAGE"))
                .andExpect(jsonPath("$.signalEvent").value("CROSS_UP"))
                .andExpect(jsonPath("$.weeksSinceCross").value(0));

        verify(strategyGuideService)
                .getStrategySignal(Market.US, "SOXL");
    }

    @Test
    void returnsNotFoundWhenAssetProfileDoesNotExist() throws Exception {
        when(strategyGuideService.getStrategySignal(
                Market.US,
                "AAPL"
        )).thenThrow(new AssetProfileNotFoundException(
                Market.US,
                "AAPL"
        ));

        mockMvc.perform(
                        get("/api/markets/US/stocks/AAPL/strategy-guide")
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(
                        "전략 프로필을 찾을 수 없습니다: US / AAPL"
                ));
    }

    @Test
    void returnsBadGatewayWhenWeeklyCandleDataIsStale() throws Exception {
        when(strategyGuideService.getStrategySignal(
                Market.US,
                "SOXL"
        )).thenThrow(new StaleMarketDataException(
                "최신 완료 주봉 데이터가 오래되었습니다."
        ));

        mockMvc.perform(
                        get("/api/markets/US/stocks/SOXL/strategy-guide")
                )
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value(
                        "최신 완료 주봉 데이터가 오래되었습니다."
                ));

        verify(strategyGuideService)
                .getStrategySignal(Market.US, "SOXL");
    }
}
