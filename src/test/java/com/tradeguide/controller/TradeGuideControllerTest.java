package com.tradeguide.controller;

import com.tradeguide.domain.TradeAction;
import com.tradeguide.domain.TradeGuideRequest;
import com.tradeguide.domain.TradeGuideResult;
import com.tradeguide.service.TradeGuideCalculator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TradeGuideController.class)
public class TradeGuideControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TradeGuideCalculator calculator;

    @Test
    void 정상_요청이면_계산_결과를_응답한다() throws Exception {
        when(calculator.calculate(any(TradeGuideRequest.class)))
                .thenReturn(new TradeGuideResult(
                        20.0,
                        115.0,
                        92.0,
                        TradeAction.TAKE_PROFIT,
                        "익절 매도 고려"
                ));

        mockMvc.perform(post("/api/trade-guide/calculate")
                        .contentType("application/json")
                        .content("""
                                {
                                "averagePrice": 100,
                                "currentPrice": 120,
                                "targetReturnRate": 15,
                                "maximumLossRate": 8
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentReturnRate").value(20.0))
                .andExpect(jsonPath("$.targetSellPrice").value(115.0))
                .andExpect(jsonPath("$.stopLossPrice").value(92.0))
                .andExpect(jsonPath("$.tradeAction").value("TAKE_PROFIT"))
                .andExpect(jsonPath("$.tradeActionMessage").value("익절 매도 고려"));
    }

    @Test
    void 잘못된_요청이면_에러_메시지를_응답한다() throws Exception {

        mockMvc.perform(post("/api/trade-guide/calculate")
                        .contentType("application/json")
                        .content("""
                            {
                              "averagePrice": 0,
                              "currentPrice": 120,
                              "targetReturnRate": 15,
                              "maximumLossRate": 8
                            }
                            """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("평균 매입가는 0보다 커야 합니다."));
    }
}
