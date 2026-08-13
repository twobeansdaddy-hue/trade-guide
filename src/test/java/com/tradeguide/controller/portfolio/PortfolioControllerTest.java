package com.tradeguide.controller.portfolio;

import com.tradeguide.domain.holding.Holding;
import com.tradeguide.domain.portfolio.Portfolio;
import com.tradeguide.domain.strategy.StrategyMetadata;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.domain.valuation.HoldingValuation;
import com.tradeguide.domain.valuation.PortfolioValuation;
import com.tradeguide.domain.risk.HoldingExposure;
import com.tradeguide.service.holding.HoldingService;
import com.tradeguide.service.portfolio.PortfolioService;
import com.tradeguide.service.strategy.PortfolioCandidateStrategyGuideService;
import com.tradeguide.service.strategy.PortfolioStrategyGuideService;
import com.tradeguide.service.valuation.PortfolioValuationService;
import com.tradeguide.service.risk.PortfolioExposureService;
import com.tradeguide.exception.MarketDataRateLimitExceededException;
import com.tradeguide.domain.strategy.AssetStrategyGuide;
import com.tradeguide.domain.strategy.StrategyAction;
import com.tradeguide.domain.strategy.StrategyDecision;
import com.tradeguide.domain.strategy.StrategySignal;
import com.tradeguide.domain.strategy.StrategySignalEvent;
import com.tradeguide.domain.strategy.StrategyTrend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PortfolioController.class)
class PortfolioControllerTest {

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

    @Test
    void getsHoldings() throws Exception {
        List<Holding> holdings = List.of(
                new Holding(
                        Market.US,
                        "AAPL",
                        new BigDecimal("8"),
                        new BigDecimal("100.01")
                )
        );

        when(holdingService.getHoldings(10L, 100L))
                .thenReturn(holdings);

        mockMvc.perform(
                        get("/api/members/10/portfolios/100/holdings")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].market").value("US"))
                .andExpect(jsonPath("$[0].ticker").value("AAPL"))
                .andExpect(jsonPath("$[0].quantity").value(8))
                .andExpect(jsonPath("$[0].averagePurchasePrice")
                        .value(100.01));

        verify(holdingService).getHoldings(10L, 100L);
    }

    @Test
    void returnsBadRequestWhenPortfolioIsNotOwnedByMember()
            throws Exception {
        when(holdingService.getHoldings(777L, 999L))
                .thenThrow(new IllegalArgumentException(
                        "포트폴리오를 찾을 수 없습니다."
                ));

        mockMvc.perform(
                        get("/api/members/777/portfolios/999/holdings")
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("포트폴리오를 찾을 수 없습니다."));
    }

    @Test
    void getsPortfolioValuation() throws Exception {
        HoldingValuation holdingValuation = new HoldingValuation(
                Market.US,
                "AAPL",
                new BigDecimal("10"),
                new BigDecimal("100"),
                new BigDecimal("210.50"),
                new BigDecimal("1000"),
                new BigDecimal("2105"),
                new BigDecimal("1105"),
                new BigDecimal("110.5")
        );
        PortfolioValuation valuation = new PortfolioValuation(
                List.of(holdingValuation),
                new BigDecimal("1000"),
                new BigDecimal("2105"),
                new BigDecimal("1105"),
                new BigDecimal("110.5")
        );

        when(portfolioValuationService.getPortfolioValuation(10L, 100L))
                .thenReturn(valuation);

        mockMvc.perform(
                        get("/api/members/10/portfolios/100/valuation")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.holdingValuations[0].market")
                        .value("US"))
                .andExpect(jsonPath("$.holdingValuations[0].ticker")
                        .value("AAPL"))
                .andExpect(jsonPath("$.holdingValuations[0].currentPrice")
                        .value(210.5))
                .andExpect(jsonPath("$.totalPurchaseAmount").value(1000))
                .andExpect(jsonPath("$.totalMarketValue").value(2105))
                .andExpect(jsonPath("$.totalUnrealizedProfitLoss").value(1105))
                .andExpect(jsonPath("$.totalReturnRate").value(110.5));

        verify(portfolioValuationService)
                .getPortfolioValuation(10L, 100L);
    }

