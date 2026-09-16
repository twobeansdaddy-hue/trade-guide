package com.tradeguide.service.strategy;

import com.tradeguide.domain.holding.Holding;
import com.tradeguide.domain.portfolio.Portfolio;
import com.tradeguide.domain.risk.PortfolioRiskPolicy;
import com.tradeguide.domain.strategy.*;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.domain.valuation.HoldingValuation;
import com.tradeguide.domain.valuation.PortfolioValuation;
import com.tradeguide.repository.portfolio.PortfolioRepository;
import com.tradeguide.service.holding.HoldingService;
import com.tradeguide.service.valuation.PortfolioValuationService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 검토용 매매 계획 미리보기 서비스의 핵심 계산을 검증한다. 어떤 테스트도 실제 주문 전송이나
 * 저장을 가정하지 않으며, {@link TradePlan}이나 Track A 진입/청산 규칙 자체는 다시
 * 검증하지 않는다(이미 {@link StrategyDecisionMakerTest} 등에서 다룬다).
 */
@ExtendWith(MockitoExtension.class)
class TradePlanPreviewServiceTest {

    private static final Long MEMBER_ID = 10L;
    private static final Long PORTFOLIO_ID = 100L;

    @Mock
    private PortfolioStrategyGuideService portfolioStrategyGuideService;

    @Mock
    private PortfolioCandidateStrategyGuideService portfolioCandidateStrategyGuideService;

    @Mock
    private PortfolioValuationService portfolioValuationService;

    @Mock
    private HoldingService holdingService;

    @Mock
    private PortfolioRepository portfolioRepository;

    @InjectMocks
    private TradePlanPreviewService tradePlanPreviewService;

    @Test
    void calculatesRiskLimitedBuyQuantityFromFormulaAndFlagsCashNotSynced() {
        Portfolio portfolio = mock(Portfolio.class);
        PortfolioRiskPolicy riskPolicy = new PortfolioRiskPolicy(
                new BigDecimal("0.02"),
                new BigDecimal("0.5"),
                new BigDecimal("0.10")
        );
        when(portfolio.getRiskPolicy()).thenReturn(riskPolicy);
        when(portfolioRepository.findByMember_IdAndId(MEMBER_ID, PORTFOLIO_ID))
                .thenReturn(Optional.of(portfolio));

        when(portfolioStrategyGuideService.getPortfolioStrategyGuides(MEMBER_ID, PORTFOLIO_ID))
                .thenReturn(new StrategyGuideBatch(List.of(), List.of()));
        when(holdingService.getHoldings(MEMBER_ID, PORTFOLIO_ID)).thenReturn(List.of());

        AssetStrategyGuide candidate = candidateBuyGuide("TQQQ", new BigDecimal("90"), new BigDecimal("81"));
        when(portfolioCandidateStrategyGuideService.getCandidateStrategyGuides(MEMBER_ID, PORTFOLIO_ID))
                .thenReturn(new StrategyGuideBatch(List.of(candidate), List.of()));

        when(portfolioValuationService.getPortfolioValuation(MEMBER_ID, PORTFOLIO_ID))
                .thenReturn(portfolioValuationWithMarketValue(new BigDecimal("10000")));

        TradePlanPreviewBatch batch = tradePlanPreviewService.getTradePlanPreview(MEMBER_ID, PORTFOLIO_ID);

        assertThat(batch.getCandidatePlans()).hasSize(1);
        AssetTradePlanPreview plan = batch.getCandidatePlans().get(0);

        assertThat(plan.getStatus()).isEqualTo(TradePlanPreviewStatus.BUY);
        // 위험 금액 200 / 손절폭 9 = 22.222222 (소수점 6자리 내림)
        assertThat(plan.getQuantity()).isEqualByComparingTo("22.222222");
        assertThat(plan.getAmount()).isEqualByComparingTo("1999.99");
        assertThat(plan.getEstimatedMaxLoss()).isEqualByComparingTo("199.99");
        assertThat(plan.getConstraints()).containsExactly(TradePlanPreviewConstraint.AVAILABLE_CASH_NOT_SYNCED);
        assertThat(plan.isRequiresUserConfirmation()).isTrue();
    }

    @Test
    void capsBuyQuantityByRemainingSingleAssetExposure() {
        Portfolio portfolio = mock(Portfolio.class);
        PortfolioRiskPolicy riskPolicy = new PortfolioRiskPolicy(
                new BigDecimal("0.02"),
                new BigDecimal("0.05"),
                new BigDecimal("0.10")
        );
        when(portfolio.getRiskPolicy()).thenReturn(riskPolicy);
        when(portfolioRepository.findByMember_IdAndId(MEMBER_ID, PORTFOLIO_ID))
                .thenReturn(Optional.of(portfolio));

        when(portfolioStrategyGuideService.getPortfolioStrategyGuides(MEMBER_ID, PORTFOLIO_ID))
                .thenReturn(new StrategyGuideBatch(List.of(), List.of()));
        when(holdingService.getHoldings(MEMBER_ID, PORTFOLIO_ID)).thenReturn(List.of());

        AssetStrategyGuide candidate = candidateBuyGuide("TQQQ", new BigDecimal("90"), new BigDecimal("81"));
        when(portfolioCandidateStrategyGuideService.getCandidateStrategyGuides(MEMBER_ID, PORTFOLIO_ID))
                .thenReturn(new StrategyGuideBatch(List.of(candidate), List.of()));

        when(portfolioValuationService.getPortfolioValuation(MEMBER_ID, PORTFOLIO_ID))
                .thenReturn(portfolioValuationWithMarketValue(new BigDecimal("10000")));

        TradePlanPreviewBatch batch = tradePlanPreviewService.getTradePlanPreview(MEMBER_ID, PORTFOLIO_ID);
        AssetTradePlanPreview plan = batch.getCandidatePlans().get(0);

        // 노출 한도 500 / 기준가 90 = 5.555555, 위험 기준 수량 22.222222보다 작아 한도로 축소된다.
        assertThat(plan.getQuantity()).isEqualByComparingTo("5.555555");
        assertThat(plan.getConstraints()).containsExactlyInAnyOrder(
                TradePlanPreviewConstraint.AVAILABLE_CASH_NOT_SYNCED,
                TradePlanPreviewConstraint.SINGLE_ASSET_EXPOSURE_CAP_APPLIED
        );
    }

