package com.tradeguide.service.broker;

import com.tradeguide.domain.broker.BrokerAccount;
import com.tradeguide.domain.broker.BrokerConnection;
import com.tradeguide.domain.broker.BrokerOrderExecutionGrant;
import com.tradeguide.domain.broker.BrokerOrderExecutionGrantStatus;
import com.tradeguide.domain.broker.BrokerOrderExecutionRun;
import com.tradeguide.domain.broker.BrokerOrderSide;
import com.tradeguide.domain.member.Member;
import com.tradeguide.domain.portfolio.Portfolio;
import com.tradeguide.domain.portfolio.PortfolioBrokerLink;
import com.tradeguide.domain.strategy.AssetStrategyGuide;
import com.tradeguide.domain.strategy.AssetTradePlanPreview;
import com.tradeguide.domain.strategy.StrategyAction;
import com.tradeguide.domain.strategy.StrategyDecision;
import com.tradeguide.domain.strategy.StrategyGuideBatch;
import com.tradeguide.domain.strategy.StrategyMetadata;
import com.tradeguide.domain.strategy.StrategySignal;
import com.tradeguide.domain.strategy.TradePlanPreviewBatch;
import com.tradeguide.domain.strategy.TradePlanPreviewStatus;
import com.tradeguide.domain.trade.Currency;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.domain.valuation.CurrencyValuationTotals;
import com.tradeguide.domain.valuation.HoldingValuation;
import com.tradeguide.domain.valuation.PortfolioValuation;
import com.tradeguide.repository.broker.BrokerOrderExecutionGrantRepository;
import com.tradeguide.repository.broker.PortfolioBrokerLinkRepository;
import com.tradeguide.service.strategy.PortfolioStrategyGuideService;
import com.tradeguide.service.strategy.TradePlanPreviewService;
import com.tradeguide.service.valuation.PortfolioValuationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BrokerOrderExecutionTriggerSchedulerTest {

    @Mock
    private BrokerOrderExecutionGrantRepository brokerOrderExecutionGrantRepository;

    @Mock
    private PortfolioBrokerLinkRepository portfolioBrokerLinkRepository;

    @Mock
    private PortfolioStrategyGuideService portfolioStrategyGuideService;

    @Mock
    private TradePlanPreviewService tradePlanPreviewService;

    @Mock
    private PortfolioValuationService portfolioValuationService;

    @Mock
    private BrokerOrderExecutionService brokerOrderExecutionService;

    private BrokerOrderExecutionTriggerScheduler scheduler;

    private Member member;
    private BrokerConnection connection;
    private BrokerOrderExecutionGrant grant;
    private Portfolio portfolio;

    @BeforeEach
    void setUp() {
        scheduler = new BrokerOrderExecutionTriggerScheduler(
                brokerOrderExecutionGrantRepository,
                portfolioBrokerLinkRepository,
                portfolioStrategyGuideService,
                tradePlanPreviewService,
                portfolioValuationService,
                brokerOrderExecutionService
        );

        member = mock(Member.class);
        lenient().when(member.getId()).thenReturn(1L);
        connection = mock(BrokerConnection.class);
        lenient().when(connection.getId()).thenReturn(2L);
        grant = mock(BrokerOrderExecutionGrant.class);
        lenient().when(grant.getMember()).thenReturn(member);
        lenient().when(grant.getBrokerConnection()).thenReturn(connection);
        lenient().when(grant.getStrategyId()).thenReturn("track-a-weekly-ma-crossover");

        portfolio = mock(Portfolio.class);
        lenient().when(portfolio.getId()).thenReturn(3L);
    }

    @Test
    void onlyEvaluatesActiveGrants() {
        when(brokerOrderExecutionGrantRepository.findAllByStatus(BrokerOrderExecutionGrantStatus.ACTIVE))
                .thenReturn(List.of());

        scheduler.evaluateActiveGrants();

        verify(brokerOrderExecutionGrantRepository).findAllByStatus(BrokerOrderExecutionGrantStatus.ACTIVE);
        verifyNoInteractions(portfolioBrokerLinkRepository, portfolioStrategyGuideService, brokerOrderExecutionService);
    }

    @Test
    void executesSellOrderWhenStrategyMatchesAndCurrencyIsUsd() {
        stubOneLinkedPortfolio();
        stubValuation(new BigDecimal("100000"), sellGuide(Market.US, "SOXL", "track-a-weekly-ma-crossover"));

        scheduler.evaluateActiveGrants();

        ArgumentCaptor<BigDecimal> quantityCaptor = ArgumentCaptor.forClass(BigDecimal.class);
        verify(brokerOrderExecutionService).executeOrder(
                any(), anyString(), any(), any(), quantityCaptor.capture(), any(), any());
        assertThat(quantityCaptor.getValue()).isEqualByComparingTo("5");
    }

    @Test
    void skipsSellSignalFromDifferentStrategy() {
        stubOneLinkedPortfolio();
        stubValuation(new BigDecimal("100000"), sellGuide(Market.US, "SOXL", "some-other-strategy"));

        scheduler.evaluateActiveGrants();

        verify(brokerOrderExecutionService, never()).executeOrder(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void skipsNonUsdHoldings() {
        stubOneLinkedPortfolio();
        stubValuation(new BigDecimal("100000"), sellGuide(Market.KR, "005930", "track-a-weekly-ma-crossover"));

        scheduler.evaluateActiveGrants();

        verify(brokerOrderExecutionService, never()).executeOrder(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void skipsNonSellActions() {
        stubOneLinkedPortfolio();
        AssetStrategyGuide holdGuide = new AssetStrategyGuide(
                Market.US, "SOXL",
                new StrategyDecision(StrategyAction.HOLD, "추세 유지", signal("track-a-weekly-ma-crossover")));
        stubValuation(new BigDecimal("100000"), holdGuide);

        scheduler.evaluateActiveGrants();

        verify(brokerOrderExecutionService, never()).executeOrder(any(), any(), any(), any(), any(), any(), any());
    }

    // BUY(신규 진입) 평가 - TradePlanPreviewService가 계산한 수량·금액을 그대로 읽어 쓴다.

    @Test
    void executesBuyOrderWhenPlanStatusIsBuyAndStrategyMatches() {
        stubOneLinkedPortfolio();
        stubValuation(new BigDecimal("100000"), sellGuide(Market.US, "IRRELEVANT", "no-match"));
        stubCandidatePlans(buyPlan(Market.US, "TQQQ", "track-a-weekly-ma-crossover"));

        scheduler.evaluateActiveGrants();

        verify(brokerOrderExecutionService).executeOrder(
                eq(grant), eq("track-a-weekly-ma-crossover"), eq("TQQQ"), eq(BrokerOrderSide.BUY),
                any(), any(), any());
    }

    @Test
    void skipsCandidatePlanNotInBuyStatus() {
        stubOneLinkedPortfolio();
        stubValuation(new BigDecimal("100000"), sellGuide(Market.US, "IRRELEVANT", "no-match"));
        AssetTradePlanPreview notReadyPlan = new AssetTradePlanPreview(
                Market.US, "TQQQ", TradePlanPreviewStatus.NOT_READY,
                com.tradeguide.domain.strategy.TradePlanPreviewNotReadyReason.MISSING_RISK_POLICY,
                null, null, null, null, null, List.of(),
                "위험 한도 미설정", metadata("track-a-weekly-ma-crossover"));
        stubCandidatePlans(notReadyPlan);

        scheduler.evaluateActiveGrants();

        verify(brokerOrderExecutionService, never()).executeOrder(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void skipsBuyPlanFromDifferentStrategy() {
        stubOneLinkedPortfolio();
        stubValuation(new BigDecimal("100000"), sellGuide(Market.US, "IRRELEVANT", "no-match"));
        stubCandidatePlans(buyPlan(Market.US, "TQQQ", "some-other-strategy"));

        scheduler.evaluateActiveGrants();

        verify(brokerOrderExecutionService, never()).executeOrder(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void skipsBuyPlanForNonUsdMarket() {
        stubOneLinkedPortfolio();
        stubValuation(new BigDecimal("100000"), sellGuide(Market.US, "IRRELEVANT", "no-match"));
        stubCandidatePlans(buyPlan(Market.KR, "005930", "track-a-weekly-ma-crossover"));

        scheduler.evaluateActiveGrants();

        verify(brokerOrderExecutionService, never()).executeOrder(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void oneGrantFailureDoesNotBlockOthers() {
        BrokerOrderExecutionGrant secondGrant = mock(BrokerOrderExecutionGrant.class);
        Member secondMember = mock(Member.class);
        when(secondMember.getId()).thenReturn(9L);
        BrokerConnection secondConnection = mock(BrokerConnection.class);
        when(secondConnection.getId()).thenReturn(8L);
        when(secondGrant.getMember()).thenReturn(secondMember);
        when(secondGrant.getBrokerConnection()).thenReturn(secondConnection);

        when(brokerOrderExecutionGrantRepository.findAllByStatus(BrokerOrderExecutionGrantStatus.ACTIVE))
                .thenReturn(List.of(grant, secondGrant));
        when(portfolioBrokerLinkRepository.findAllByBrokerConnection_Id(connection.getId()))
                .thenThrow(new RuntimeException("boom"));
        when(portfolioBrokerLinkRepository.findAllByBrokerConnection_Id(secondConnection.getId()))
                .thenReturn(List.of());

        scheduler.evaluateActiveGrants();

        verify(portfolioBrokerLinkRepository).findAllByBrokerConnection_Id(connection.getId());
        verify(portfolioBrokerLinkRepository).findAllByBrokerConnection_Id(secondConnection.getId());
    }

    private void stubOneLinkedPortfolio() {
        PortfolioBrokerLink link = mock(PortfolioBrokerLink.class);
        when(link.getPortfolio()).thenReturn(portfolio);
        when(brokerOrderExecutionGrantRepository.findAllByStatus(BrokerOrderExecutionGrantStatus.ACTIVE))
                .thenReturn(List.of(grant));
        when(portfolioBrokerLinkRepository.findAllByBrokerConnection_Id(connection.getId()))
                .thenReturn(List.of(link));
    }

    private void stubValuation(BigDecimal accountAssetValue, AssetStrategyGuide guide) {
        HoldingValuation holdingValuation = new HoldingValuation(
                guide.getMarket(), guide.getTicker(),
                new BigDecimal("5"), new BigDecimal("100"), new BigDecimal("110"),
                new BigDecimal("500"), new BigDecimal("550"), new BigDecimal("50"), new BigDecimal("10"));
        CurrencyValuationTotals usdTotals = new CurrencyValuationTotals(
                Currency.USD, accountAssetValue, accountAssetValue, BigDecimal.ZERO, BigDecimal.ZERO);
        PortfolioValuation valuation = new PortfolioValuation(
                List.of(holdingValuation), Map.of(Currency.USD, usdTotals));

        when(portfolioValuationService.getPortfolioValuation(member.getId(), portfolio.getId()))
                .thenReturn(valuation);
        when(portfolioStrategyGuideService.getPortfolioStrategyGuides(member.getId(), portfolio.getId()))
                .thenReturn(new StrategyGuideBatch(List.of(guide), List.of()));
        lenient().when(tradePlanPreviewService.getTradePlanPreview(member.getId(), portfolio.getId()))
                .thenReturn(new TradePlanPreviewBatch(List.of(), List.of(), List.of()));
        lenient().when(brokerOrderExecutionService.executeOrder(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(mock(BrokerOrderExecutionRun.class));
    }

    private AssetStrategyGuide sellGuide(Market market, String ticker, String strategyId) {
        return new AssetStrategyGuide(
                market, ticker,
                new StrategyDecision(StrategyAction.SELL, "추세 이탈", signal(strategyId)));
    }

    private StrategySignal signal(String strategyId) {
        return new StrategySignal(
                new BigDecimal("110"), "10주선이 40주선 아래로 교차",
                new StrategyMetadata(strategyId, "1.0", LocalDateTime.now().toLocalDate()),
                null, null, null);
    }

    private void stubCandidatePlans(AssetTradePlanPreview... plans) {
        when(tradePlanPreviewService.getTradePlanPreview(member.getId(), portfolio.getId()))
                .thenReturn(new TradePlanPreviewBatch(List.of(plans), List.of(), List.of()));
    }

    private AssetTradePlanPreview buyPlan(Market market, String ticker, String strategyId) {
        return new AssetTradePlanPreview(
                market, ticker, TradePlanPreviewStatus.BUY, null,
                new BigDecimal("55"), new BigDecimal("50"),
                new BigDecimal("10"), new BigDecimal("550"), new BigDecimal("50"),
                List.of(), "검토용 매수 계획", metadata(strategyId));
    }

    private StrategyMetadata metadata(String strategyId) {
        return new StrategyMetadata(strategyId, "1.0", LocalDateTime.now().toLocalDate());
    }
}
