package com.tradeguide.controller.portfolio;

import com.tradeguide.domain.backtest.BacktestAssumptions;
import com.tradeguide.domain.backtest.BacktestResult;
import com.tradeguide.domain.backtest.BacktestTradeEvent;
import com.tradeguide.domain.backtest.BacktestTradeType;
import com.tradeguide.domain.backtest.PortfolioAssetBacktest;
import com.tradeguide.domain.holding.Holding;
import com.tradeguide.domain.market.MarketDataProvider;
import com.tradeguide.exception.AssetProfileNotFoundException;
import com.tradeguide.exception.MarketDataProviderNotConfiguredException;
import com.tradeguide.exception.MarketDataUnavailableException;
import com.tradeguide.domain.market.PortfolioMarketDataPreference;
import com.tradeguide.domain.portfolio.Portfolio;
import com.tradeguide.domain.risk.PortfolioRiskPolicy;
import com.tradeguide.domain.strategy.*;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.domain.valuation.HoldingValuation;
import com.tradeguide.domain.valuation.PortfolioValuation;
import com.tradeguide.domain.risk.HoldingExposure;
import com.tradeguide.domain.risk.PortfolioRiskAlert;
import com.tradeguide.exception.PortfolioRiskPolicyNotFoundException;
import com.tradeguide.service.backtest.PortfolioAssetBacktestService;
import com.tradeguide.service.holding.HoldingService;
import com.tradeguide.service.portfolio.PortfolioService;
import com.tradeguide.service.strategy.PortfolioCandidateStrategyGuideService;
import com.tradeguide.service.strategy.PortfolioStrategyGuideService;
import com.tradeguide.service.valuation.PortfolioValuationService;
import com.tradeguide.service.risk.PortfolioExposureService;
import com.tradeguide.service.risk.PortfolioRiskAlertService;
import com.tradeguide.service.auth.MemberAccessService;
import com.tradeguide.service.market.MarketDataProviderCatalog;
import com.tradeguide.exception.MarketDataRateLimitExceededException;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PortfolioController.class)
@AutoConfigureMockMvc(addFilters = false)
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

    @MockitoBean
    private PortfolioRiskAlertService portfolioRiskAlertService;

    @MockitoBean
    private MemberAccessService memberAccessService;

    @MockitoBean
    private MarketDataProviderCatalog marketDataProviderCatalog;

    @MockitoBean
    private PortfolioAssetBacktestService portfolioAssetBacktestService;

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
    void getsPortfolios() throws Exception {
        Portfolio portfolio = mock(Portfolio.class);
        when(portfolio.getId()).thenReturn(1L);
        when(portfolio.getName()).thenReturn("US Stocks");
        when(portfolioService.getPortfolios(1L)).thenReturn(List.of(portfolio));

        mockMvc.perform(get("/api/members/1/portfolios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("US Stocks"));
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
                        .value("현재가 조회 요청이 많습니다. 잠시 후 다시 시도해 주세요."))
                .andExpect(jsonPath("$.code")
                        .value("MARKET_DATA_RATE_LIMIT_EXCEEDED"));
    }

    @Test
    void returnsServiceUnavailableWhenPriceProviderIsNotConfigured()
            throws Exception {
        when(portfolioValuationService.getPortfolioValuation(10L, 100L))
                .thenThrow(new MarketDataProviderNotConfiguredException(
                        MarketDataProvider.TWELVE_DATA,
                        "Twelve Data 시장 데이터 API 키가 서버에 설정되지 않았습니다."
                                + " 서버 환경 변수 TWELVE_DATA_API_KEY를 설정한 뒤 다시 시도해 주세요."
                ));

        mockMvc.perform(
                        get("/api/members/10/portfolios/100/valuation")
                )
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code")
                        .value("MARKET_DATA_PROVIDER_NOT_CONFIGURED"))
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("TWELVE_DATA_API_KEY")));
    }

    @Test
    void returnsBadGatewayWhenPriceProviderCallFails() throws Exception {
        when(portfolioValuationService.getPortfolioValuation(10L, 100L))
                .thenThrow(new MarketDataUnavailableException(
                        "현재가 조회에 실패했습니다."
                ));

        mockMvc.perform(
                        get("/api/members/10/portfolios/100/valuation")
                )
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("MARKET_DATA_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("현재가 조회에 실패했습니다."));
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
        )).thenReturn(new StrategyGuideBatch(
                List.of(strategyGuide),
                List.of()
        ));

        mockMvc.perform(
                        get("/api/members/10/portfolios/100/strategy-guides")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.guides[0].market").value("US"))
                .andExpect(jsonPath("$.guides[0].ticker").value("SOXL"))
                .andExpect(jsonPath("$.guides[0].decision.action").value("BUY"))
                .andExpect(jsonPath("$.guides[0].decision.referencePrice").value(25.5))
                .andExpect(jsonPath("$.guides[0].decision.reason").value("이번 완료 주봉에서 상승 교차가 발생했습니다."))
                .andExpect(jsonPath("$.guides[0].decision.metadata.strategyId").value("test-strategy"))
                .andExpect(jsonPath("$.guides[0].decision.metadata.strategyVersion").value("test-v1"))
                .andExpect(jsonPath("$.guides[0].decision.metadata.dataAsOf").value("2026-08-07"))
                .andExpect(jsonPath("$.guides[0].decision.trend").value("ABOVE_LONG_AVERAGE"))
                .andExpect(jsonPath("$.guides[0].decision.weeksSinceCross").value(0))
                .andExpect(jsonPath("$.guides[0].decision.guidance.entryTimingStatus").value("UNKNOWN"))
                .andExpect(jsonPath("$.guides[0].decision.guidance.stopLossStatus").value("NOT_CONFIGURED"))
                .andExpect(jsonPath("$.guides[0].decision.guidance.stopLossPrice").doesNotExist())
                .andExpect(jsonPath("$.unavailableAssets").isEmpty());

        verify(portfolioStrategyGuideService)
                .getPortfolioStrategyGuides(10L, 100L);
    }

    @Test
    void returnsAvailableAndUnavailableAssetsForPortfolioStrategyGuides()
            throws Exception {
        StrategyGuideBatch batch = new StrategyGuideBatch(
                List.of(),
                List.of(new UnavailableAsset(
                        Market.US,
                        "TQQQ",
                        "시장 데이터 조회에 실패했습니다.",
                        StrategyGuideUnavailableReason.MARKET_DATA_UNAVAILABLE
                ))
        );

        when(portfolioStrategyGuideService.getPortfolioStrategyGuides(
                10L,
                100L
        )).thenReturn(batch);

        mockMvc.perform(
                        get("/api/members/10/portfolios/100/strategy-guides")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.guides").isEmpty())
                .andExpect(jsonPath("$.unavailableAssets[0].market")
                        .value("US"))
                .andExpect(jsonPath("$.unavailableAssets[0].ticker")
                        .value("TQQQ"))
                .andExpect(jsonPath("$.unavailableAssets[0].message")
                        .value("시장 데이터 조회에 실패했습니다."))
                .andExpect(jsonPath("$.unavailableAssets[0].reason")
                        .value("MARKET_DATA_UNAVAILABLE"))
                .andExpect(jsonPath("$.emptyHoldingsGuidance").doesNotExist());

        verify(portfolioStrategyGuideService)
                .getPortfolioStrategyGuides(10L, 100L);
    }

    @Test
    void returnsEmptyHoldingsGuidanceWhenLedgerHoldingsAreEmpty() throws Exception {
        StrategyGuideBatch batch = new StrategyGuideBatch(
                List.of(),
                List.of(),
                new EmptyHoldingsGuidance(
                        EmptyHoldingsReason.BROKER_SNAPSHOT_NOT_REFLECTED,
                        "증권사 보유 종목 스냅샷은 있지만 아직 매매 원장에 반영되지 않아 보유 종목 가이드를 계산할 수 없습니다. "
                                + "개시 잔고 반영 또는 보유 종목 반영을 진행해 주세요."
                )
        );

        when(portfolioStrategyGuideService.getPortfolioStrategyGuides(
                10L,
                100L
        )).thenReturn(batch);

        mockMvc.perform(
                        get("/api/members/10/portfolios/100/strategy-guides")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.guides").isEmpty())
                .andExpect(jsonPath("$.unavailableAssets").isEmpty())
                .andExpect(jsonPath("$.emptyHoldingsGuidance.reason")
                        .value("BROKER_SNAPSHOT_NOT_REFLECTED"))
                .andExpect(jsonPath("$.emptyHoldingsGuidance.message")
                        .value("증권사 보유 종목 스냅샷은 있지만 아직 매매 원장에 반영되지 않아 보유 종목 가이드를 계산할 수 없습니다. "
                                + "개시 잔고 반영 또는 보유 종목 반영을 진행해 주세요."));

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
                .thenReturn(new StrategyGuideBatch(
                        List.of(strategyGuide),
                        List.of()
                ));

        mockMvc.perform(
                        get("/api/members/10/portfolios/100/candidate-strategy-guides")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.guides[0].market").value("US"))
                .andExpect(jsonPath("$.guides[0].ticker").value("TQQQ"))
                .andExpect(jsonPath("$.guides[0].decision.action").value("BUY"))
                .andExpect(jsonPath("$.guides[0].decision.referencePrice").value(90))
                .andExpect(jsonPath("$.guides[0].decision.trend").value("ABOVE_LONG_AVERAGE"))
                .andExpect(jsonPath("$.guides[0].decision.weeksSinceCross").value(2))
                .andExpect(jsonPath("$.unavailableAssets").isEmpty());

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

    @Test
    void updatesPortfolioRiskPolicy() throws Exception {
        PortfolioRiskPolicy riskPolicy = new PortfolioRiskPolicy(
                new BigDecimal("0.025"),
                new BigDecimal("0.125")
        );

        when(portfolioService.updateRiskPolicy(
                10L,
                100L,
                new BigDecimal("0.025"),
                new BigDecimal("0.125")
        )).thenReturn(riskPolicy);

        mockMvc.perform(put(
                        "/api/members/10/portfolios/100/risk-policy"
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "maxLossPerTradeRatio": 0.025,
                              "maxSingleAssetExposureRatio": 0.125
                            }
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxLossPerTradeRatio").value(0.025))
                .andExpect(jsonPath("$.maxSingleAssetExposureRatio").value(0.125));

        verify(portfolioService).updateRiskPolicy(
                10L,
                100L,
                new BigDecimal("0.025"),
                new BigDecimal("0.125")
        );
    }

    @Test
    void returnsBadRequestWhenRiskPolicyRatioIsOutOfRange()
            throws Exception {
        mockMvc.perform(put(
                        "/api/members/10/portfolios/100/risk-policy"
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "maxLossPerTradeRatio": 0,
                              "maxSingleAssetExposureRatio": 0.125
                            }
                            """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("주문당 최대 손실 비율은 0보다 커야 합니다."));

        verifyNoInteractions(portfolioService);
    }

    @Test
    void getsPortfolioRiskPolicy() throws Exception {
        PortfolioRiskPolicy riskPolicy = new PortfolioRiskPolicy(
                new BigDecimal("0.025"),
                new BigDecimal("0.125")
        );

        when(portfolioService.getRiskPolicy(10L, 100L))
                .thenReturn(riskPolicy);

        mockMvc.perform(get(
                        "/api/members/10/portfolios/100/risk-policy"
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxLossPerTradeRatio").value(0.025))
                .andExpect(jsonPath("$.maxSingleAssetExposureRatio")
                        .value(0.125));

        verify(portfolioService).getRiskPolicy(10L, 100L);
    }

    @Test
    void returnsNotFoundWhenPortfolioRiskPolicyIsNotConfigured()
            throws Exception {
        when(portfolioService.getRiskPolicy(10L, 100L))
                .thenThrow(new PortfolioRiskPolicyNotFoundException(
                        "포트폴리오 위험 한도 정책이 설정되지 않았습니다."
                ));

        mockMvc.perform(get(
                        "/api/members/10/portfolios/100/risk-policy"
                ))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message")
                        .value("포트폴리오 위험 한도 정책이 설정되지 않았습니다."));

        verify(portfolioService).getRiskPolicy(10L, 100L);
    }

    @Test
    void getsPortfolioRiskAlerts() throws Exception {
        List<PortfolioRiskAlert> alerts = List.of(
                new PortfolioRiskAlert(
                        Market.US,
                        "SOXL",
                        new BigDecimal("30.00"),
                        new BigDecimal("12.50"),
                        "종목별 최대 노출 비율을 초과했습니다."
                )
        );

        when(portfolioRiskAlertService.getRiskAlerts(10L, 100L))
                .thenReturn(alerts);

        mockMvc.perform(get(
                        "/api/members/10/portfolios/100/risk-alerts"
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].market").value("US"))
                .andExpect(jsonPath("$[0].ticker").value("SOXL"))
                .andExpect(jsonPath("$[0].exposureRate").value(30))
                .andExpect(jsonPath("$[0].maxExposureRate").value(12.5))
                .andExpect(jsonPath("$[0].message")
                        .value("종목별 최대 노출 비율을 초과했습니다."));

        verify(portfolioRiskAlertService).getRiskAlerts(10L, 100L);
    }


    @Test
    void returnsEmptyListWhenNoPortfolioRiskAlertsExist()
            throws Exception {
        when(portfolioRiskAlertService.getRiskAlerts(10L, 100L))
                .thenReturn(List.of());

        mockMvc.perform(get(
                        "/api/members/10/portfolios/100/risk-alerts"
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        verify(portfolioRiskAlertService).getRiskAlerts(10L, 100L);
    }

    @Test
    void getsMarketDataProviders() throws Exception {
        when(marketDataProviderCatalog.getProviders()).thenReturn(List.of(
                MarketDataProvider.TWELVE_DATA,
                MarketDataProvider.TOSS_SECURITIES
        ));

        when(portfolioService.getMarketDataPreference(10L, 100L))
                .thenReturn(PortfolioMarketDataPreference.unified(MarketDataProvider.TWELVE_DATA));

        when(marketDataProviderCatalog.isConfigured(MarketDataProvider.TWELVE_DATA))
                .thenReturn(false);
        when(marketDataProviderCatalog.isConfigured(MarketDataProvider.TOSS_SECURITIES))
                .thenReturn(true);

        mockMvc.perform(get("/api/members/10/portfolios/100/market-data-providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].provider").value("TWELVE_DATA"))
                .andExpect(jsonPath("$[0].displayName").value("Twelve Data"))
                .andExpect(jsonPath("$[0].selectable").value(true))
                .andExpect(jsonPath("$[0].configured").value(false))
                .andExpect(jsonPath("$[1].provider").value("TOSS_SECURITIES"))
                .andExpect(jsonPath("$[1].requiresBrokerConnection").value(true))
                // 카탈로그의 selectable은 "선택 가능한 후보"라는 선언일 뿐이다. 실제 선택은
                // 포트폴리오에 연결된 검증된 토스증권 연결이 있을 때만 허용되며, 그 판정은
                // PUT market-data-preference에서 PortfolioService가 별도로 수행한다.
                .andExpect(jsonPath("$[1].selectable").value(true))
                .andExpect(jsonPath("$[1].configured").value(true));
    }

    @Test
    void getsPortfolioMarketDataPreference() throws Exception {
        when(portfolioService.getMarketDataPreference(10L, 100L))
                .thenReturn(PortfolioMarketDataPreference.unified(MarketDataProvider.TWELVE_DATA));

        mockMvc.perform(get("/api/members/10/portfolios/100/market-data-preference"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priceProvider").value("TWELVE_DATA"))
                .andExpect(jsonPath("$.candleProvider").value("TWELVE_DATA"))
                .andExpect(jsonPath("$.assetReferenceProvider").value("TWELVE_DATA"));
    }

    @Test
    void updatesPortfolioMarketDataPreference() throws Exception {
        PortfolioMarketDataPreference preference =
                PortfolioMarketDataPreference.unified(MarketDataProvider.TWELVE_DATA);
        when(portfolioService.updateMarketDataPreference(
                10L,
                100L,
                MarketDataProvider.TWELVE_DATA
        )).thenReturn(preference);

        mockMvc.perform(put("/api/members/10/portfolios/100/market-data-preference")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "provider": "TWELVE_DATA"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priceProvider").value("TWELVE_DATA"));

        verify(portfolioService).updateMarketDataPreference(
                10L,
                100L,
                MarketDataProvider.TWELVE_DATA
        );
    }

    @Test
    void rejectsUnavailableMarketDataProvider() throws Exception {
        when(portfolioService.updateMarketDataPreference(
                10L,
                100L,
                MarketDataProvider.TOSS_SECURITIES
        )).thenThrow(new IllegalArgumentException(
                "현재 선택할 수 없는 시장 데이터 제공자입니다."
        ));

        mockMvc.perform(put("/api/members/10/portfolios/100/market-data-preference")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "provider": "TOSS_SECURITIES"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("현재 선택할 수 없는 시장 데이터 제공자입니다."));
    }

    @Test
    void getsPortfolioAssetBacktest() throws Exception {
        BacktestTradeEvent buyEvent = new BacktestTradeEvent(
                LocalDate.of(2026, 3, 6),
                BacktestTradeType.BUY,
                new BigDecimal("200"),
                new BigDecimal("5"),
                BigDecimal.ZERO,
                new BigDecimal("5"),
                new BigDecimal("1000")
        );

        BacktestResult result = new BacktestResult(
                LocalDate.of(2025, 1, 3),
                LocalDate.of(2026, 3, 6),
                new BigDecimal("1000"),
                new BigDecimal("1000"),
                new BigDecimal("0"),
                new BigDecimal("0"),
                1,
                List.of(buyEvent),
                BacktestAssumptions.zeroCost()
        );

        PortfolioAssetBacktest backtest = new PortfolioAssetBacktest(
                Market.US,
                "SOXL",
                LocalDate.of(2026, 3, 6),
                result
        );

        when(portfolioAssetBacktestService.getBacktest(
                10L,
                100L,
                Market.US,
                "SOXL",
                new BigDecimal("1000")
        )).thenReturn(backtest);

        mockMvc.perform(
                        get("/api/members/10/portfolios/100/assets/US/SOXL/backtest")
                                .param("initialCash", "1000")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.market").value("US"))
                .andExpect(jsonPath("$.ticker").value("SOXL"))
                .andExpect(jsonPath("$.dataAsOfDate").value("2026-03-06"))
                .andExpect(jsonPath("$.result.tradeCount").value(1))
                .andExpect(jsonPath("$.result.trades[0].type").value("BUY"))
                .andExpect(jsonPath("$.result.assumptions.feeRate").value(0))
                .andExpect(jsonPath("$.result.assumptions.slippageRate").value(0));

        verify(portfolioAssetBacktestService).getBacktest(
                10L,
                100L,
                Market.US,
                "SOXL",
                new BigDecimal("1000")
        );
    }

    @Test
    void returnsBadRequestWhenBacktestInitialCashIsInvalid() throws Exception {
        when(portfolioAssetBacktestService.getBacktest(
                10L,
                100L,
                Market.US,
                "SOXL",
                new BigDecimal("0")
        )).thenThrow(new IllegalArgumentException("초기 자산은 0보다 커야 합니다."));

        mockMvc.perform(
                        get("/api/members/10/portfolios/100/assets/US/SOXL/backtest")
                                .param("initialCash", "0")
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("초기 자산은 0보다 커야 합니다."));
    }

    @Test
    void returnsNotFoundWhenBacktestAssetProfileDoesNotExist() throws Exception {
        when(portfolioAssetBacktestService.getBacktest(
                10L,
                100L,
                Market.US,
                "UNKNOWN",
                new BigDecimal("1000")
        )).thenThrow(new AssetProfileNotFoundException(Market.US, "UNKNOWN"));

        mockMvc.perform(
                        get("/api/members/10/portfolios/100/assets/US/UNKNOWN/backtest")
                                .param("initialCash", "1000")
                )
                .andExpect(status().isNotFound());
    }
}