    @Test
    void returnsNotReadyWhenStopLossIsNotConfigured() {
        Portfolio portfolio = mock(Portfolio.class);
        PortfolioRiskPolicy riskPolicy = new PortfolioRiskPolicy(
                new BigDecimal("0.02"),
                new BigDecimal("0.5")
        );
        when(portfolio.getRiskPolicy()).thenReturn(riskPolicy);
        when(portfolioRepository.findByMember_IdAndId(MEMBER_ID, PORTFOLIO_ID))
                .thenReturn(Optional.of(portfolio));

        when(portfolioStrategyGuideService.getPortfolioStrategyGuides(MEMBER_ID, PORTFOLIO_ID))
                .thenReturn(new StrategyGuideBatch(List.of(), List.of()));
        when(holdingService.getHoldings(MEMBER_ID, PORTFOLIO_ID)).thenReturn(List.of());

        AssetStrategyGuide candidate = candidateBuyGuideWithoutStopLoss("TQQQ", new BigDecimal("90"));
        when(portfolioCandidateStrategyGuideService.getCandidateStrategyGuides(MEMBER_ID, PORTFOLIO_ID))
                .thenReturn(new StrategyGuideBatch(List.of(candidate), List.of()));

        TradePlanPreviewBatch batch = tradePlanPreviewService.getTradePlanPreview(MEMBER_ID, PORTFOLIO_ID);
        AssetTradePlanPreview plan = batch.getCandidatePlans().get(0);

        assertThat(plan.getStatus()).isEqualTo(TradePlanPreviewStatus.NOT_READY);
        assertThat(plan.getNotReadyReason()).isEqualTo(TradePlanPreviewNotReadyReason.MISSING_STOP_LOSS);
        assertThat(plan.getQuantity()).isNull();
        assertThat(plan.getAmount()).isNull();

        verify(portfolioValuationService, never()).getPortfolioValuation(any(), any());
    }

    @Test
    void returnsNotReadyWhenRiskPolicyIsNotConfigured() {
        Portfolio portfolio = mock(Portfolio.class);
        when(portfolio.getRiskPolicy()).thenReturn(null);
        when(portfolioRepository.findByMember_IdAndId(MEMBER_ID, PORTFOLIO_ID))
                .thenReturn(Optional.of(portfolio));

        when(portfolioStrategyGuideService.getPortfolioStrategyGuides(MEMBER_ID, PORTFOLIO_ID))
                .thenReturn(new StrategyGuideBatch(List.of(), List.of()));
        when(holdingService.getHoldings(MEMBER_ID, PORTFOLIO_ID)).thenReturn(List.of());

        AssetStrategyGuide candidate = candidateBuyGuideWithoutStopLoss("TQQQ", new BigDecimal("90"));
        when(portfolioCandidateStrategyGuideService.getCandidateStrategyGuides(MEMBER_ID, PORTFOLIO_ID))
                .thenReturn(new StrategyGuideBatch(List.of(candidate), List.of()));

        TradePlanPreviewBatch batch = tradePlanPreviewService.getTradePlanPreview(MEMBER_ID, PORTFOLIO_ID);
        AssetTradePlanPreview plan = batch.getCandidatePlans().get(0);

        assertThat(plan.getStatus()).isEqualTo(TradePlanPreviewStatus.NOT_READY);
        assertThat(plan.getNotReadyReason()).isEqualTo(TradePlanPreviewNotReadyReason.MISSING_RISK_POLICY);

        verify(portfolioValuationService, never()).getPortfolioValuation(any(), any());
    }

