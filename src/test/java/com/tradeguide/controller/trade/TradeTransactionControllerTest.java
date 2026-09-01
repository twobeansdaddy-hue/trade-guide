package com.tradeguide.controller.trade;

import com.tradeguide.domain.trade.Market;
import com.tradeguide.domain.trade.TradeTransaction;
import com.tradeguide.domain.trade.TradeType;
import com.tradeguide.service.trade.TradeTransactionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TradeTransactionController.class)
@AutoConfigureMockMvc(addFilters = false)
class TradeTransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TradeTransactionService tradeTransactionService;

    @Test
    void createsTradeTransaction() throws Exception {
        Instant tradedAt = Instant.parse("2026-08-03T13:30:00Z");

        TradeTransaction transaction = mock(TradeTransaction.class);
        when(transaction.getId()).thenReturn(1L);
        when(transaction.getMarket()).thenReturn(Market.US);
        when(transaction.getTicker()).thenReturn("AAPL");
        when(transaction.getTradeType()).thenReturn(TradeType.BUY);
        when(transaction.getQuantity())
                .thenReturn(new BigDecimal("10"));
        when(transaction.getExecutedPrice())
                .thenReturn(new BigDecimal("100.00"));
        when(transaction.getFee())
                .thenReturn(new BigDecimal("0.10"));
        when(transaction.getTradedAt()).thenReturn(tradedAt);

        when(tradeTransactionService.createTradeTransaction(
                10L,
                100L,
                Market.US,
                "AAPL",
                TradeType.BUY,
                new BigDecimal("10"),
                new BigDecimal("100.00"),
                new BigDecimal("0.10"),
                tradedAt
        )).thenReturn(transaction);

        mockMvc.perform(
                        post("/api/members/10/portfolios/100/transactions")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "market": "US",
                                          "ticker": "AAPL",
                                          "tradeType": "BUY",
                                          "quantity": 10,
                                          "executedPrice": 100.00,
                                          "fee": 0.10,
                                          "tradedAt": "2026-08-03T13:30:00Z"
                                        }
                                        """)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.market").value("US"))
                .andExpect(jsonPath("$.ticker").value("AAPL"))
                .andExpect(jsonPath("$.tradeType").value("BUY"))
                .andExpect(jsonPath("$.quantity").value(10))
                .andExpect(jsonPath("$.executedPrice").value(100.00))
                .andExpect(jsonPath("$.fee").value(0.10));

        verify(tradeTransactionService).createTradeTransaction(
                10L,
                100L,
                Market.US,
                "AAPL",
                TradeType.BUY,
                new BigDecimal("10"),
                new BigDecimal("100.00"),
                new BigDecimal("0.10"),
                tradedAt
        );
    }

    @Test
    void returnsBadRequestWhenQuantityIsZero() throws Exception {
        mockMvc.perform(
                        post("/api/members/10/portfolios/100/transactions")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "market": "US",
                                          "ticker": "AAPL",
                                          "tradeType": "BUY",
                                          "quantity": 0,
                                          "executedPrice": 100.00,
                                          "fee": 0.10,
                                          "tradedAt": "2026-08-03T13:30:00Z"
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("거래 수량은 0보다 커야 합니다."));

        verifyNoInteractions(tradeTransactionService);
    }

    @Test
    void returnsBadRequestWhenSellQuantityExceedsHoldingQuantity()
            throws Exception {
        Instant tradedAt = Instant.parse("2026-08-03T15:30:00Z");

        when(tradeTransactionService.createTradeTransaction(
                10L,
                100L,
                Market.US,
                "AAPL",
                TradeType.SELL,
                new BigDecimal("11"),
                new BigDecimal("120.00"),
                BigDecimal.ZERO,
                tradedAt
        )).thenThrow(new IllegalArgumentException(
                "매도 수량이 보유 수량보다 많습니다."
        ));

        mockMvc.perform(
                        post("/api/members/10/portfolios/100/transactions")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "market": "US",
                                          "ticker": "AAPL",
                                          "tradeType": "SELL",
                                          "quantity": 11,
                                          "executedPrice": 120.00,
                                          "fee": 0,
                                          "tradedAt": "2026-08-03T15:30:00Z"
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("매도 수량이 보유 수량보다 많습니다."));
    }
}
