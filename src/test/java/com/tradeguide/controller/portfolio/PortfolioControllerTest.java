package com.tradeguide.controller.portfolio;

import com.tradeguide.domain.portfolio.Portfolio;
import com.tradeguide.service.portfolio.PortfolioService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PortfolioController.class)
class PortfolioControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PortfolioService portfolioService;

    @Test
    void createsPortfolio() throws Exception {
        Portfolio portfolio = mock(Portfolio.class);
        when(portfolio.getId()).thenReturn(1L);
        when(portfolio.getName()).thenReturn("US Stocks");

        when(portfolioService.createPortfolio(1L, "US Stocks"))
                .thenReturn(portfolio);

        mockMvc.perform(post("/api/members/1/portfolios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "US Stocks"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("US Stocks"));

        verify(portfolioService).createPortfolio(1L, "US Stocks");
    }

    @Test
    void returnsBadRequestWhenNameIsBlank() throws Exception {
        mockMvc.perform(post("/api/members/1/portfolios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("포트폴리오 이름은 필수입니다."));

        verifyNoInteractions(portfolioService);
    }

    @Test
    void returnsBadRequestWhenMemberDoesNotExist() throws Exception {
        when(portfolioService.createPortfolio(999L, "US Stocks"))
                .thenThrow(new IllegalArgumentException(
                        "회원을 찾을 수 없습니다."
                ));

        mockMvc.perform(post("/api/members/999/portfolios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "US Stocks"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("회원을 찾을 수 없습니다."));
    }
}