    @Test
    void returnsStopLossExitReviewForHeldAssetAtOrBelowStopLossPrice() {
        Portfolio portfolio = mock(Portfolio.class);
        PortfolioRiskPolicy riskPolicy = new PortfolioRiskPolicy(
                new BigDecimal("0.02"),
                new BigDecimal("0.5"),
                new BigDecimal("0.10")
        );
        when(portfolio.getRiskPolicy()).thenReturn(riskPolicy);
        when(portfolioRepository.findByMember_IdAndId(MEMBER_ID, PORTFOLIO_ID))
                .thenReturn(Optional.of(portfolio));

        Holding holding = new Holding(Market.US, "SOXL", new BigDecimal("50"), new BigDecimal("25"));
        when(holdingService.getHoldings(MEMBER_ID, PORTFOLIO_ID)).thenReturn(List.of(holding));

        // 완료 주봉 종가(referencePrice)는 22.50(손절가)보다 높지만, 실시간 현재가는
        // 20.00으로 이미 손절가 아래다. 손절 판정은 실시간 현재가를 따라야 한다.
        AssetStrategyGuide guide = heldSellGuide(
                "SOXL",
                new BigDecimal("30.00"),
                new BigDecimal("22.50")
        );
        when(portfolioStrategyGuideService.getPortfolioStrategyGuides(MEMBER_ID, PORTFOLIO_ID))
                .thenReturn(new StrategyGuideBatch(List.of(guide), List.of()));
        when(portfolioCandidateStrategyGuideService.getCandidateStrategyGuides(MEMBER_ID, PORTFOLIO_ID))
                .thenReturn(new StrategyGuideBatch(List.of(), List.of()));
        when(portfolioValuationService.getPortfolioValuation(MEMBER_ID, PORTFOLIO_ID))
                .thenReturn(portfolioValuationWithCurrentPrice("SOXL", new BigDecimal("20.00")));

        TradePlanPreviewBatch batch = tradePlanPreviewService.getTradePlanPreview(MEMBER_ID, PORTFOLIO_ID);
        AssetTradePlanPreview plan = batch.getHeldAssetPlans().get(0);

        assertThat(plan.getStatus()).isEqualTo(TradePlanPreviewStatus.STOP_LOSS_EXIT_REVIEW);
        assertThat(plan.getQuantity()).isEqualByComparingTo("50");
        // 상태의 기준 가격도 실시간 현재가로 노출되어야 한다(주봉 종가가 아님).
        assertThat(plan.getReferencePrice()).isEqualByComparingTo("20.00");
    }

    @Test
    void includesProtectiveExitPlannedActionForFullHeldQuantityWhenStopIsConfigured() {
        Portfolio portfolio = mock(Portfolio.class);
        PortfolioRiskPolicy riskPolicy = new PortfolioRiskPolicy(
                new BigDecimal("0.02"),
                new BigDecimal("0.5"),
                new BigDecimal("0.10")
        );
        when(portfolio.getRiskPolicy()).thenReturn(riskPolicy);
        when(portfolioRepository.findByMember_IdAndId(MEMBER_ID, PORTFOLIO_ID))
                .thenReturn(Optional.of(portfolio));

        Holding holding = new Holding(Market.US, "SOXL", new BigDecimal("50"), new BigDecimal("25"));
        when(holdingService.getHoldings(MEMBER_ID, PORTFOLIO_ID)).thenReturn(List.of(holding));

        AssetStrategyGuide guide = heldSellGuide(
                "SOXL",
                new BigDecimal("30.00"),
                new BigDecimal("22.50")
        );
        when(portfolioStrategyGuideService.getPortfolioStrategyGuides(MEMBER_ID, PORTFOLIO_ID))
                .thenReturn(new StrategyGuideBatch(List.of(guide), List.of()));
        when(portfolioCandidateStrategyGuideService.getCandidateStrategyGuides(MEMBER_ID, PORTFOLIO_ID))
                .thenReturn(new StrategyGuideBatch(List.of(), List.of()));
        when(portfolioValuationService.getPortfolioValuation(MEMBER_ID, PORTFOLIO_ID))
                .thenReturn(portfolioValuationWithCurrentPrice("SOXL", new BigDecimal("20.00")));

        TradePlanPreviewBatch batch = tradePlanPreviewService.getTradePlanPreview(MEMBER_ID, PORTFOLIO_ID);
        AssetTradePlanPreview plan = batch.getHeldAssetPlans().get(0);

        assertThat(plan.getStatus()).isEqualTo(TradePlanPreviewStatus.STOP_LOSS_EXIT_REVIEW);
        assertThat(plan.getPlannedActions()).hasSize(1);
        PlannedTradeAction action = plan.getPlannedActions().get(0);
        assertThat(action.getType()).isEqualTo(PlannedTradeActionType.PROTECTIVE_EXIT_REVIEW);
        assertThat(action.getTriggerPrice()).isEqualByComparingTo("22.50");
        assertThat(action.getQuantity()).isEqualByComparingTo("50");
        assertThat(action.getAmount()).isEqualByComparingTo("1125.00");
        assertThat(action.isRequiresUserConfirmation()).isTrue();
    }

    /**
     * 완료 주봉 추세 신호가 HOLD(상승 추세 유지)라도, 실시간 현재가가 이미 손절가
     * 이하로 떨어졌다면 추세 신호보다 손절 검토를 우선해야 한다. 주봉은 최대 한 주 이상
     * 오래될 수 있어 이 우선순위가 없으면 실제로는 손절 구간인데도 "보유"로 잘못 안내한다.
     */
    @Test
    void prioritizesStopLossExitReviewOverHoldTrendWhenCurrentPriceHasAlreadyBreachedStopLoss() {
        Portfolio portfolio = mock(Portfolio.class);
        PortfolioRiskPolicy riskPolicy = new PortfolioRiskPolicy(
                new BigDecimal("0.02"),
                new BigDecimal("0.5"),
                new BigDecimal("0.10")
        );
        when(portfolio.getRiskPolicy()).thenReturn(riskPolicy);
        when(portfolioRepository.findByMember_IdAndId(MEMBER_ID, PORTFOLIO_ID))
                .thenReturn(Optional.of(portfolio));

        Holding holding = new Holding(Market.US, "SOXL", new BigDecimal("7"), new BigDecimal("125.65"));
        when(holdingService.getHoldings(MEMBER_ID, PORTFOLIO_ID)).thenReturn(List.of(holding));

        // 완료 주봉 종가(121.82)는 여전히 상승 추세라 HOLD를 반환하지만, 실시간 현재가(102.72)는
        // 손절가(113.14)를 이미 하회한 상태다.
        AssetStrategyGuide guide = heldHoldGuide(
                "SOXL",
                new BigDecimal("121.82"),
                new BigDecimal("113.14")
        );
        when(portfolioStrategyGuideService.getPortfolioStrategyGuides(MEMBER_ID, PORTFOLIO_ID))
                .thenReturn(new StrategyGuideBatch(List.of(guide), List.of()));
        when(portfolioCandidateStrategyGuideService.getCandidateStrategyGuides(MEMBER_ID, PORTFOLIO_ID))
                .thenReturn(new StrategyGuideBatch(List.of(), List.of()));
        when(portfolioValuationService.getPortfolioValuation(MEMBER_ID, PORTFOLIO_ID))
                .thenReturn(portfolioValuationWithCurrentPrice("SOXL", new BigDecimal("102.72")));

        TradePlanPreviewBatch batch = tradePlanPreviewService.getTradePlanPreview(MEMBER_ID, PORTFOLIO_ID);
        AssetTradePlanPreview plan = batch.getHeldAssetPlans().get(0);

        assertThat(plan.getStatus()).isEqualTo(TradePlanPreviewStatus.STOP_LOSS_EXIT_REVIEW);
        assertThat(plan.getQuantity()).isEqualByComparingTo("7");
        assertThat(plan.getReferencePrice()).isEqualByComparingTo("102.72");
    }