    @Test
    void returnsTooManyRequestsWhenMarketPriceRateLimitIsExceeded()
            throws Exception {
        when(portfolioValuationService.getPortfolioValuation(10L, 100L))
                .thenThrow(new MarketDataRateLimitExceededException(
                        "현재가 조회 요청이 많습니다. 잠시 후 다시 시도해 주세요.",
                        new RuntimeException()
                ));

        mockMvc.perform(
                        get("/api/members/10/portfolios/100/valuation")
                )
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message")
                        .value("현재가 조회 요청이 많습니다. 잠시 후 다시 시도해 주세요."));
    }

    @Test
    void getsPortfolioStrategyGuides() throws Exception {
        StrategySignal signal = new StrategySignal(
                new BigDecimal("25.5"),
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

        StrategyDecision decision = new StrategyDecision(
                StrategyAction.BUY,
                "이번 완료 주봉에서 상승 교차가 발생했습니다.",
                signal
        );

        AssetStrategyGuide strategyGuide = new AssetStrategyGuide(
                Market.US,
                "SOXL",
                decision
        );

        when(portfolioStrategyGuideService.getPortfolioStrategyGuides(
                10L,
                100L
        )).thenReturn(List.of(strategyGuide));

        mockMvc.perform(
                        get("/api/members/10/portfolios/100/strategy-guides")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].market").value("US"))
                .andExpect(jsonPath("$[0].ticker").value("SOXL"))
                .andExpect(jsonPath("$[0].decision.action").value("BUY"))
                .andExpect(jsonPath("$[0].decision.referencePrice").value(25.5))
                .andExpect(jsonPath("$[0].decision.reason").value("이번 완료 주봉에서 상승 교차가 발생했습니다."))
                .andExpect(jsonPath("$[0].decision.metadata.strategyId").value("test-strategy"))
                .andExpect(jsonPath("$[0].decision.metadata.strategyVersion").value("test-v1"))
                .andExpect(jsonPath("$[0].decision.metadata.dataAsOf").value("2026-08-07"))
                .andExpect(jsonPath("$[0].decision.trend").value("ABOVE_LONG_AVERAGE"))
                .andExpect(jsonPath("$[0].decision.weeksSinceCross").value(0));

        verify(portfolioStrategyGuideService)
                .getPortfolioStrategyGuides(10L, 100L);
    }

    @Test
    void getsCandidateStrategyGuides() throws Exception {
        StrategySignal signal = new StrategySignal(
                new BigDecimal("90"),
                "테스트 시장 신호",
                new StrategyMetadata(
                        "test-strategy",
                        "test-v1",
                        LocalDate.of(2026, 8, 10)
                ),
                StrategyTrend.ABOVE_LONG_AVERAGE,
                StrategySignalEvent.NONE,
                2
        );

        StrategyDecision decision = new StrategyDecision(
                StrategyAction.BUY,
                "테스트 후보 판단",
                signal
        );

        AssetStrategyGuide strategyGuide = new AssetStrategyGuide(
                Market.US,
                "TQQQ",
                decision
        );

        when(portfolioCandidateStrategyGuideService
                .getCandidateStrategyGuides(10L, 100L))
                .thenReturn(List.of(strategyGuide));

        mockMvc.perform(
                        get("/api/members/10/portfolios/100/candidate-strategy-guides")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].market").value("US"))
                .andExpect(jsonPath("$[0].ticker").value("TQQQ"))
                .andExpect(jsonPath("$[0].decision.action").value("BUY"))
                .andExpect(jsonPath("$[0].decision.referencePrice").value(90))
                .andExpect(jsonPath("$[0].decision.trend").value("ABOVE_LONG_AVERAGE"))
                .andExpect(jsonPath("$[0].decision.weeksSinceCross").value(2));

        verify(portfolioCandidateStrategyGuideService)
                .getCandidateStrategyGuides(10L, 100L);
    }

    @Test
    void getsPortfolioExposures() throws Exception {
        HoldingExposure exposure = new HoldingExposure(
                Market.US,
                "SOXL",
                new BigDecimal("600"),
                new BigDecimal("30.00")
        );

        when(portfolioExposureService.getExposures(10L, 100L))
                .thenReturn(List.of(exposure));

        mockMvc.perform(get("/api/members/10/portfolios/100/exposures"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].market").value("US"))
                .andExpect(jsonPath("$[0].ticker").value("SOXL"))
                .andExpect(jsonPath("$[0].marketValue").value(600))
                .andExpect(jsonPath("$[0].exposureRate").value(30));

        verify(portfolioExposureService).getExposures(10L, 100L);
    }
}
