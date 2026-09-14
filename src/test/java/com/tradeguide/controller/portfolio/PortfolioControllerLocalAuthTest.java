package com.tradeguide.controller.portfolio;

import com.tradeguide.domain.portfolio.Portfolio;
import com.tradeguide.service.auth.AuthIdentityService;
import com.tradeguide.service.auth.MemberAccessService;
import com.tradeguide.service.backtest.PortfolioAssetBacktestService;
import com.tradeguide.service.holding.HoldingService;
import com.tradeguide.service.portfolio.PortfolioService;
import com.tradeguide.service.strategy.PortfolioCandidateStrategyGuideService;
import com.tradeguide.service.strategy.PortfolioStrategyGuideService;
import com.tradeguide.service.risk.PortfolioExposureService;
import com.tradeguide.service.risk.PortfolioRiskAlertService;
import com.tradeguide.service.valuation.PortfolioValuationService;
import com.tradeguide.service.market.MarketDataProviderCatalog;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * tradeguide.auth.enabled의 기본값(false)에서 로컬 개발 흐름이
 * 별도 로그인이나 비밀값 없이 그대로 동작하는지 확인한다.
 */
@WebMvcTest(PortfolioController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(MemberAccessService.class)
class PortfolioControllerLocalAuthTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PortfolioService portfolioService;
    @MockitoBean
    private HoldingService holdingService;
    @MockitoBean
    private PortfolioValuationService portfolioValuationService;
    @MockitoBean
    private PortfolioStrategyGuideService portfolioStrategyGuideService;
    @MockitoBean
    private PortfolioExposureService portfolioExposureService;
    @MockitoBean
    private PortfolioCandidateStrategyGuideService portfolioCandidateStrategyGuideService;
    @MockitoBean
    private PortfolioRiskAlertService portfolioRiskAlertService;
    @MockitoBean
    private AuthIdentityService authIdentityService;
    @MockitoBean
    private MarketDataProviderCatalog marketDataProviderCatalog;
    @MockitoBean
    private PortfolioAssetBacktestService portfolioAssetBacktestService;

    @Test
    void allowsUnauthenticatedLocalRequestWhenAuthenticationIsDisabled() throws Exception {
        Portfolio portfolio = mock(Portfolio.class);
        when(portfolio.getId()).thenReturn(1L);
        when(portfolio.getName()).thenReturn("US Stocks");
        when(portfolioService.getPortfolios(1L)).thenReturn(List.of(portfolio));

        mockMvc.perform(get("/api/members/1/portfolios"))
                .andExpect(status().isOk());

        verifyNoInteractions(authIdentityService);
    }
}