    @Test
    void includesNonActionablePartialProfitReviewForProfitableHeldPositionWithoutInventingPriceOrQuantity() {
        Portfolio portfolio = mock(Portfolio.class);
        when(portfolioRepository.findByMember_IdAndId(MEMBER_ID, PORTFOLIO_ID))
                .thenReturn(Optional.of(portfolio));

        Holding holding = new Holding(Market.US, "SOXL", new BigDecimal("50"), new BigDecimal("90"));
        when(holdingService.getHoldings(MEMBER_ID, PORTFOLIO_ID)).thenReturn(List.of(holding));

        AssetStrategyGuide guide = heldSellGuide(
                "SOXL",
                new BigDecimal("95.00"),
                new BigDecimal("81.00")
        );
        when(portfolioStrategyGuideService.getPortfolioStrategyGuides(MEMBER_ID, PORTFOLIO_ID))
                .thenReturn(new StrategyGuideBatch(List.of(guide), List.of()));
        when(portfolioCandidateStrategyGuideService.getCandidateStrategyGuides(MEMBER_ID, PORTFOLIO_ID))
                .thenReturn(new StrategyGuideBatch(List.of(), List.of()));

        TradePlanPreviewBatch batch = tradePlanPreviewService.getTradePlanPreview(MEMBER_ID, PORTFOLIO_ID);
        AssetTradePlanPreview plan = batch.getHeldAssetPlans().get(0);

        assertThat(plan.getStatus()).isEqualTo(TradePlanPreviewStatus.SELL_REVIEW);
        assertThat(plan.getPlannedActions()).hasSize(2);

        PlannedTradeAction protectiveExit = plan.getPlannedActions().get(0);
        assertThat(protectiveExit.getType()).isEqualTo(PlannedTradeActionType.PROTECTIVE_EXIT_REVIEW);
        assertThat(protectiveExit.getQuantity()).isEqualByComparingTo("50");

        PlannedTradeAction partialProfit = plan.getPlannedActions().get(1);
        assertThat(partialProfit.getType()).isEqualTo(PlannedTradeActionType.PARTIAL_PROFIT_REVIEW);
        assertThat(partialProfit.getTriggerPrice()).isNull();
        assertThat(partialProfit.getQuantity()).isNull();
        assertThat(partialProfit.getAmount()).isNull();
        assertThat(partialProfit.getReason()).contains("검증");
    }

    /**
     * 전략 기준 가격이 손절가보다 높아도(전 로직 기준으로는 이익 구간) 평균 매입가보다
     * 낮으면 실제로는 손실 포지션이다. 이 경우 부분 이익 검토 항목을 제시하지 않아야 한다.
     */
    @Test
    void omitsPartialProfitReviewWhenReferencePriceIsAboveStopLossButBelowAveragePurchasePrice() {
        Portfolio portfolio = mock(Portfolio.class);
        when(portfolioRepository.findByMember_IdAndId(MEMBER_ID, PORTFOLIO_ID))
                .thenReturn(Optional.of(portfolio));

        Holding holding = new Holding(Market.US, "SOXL", new BigDecimal("50"), new BigDecimal("100"));
        when(holdingService.getHoldings(MEMBER_ID, PORTFOLIO_ID)).thenReturn(List.of(holding));

        AssetStrategyGuide guide = heldSellGuide(
                "SOXL",
                new BigDecimal("95.00"),
                new BigDecimal("81.00")
        );
        when(portfolioStrategyGuideService.getPortfolioStrategyGuides(MEMBER_ID, PORTFOLIO_ID))
                .thenReturn(new StrategyGuideBatch(List.of(guide), List.of()));
        when(portfolioCandidateStrategyGuideService.getCandidateStrategyGuides(MEMBER_ID, PORTFOLIO_ID))
                .thenReturn(new StrategyGuideBatch(List.of(), List.of()));

        TradePlanPreviewBatch batch = tradePlanPreviewService.getTradePlanPreview(MEMBER_ID, PORTFOLIO_ID);
        AssetTradePlanPreview plan = batch.getHeldAssetPlans().get(0);

        assertThat(plan.getStatus()).isEqualTo(TradePlanPreviewStatus.SELL_REVIEW);
        assertThat(plan.getPlannedActions()).hasSize(1);
        assertThat(plan.getPlannedActions().get(0).getType())
                .isEqualTo(PlannedTradeActionType.PROTECTIVE_EXIT_REVIEW);
    }

