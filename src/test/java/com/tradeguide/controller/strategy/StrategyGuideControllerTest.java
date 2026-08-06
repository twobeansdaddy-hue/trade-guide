package com.tradeguide.controller.strategy;

import com.tradeguide.domain.strategy.StrategyAction;
import com.tradeguide.domain.strategy.StrategyDecision;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.exception.AssetProfileNotFoundException;
import com.tradeguide.service.strategy.StrategyGuideService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

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
                "10주 이동평균이 40주 이동평균을 상향 돌파했습니다."
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
                ));

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
}