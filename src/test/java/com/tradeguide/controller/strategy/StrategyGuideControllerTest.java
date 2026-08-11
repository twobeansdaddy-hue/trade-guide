package com.tradeguide.controller.strategy;

import com.tradeguide.domain.strategy.StrategyAction;
import com.tradeguide.domain.strategy.StrategyDecision;
import com.tradeguide.domain.strategy.StrategyMetadata;
import com.tradeguide.domain.strategy.StrategySignalEvent;
import com.tradeguide.domain.strategy.StrategyTrend;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.exception.AssetProfileNotFoundException;
import com.tradeguide.exception.StaleMarketDataException;
import com.tradeguide.service.strategy.StrategyGuideService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
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
class StrategyGuideControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StrategyGuideService strategyGuideService;

    @Test
    void getsStrategyGuide() throws Exception {
        StrategyDecision strategyDecision = new StrategyDecision(
                StrategyAction.BUY,
                new BigDecimal("120.25"),
                "10주 이동평균이 40주 이동평균을 상향 돌파했습니다.",
                new StrategyMetadata(
                        "test-strategy",
                        "test-v1",
                        LocalDate.of(2026, 8, 7)
                ),
                StrategyTrend.ABOVE_LONG_AVERAGE,
                StrategySignalEvent.CROSS_UP
        );

        when(strategyGuideService.getStrategyDecision(
                Market.US,
                "SOXL"
        )).thenReturn(strategyDecision);

        mockMvc.perform(
                        get("/api/markets/US/stocks/SOXL/strategy-guide")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("BUY"))
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
                .andExpect(jsonPath("$.signalEvent").value("CROSS_UP"));

        verify(strategyGuideService)
                .getStrategyDecision(Market.US, "SOXL");
    }

    @Test
    void returnsNotFoundWhenAssetProfileDoesNotExist() throws Exception {
        when(strategyGuideService.getStrategyDecision(
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
        when(strategyGuideService.getStrategyDecision(
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
                .getStrategyDecision(Market.US, "SOXL");
    }
}