    /**
     * 평균 매입가를 알 수 없는(미확정) 보유 포지션은 손절가 대비 위치와 무관하게 부분
     * 이익 검토 항목을 제시하지 않아야 한다.
     */
    @Test
    void omitsPartialProfitReviewWhenAveragePurchasePriceIsUnknown() {
        Portfolio portfolio = mock(Portfolio.class);
        when(portfolioRepository.findByMember_IdAndId(MEMBER_ID, PORTFOLIO_ID))
                .thenReturn(Optional.of(portfolio));

        Holding holding = new Holding(Market.US, "SOXL", new BigDecimal("50"), null);
        when(holdingService.getHoldings(MEMBER_ID, PORTFOLIO_ID)).thenReturn(List.of(holding));

        AssetStrategyGuide guide = heldSellGuide(
                "SOXL",
                new BigDecimal("95.00"),
                new BigDecimal("81.00")
        );
        when(portfolioStrategyGuideService.getPortfolioStrategyGuides(MEMBER_ID, PORTFOLIO_ID))
                .thenReturn(new StrategyGuideBatch(List.of(guide), List.of()));
        when(portfolioCandidateStrategyGuideService.getCandidateStrategyGuides(MEMBER_ID, PORTFOLIO_ID))
                .thenReturn(new StrategyGuideBatch(List.of(), List.of()));

        TradePlanPreviewBatch batch = tradePlanPreviewService.getTradePlanPreview(MEMBER_ID, PORTFOLIO_ID);
        AssetTradePlanPreview plan = batch.getHeldAssetPlans().get(0);

        assertThat(plan.getStatus()).isEqualTo(TradePlanPreviewStatus.SELL_REVIEW);
        assertThat(plan.getPlannedActions()).hasSize(1);
        assertThat(plan.getPlannedActions().get(0).getType())
                .isEqualTo(PlannedTradeActionType.PROTECTIVE_EXIT_REVIEW);
    }

    @Test
    void includesPartialProfitReviewForProfitableHeldPositionInHoldStatus() {
        Portfolio portfolio = mock(Portfolio.class);
        when(portfolioRepository.findByMember_IdAndId(MEMBER_ID, PORTFOLIO_ID))
                .thenReturn(Optional.of(portfolio));

        Holding holding = new Holding(Market.US, "SOXL", new BigDecimal("50"), new BigDecimal("90"));
        when(holdingService.getHoldings(MEMBER_ID, PORTFOLIO_ID)).thenReturn(List.of(holding));

        AssetStrategyGuide guide = heldHoldGuide(
                "SOXL",
                new BigDecimal("95.00"),
                new BigDecimal("81.00")
        );
        when(portfolioStrategyGuideService.getPortfolioStrategyGuides(MEMBER_ID, PORTFOLIO_ID))
                .thenReturn(new StrategyGuideBatch(List.of(guide), List.of()));
        when(portfolioCandidateStrategyGuideService.getCandidateStrategyGuides(MEMBER_ID, PORTFOLIO_ID))
                .thenReturn(new StrategyGuideBatch(List.of(), List.of()));

        TradePlanPreviewBatch batch = tradePlanPreviewService.getTradePlanPreview(MEMBER_ID, PORTFOLIO_ID);
        AssetTradePlanPreview plan = batch.getHeldAssetPlans().get(0);

        assertThat(plan.getStatus()).isEqualTo(TradePlanPreviewStatus.HOLD);
        assertThat(plan.getPlannedActions()).hasSize(2);
        assertThat(plan.getPlannedActions().get(0).getType())
                .isEqualTo(PlannedTradeActionType.PROTECTIVE_EXIT_REVIEW);
        assertThat(plan.getPlannedActions().get(1).getType())
                .isEqualTo(PlannedTradeActionType.PARTIAL_PROFIT_REVIEW);
    }

    @Test
    void omitsPartialProfitReviewForHeldPositionInHoldStatusWhenBelowAveragePurchasePrice() {
        Portfolio portfolio = mock(Portfolio.class);
        when(portfolioRepository.findByMember_IdAndId(MEMBER_ID, PORTFOLIO_ID))
                .thenReturn(Optional.of(portfolio));

        Holding holding = new Holding(Market.US, "SOXL", new BigDecimal("50"), new BigDecimal("100"));
        when(holdingService.getHoldings(MEMBER_ID, PORTFOLIO_ID)).thenReturn(List.of(holding));

        AssetStrategyGuide guide = heldHoldGuide(
                "SOXL",
                new BigDecimal("95.00"),
                new BigDecimal("81.00")
        );
        when(portfolioStrategyGuideService.getPortfolioStrategyGuides(MEMBER_ID, PORTFOLIO_ID))
                .thenReturn(new StrategyGuideBatch(List.of(guide), List.of()));
        when(portfolioCandidateStrategyGuideService.getCandidateStrategyGuides(MEMBER_ID, PORTFOLIO_ID))
                .thenReturn(new StrategyGuideBatch(List.of(), List.of()));

        TradePlanPreviewBatch batch = tradePlanPreviewService.getTradePlanPreview(MEMBER_ID, PORTFOLIO_ID);
        AssetTradePlanPreview plan = batch.getHeldAssetPlans().get(0);

        assertThat(plan.getStatus()).isEqualTo(TradePlanPreviewStatus.HOLD);
        assertThat(plan.getPlannedActions()).hasSize(1);
        assertThat(plan.getPlannedActions().get(0).getType())
                .isEqualTo(PlannedTradeActionType.PROTECTIVE_EXIT_REVIEW);
    }

    @Test
    void treatsBuySignalOnHeldAssetAsPossibleAddConstrainedByRemainingExposureAndRiskBudget() {
        Portfolio portfolio = mock(Portfolio.class);
        PortfolioRiskPolicy riskPolicy = new PortfolioRiskPolicy(
                new BigDecimal("0.02"),
                new BigDecimal("0.10"),
                new BigDecimal("0.10")
        );
        when(portfolio.getRiskPolicy()).thenReturn(riskPolicy);
        when(portfolioRepository.findByMember_IdAndId(MEMBER_ID, PORTFOLIO_ID))
                .thenReturn(Optional.of(portfolio));

        Holding holding = new Holding(Market.US, "TQQQ", new BigDecimal("40"), new BigDecimal("70"));
        when(holdingService.getHoldings(MEMBER_ID, PORTFOLIO_ID)).thenReturn(List.of(holding));

        AssetStrategyGuide guide = heldBuyGuide("TQQQ", new BigDecimal("90"), new BigDecimal("81"));
        when(portfolioStrategyGuideService.getPortfolioStrategyGuides(MEMBER_ID, PORTFOLIO_ID))
                .thenReturn(new StrategyGuideBatch(List.of(guide), List.of()));
        when(portfolioCandidateStrategyGuideService.getCandidateStrategyGuides(MEMBER_ID, PORTFOLIO_ID))
                .thenReturn(new StrategyGuideBatch(List.of(), List.of()));

        when(portfolioValuationService.getPortfolioValuation(MEMBER_ID, PORTFOLIO_ID))
                .thenReturn(portfolioValuationWithMarketValue(new BigDecimal("10000")));

        TradePlanPreviewBatch batch = tradePlanPreviewService.getTradePlanPreview(MEMBER_ID, PORTFOLIO_ID);
        AssetTradePlanPreview plan = batch.getHeldAssetPlans().get(0);

        assertThat(plan.getStatus()).isEqualTo(TradePlanPreviewStatus.POSSIBLE_ADD_REVIEW);
        // 위험 기준 수량 = 200 / 9 = 22.222222, 종목당 최대 노출 1000 - 보유평가(40*90=3600) < 0
        // → 남은 노출 0, 최종 수량은 0으로 제한된다.
        assertThat(plan.getQuantity()).isEqualByComparingTo("0");
        assertThat(plan.getConstraints()).contains(TradePlanPreviewConstraint.SINGLE_ASSET_EXPOSURE_CAP_APPLIED);

        assertThat(plan.getPlannedActions()).hasSize(2);
        PlannedTradeAction protectiveExit = plan.getPlannedActions().get(0);
        assertThat(protectiveExit.getType()).isEqualTo(PlannedTradeActionType.PROTECTIVE_EXIT_REVIEW);
        assertThat(protectiveExit.getQuantity()).isEqualByComparingTo("40");

        PlannedTradeAction possibleAdd = plan.getPlannedActions().get(1);
        assertThat(possibleAdd.getType()).isEqualTo(PlannedTradeActionType.POSSIBLE_ADD_REVIEW);
        assertThat(possibleAdd.getTriggerPrice()).isEqualByComparingTo("90");
        assertThat(possibleAdd.getQuantity()).isEqualByComparingTo("0");
    }

    @Test
    void constrainsPossibleAddQuantityByRemainingExposureWhenRoomIsAvailable() {
        Portfolio portfolio = mock(Portfolio.class);
        PortfolioRiskPolicy riskPolicy = new PortfolioRiskPolicy(
                new BigDecimal("0.02"),
                new BigDecimal("0.5"),
                new BigDecimal("0.10")
        );
        when(portfolio.getRiskPolicy()).thenReturn(riskPolicy);
        when(portfolioRepository.findByMember_IdAndId(MEMBER_ID, PORTFOLIO_ID))
                .thenReturn(Optional.of(portfolio));

        Holding holding = new Holding(Market.US, "TQQQ", new BigDecimal("10"), new BigDecimal("70"));
        when(holdingService.getHoldings(MEMBER_ID, PORTFOLIO_ID)).thenReturn(List.of(holding));

        AssetStrategyGuide guide = heldBuyGuide("TQQQ", new BigDecimal("90"), new BigDecimal("81"));
        when(portfolioStrategyGuideService.getPortfolioStrategyGuides(MEMBER_ID, PORTFOLIO_ID))
                .thenReturn(new StrategyGuideBatch(List.of(guide), List.of()));
        when(portfolioCandidateStrategyGuideService.getCandidateStrategyGuides(MEMBER_ID, PORTFOLIO_ID))
                .thenReturn(new StrategyGuideBatch(List.of(), List.of()));

        when(portfolioValuationService.getPortfolioValuation(MEMBER_ID, PORTFOLIO_ID))
                .thenReturn(portfolioValuationWithMarketValue(new BigDecimal("10000")));

        TradePlanPreviewBatch batch = tradePlanPreviewService.getTradePlanPreview(MEMBER_ID, PORTFOLIO_ID);
        AssetTradePlanPreview plan = batch.getHeldAssetPlans().get(0);

        // 위험 기준 수량 = 200 / 9 = 22.222222
        // 종목당 최대 노출 5000 - 보유평가(10*90=900) = 4100, 남은 노출 수량 = 4100/90 = 45.555555
        // 위험 기준 수량이 더 작으므로 그 값으로 제한된다(노출 한도 제약 없음).
        assertThat(plan.getStatus()).isEqualTo(TradePlanPreviewStatus.POSSIBLE_ADD_REVIEW);
        assertThat(plan.getQuantity()).isEqualByComparingTo("22.222222");
        assertThat(plan.getConstraints()).containsExactly(TradePlanPreviewConstraint.AVAILABLE_CASH_NOT_SYNCED);
    }

    @Test
    void returnsNonActionablePossibleAddWhenHoldingDataIsMissingForBuySignalOnHeldAsset() {
        Portfolio portfolio = mock(Portfolio.class);
        PortfolioRiskPolicy riskPolicy = new PortfolioRiskPolicy(
                new BigDecimal("0.02"),
                new BigDecimal("0.5"),
                new BigDecimal("0.10")
        );
        when(portfolio.getRiskPolicy()).thenReturn(riskPolicy);
        when(portfolioRepository.findByMember_IdAndId(MEMBER_ID, PORTFOLIO_ID))
                .thenReturn(Optional.of(portfolio));

        // 보유 종목 가이드는 있지만 실제 Holding 레코드가 없는 데이터 불일치 상황이다.
        when(holdingService.getHoldings(MEMBER_ID, PORTFOLIO_ID)).thenReturn(List.of());

        AssetStrategyGuide guide = heldBuyGuide("TQQQ", new BigDecimal("90"), new BigDecimal("81"));
        when(portfolioStrategyGuideService.getPortfolioStrategyGuides(MEMBER_ID, PORTFOLIO_ID))
                .thenReturn(new StrategyGuideBatch(List.of(guide), List.of()));
        when(portfolioCandidateStrategyGuideService.getCandidateStrategyGuides(MEMBER_ID, PORTFOLIO_ID))
                .thenReturn(new StrategyGuideBatch(List.of(), List.of()));

        when(portfolioValuationService.getPortfolioValuation(MEMBER_ID, PORTFOLIO_ID))
                .thenReturn(portfolioValuationWithMarketValue(new BigDecimal("10000")));

        TradePlanPreviewBatch batch = tradePlanPreviewService.getTradePlanPreview(MEMBER_ID, PORTFOLIO_ID);
        AssetTradePlanPreview plan = batch.getHeldAssetPlans().get(0);

        assertThat(plan.getStatus()).isEqualTo(TradePlanPreviewStatus.POSSIBLE_ADD_REVIEW);
        assertThat(plan.getQuantity()).isNull();
        assertThat(plan.getAmount()).isNull();
        assertThat(plan.getPlannedActions()).hasSize(1);
        PlannedTradeAction possibleAdd = plan.getPlannedActions().get(0);
        assertThat(possibleAdd.getQuantity()).isNull();
        assertThat(possibleAdd.getAmount()).isNull();
    }

    @Test
    void returnsNotReadyForBuySignalOnHeldAssetWhenRiskPolicyIsMissing() {
        Portfolio portfolio = mock(Portfolio.class);
        when(portfolio.getRiskPolicy()).thenReturn(null);
        when(portfolioRepository.findByMember_IdAndId(MEMBER_ID, PORTFOLIO_ID))
                .thenReturn(Optional.of(portfolio));

        Holding holding = new Holding(Market.US, "TQQQ", new BigDecimal("10"), new BigDecimal("70"));
        when(holdingService.getHoldings(MEMBER_ID, PORTFOLIO_ID)).thenReturn(List.of(holding));

        AssetStrategyGuide guide = heldBuyGuide("TQQQ", new BigDecimal("90"), new BigDecimal("81"));
        when(portfolioStrategyGuideService.getPortfolioStrategyGuides(MEMBER_ID, PORTFOLIO_ID))
                .thenReturn(new StrategyGuideBatch(List.of(guide), List.of()));
        when(portfolioCandidateStrategyGuideService.getCandidateStrategyGuides(MEMBER_ID, PORTFOLIO_ID))
                .thenReturn(new StrategyGuideBatch(List.of(), List.of()));

        TradePlanPreviewBatch batch = tradePlanPreviewService.getTradePlanPreview(MEMBER_ID, PORTFOLIO_ID);
        AssetTradePlanPreview plan = batch.getHeldAssetPlans().get(0);

        assertThat(plan.getStatus()).isEqualTo(TradePlanPreviewStatus.NOT_READY);
        assertThat(plan.getNotReadyReason()).isEqualTo(TradePlanPreviewNotReadyReason.MISSING_RISK_POLICY);

        verify(portfolioValuationService, never()).getPortfolioValuation(any(), any());
    }

    @Test
    void returnsSellReviewForHeldAssetWithoutStopLossTrigger() {
        Portfolio portfolio = mock(Portfolio.class);
        when(portfolioRepository.findByMember_IdAndId(MEMBER_ID, PORTFOLIO_ID))
                .thenReturn(Optional.of(portfolio));

        Holding holding = new Holding(Market.US, "SOXL", new BigDecimal("50"), new BigDecimal("90"));
        when(holdingService.getHoldings(MEMBER_ID, PORTFOLIO_ID)).thenReturn(List.of(holding));

        AssetStrategyGuide guide = heldSellGuide(
                "SOXL",
                new BigDecimal("95.00"),
                new BigDecimal("81.00")
        );
        when(portfolioStrategyGuideService.getPortfolioStrategyGuides(MEMBER_ID, PORTFOLIO_ID))
                .thenReturn(new StrategyGuideBatch(List.of(guide), List.of()));
        when(portfolioCandidateStrategyGuideService.getCandidateStrategyGuides(MEMBER_ID, PORTFOLIO_ID))
                .thenReturn(new StrategyGuideBatch(List.of(), List.of()));

        TradePlanPreviewBatch batch = tradePlanPreviewService.getTradePlanPreview(MEMBER_ID, PORTFOLIO_ID);
        AssetTradePlanPreview plan = batch.getHeldAssetPlans().get(0);

        assertThat(plan.getStatus()).isEqualTo(TradePlanPreviewStatus.SELL_REVIEW);
        assertThat(plan.getQuantity()).isNull();
    }

    private AssetStrategyGuide candidateBuyGuide(String ticker, BigDecimal referencePrice, BigDecimal stopLossPrice) {
        StrategySignal signal = signal(referencePrice, StrategyTrend.ABOVE_LONG_AVERAGE, 2);
        StrategyDecisionGuidance guidance = new StrategyDecisionGuidance(
                "ELIGIBLE_NOW",
                "신규 진입을 검토할 수 있습니다.",
                "USER_DEFINED",
                new BigDecimal("0.10"),
                stopLossPrice,
                "검토용 손절가입니다."
        );
        StrategyDecision decision = new StrategyDecision(StrategyAction.BUY, "테스트 매수 판단", signal, guidance);

        return new AssetStrategyGuide(Market.US, ticker, decision);
    }

    private AssetStrategyGuide candidateBuyGuideWithoutStopLoss(String ticker, BigDecimal referencePrice) {
        StrategySignal signal = signal(referencePrice, StrategyTrend.ABOVE_LONG_AVERAGE, 2);
        StrategyDecision decision = new StrategyDecision(StrategyAction.BUY, "테스트 매수 판단", signal);

        return new AssetStrategyGuide(Market.US, ticker, decision);
    }

    private AssetStrategyGuide heldSellGuide(String ticker, BigDecimal referencePrice, BigDecimal stopLossPrice) {
        StrategySignal signal = signal(referencePrice, StrategyTrend.BELOW_LONG_AVERAGE, null);
        StrategyDecisionGuidance guidance = new StrategyDecisionGuidance(
                "HOLDING",
                "신규 매수 시점이 아닙니다.",
                "USER_DEFINED",
                new BigDecimal("0.10"),
                stopLossPrice,
                "검토용 손절가입니다."
        );
        StrategyDecision decision = new StrategyDecision(StrategyAction.SELL, "테스트 매도 판단", signal, guidance);

        return new AssetStrategyGuide(Market.US, ticker, decision);
    }

    /**
     * 이미 보유 중인 종목에 Track A 신규 매수 신호(상승 추세 + 최근 교차 후 0~4주)가
     * 발생한 시나리오다. {@link StrategyDecisionMaker#decideForHolding}이 이 조건에서
     * 실제로 BUY를 반환한다는 점은 {@code StrategyDecisionMakerTest}와
     * {@code PortfolioStrategyGuideServiceTest}에서 검증하며, 이 서비스는 그 BUY를
     * 매도가 아닌 포지션 추가 검토로 처리해야 한다.
     */
    private AssetStrategyGuide heldBuyGuide(String ticker, BigDecimal referencePrice, BigDecimal stopLossPrice) {
        StrategySignal signal = signal(referencePrice, StrategyTrend.ABOVE_LONG_AVERAGE, 2);
        StrategyDecisionGuidance guidance = new StrategyDecisionGuidance(
                "ELIGIBLE_NOW",
                "이미 보유 중인 종목에 신규 매수 신호가 발생했습니다.",
                "USER_DEFINED",
                new BigDecimal("0.10"),
                stopLossPrice,
                "검토용 손절가입니다."
        );
        StrategyDecision decision = new StrategyDecision(StrategyAction.BUY, "테스트 보유 종목 매수 신호", signal, guidance);

        return new AssetStrategyGuide(Market.US, ticker, decision);
    }

    private AssetStrategyGuide heldHoldGuide(String ticker, BigDecimal referencePrice, BigDecimal stopLossPrice) {
        StrategySignal signal = signal(referencePrice, StrategyTrend.ABOVE_LONG_AVERAGE, 8);
        StrategyDecisionGuidance guidance = new StrategyDecisionGuidance(
                "HOLDING",
                "신규 매수 시점이 아닙니다.",
                "USER_DEFINED",
                new BigDecimal("0.10"),
                stopLossPrice,
                "검토용 손절가입니다."
        );
        StrategyDecision decision = new StrategyDecision(StrategyAction.HOLD, "테스트 보유 판단", signal, guidance);

        return new AssetStrategyGuide(Market.US, ticker, decision);
    }

    private StrategySignal signal(BigDecimal referencePrice, StrategyTrend trend, Integer weeksSinceCross) {
        return new StrategySignal(
                referencePrice,
                "테스트 시장 신호",
                new StrategyMetadata("test-strategy", "test-v1", LocalDate.of(2026, 8, 10)),
                trend,
                StrategySignalEvent.NONE,
                weeksSinceCross
        );
    }

    private PortfolioValuation portfolioValuationWithMarketValue(BigDecimal totalMarketValue) {
        return new PortfolioValuation(
                List.of(),
                BigDecimal.ZERO,
                totalMarketValue,
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );
    }

    /**
     * 실시간 손절 판정 테스트용으로, 지정한 종목의 현재가만 있는 최소 평가 결과를 만든다.
     * 총 평가액은 이 테스트들의 관심사가 아니므로 0으로 둔다.
     */
    private PortfolioValuation portfolioValuationWithCurrentPrice(String ticker, BigDecimal currentPrice) {
        HoldingValuation holdingValuation = new HoldingValuation(
                Market.US,
                ticker,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                currentPrice,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );
        return new PortfolioValuation(
                List.of(holdingValuation),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );
    }
}
