package com.tradeguide.service.strategy;

import com.tradeguide.domain.holding.Holding;
import com.tradeguide.domain.portfolio.Portfolio;
import com.tradeguide.domain.risk.PortfolioRiskPolicy;
import com.tradeguide.domain.strategy.AssetStrategyGuide;
import com.tradeguide.domain.strategy.AssetTradePlanPreview;
import com.tradeguide.domain.strategy.PlannedTradeAction;
import com.tradeguide.domain.strategy.PlannedTradeActionType;
import com.tradeguide.domain.strategy.StrategyAction;
import com.tradeguide.domain.strategy.StrategyDecision;
import com.tradeguide.domain.strategy.StrategyDecisionGuidance;
import com.tradeguide.domain.strategy.StrategyGuideBatch;
import com.tradeguide.domain.strategy.StrategyMetadata;
import com.tradeguide.domain.strategy.StrategySignal;
import com.tradeguide.domain.strategy.TradePlanPreviewBatch;
import com.tradeguide.domain.strategy.TradePlanPreviewConstraint;
import com.tradeguide.domain.strategy.TradePlanPreviewNotReadyReason;
import com.tradeguide.domain.strategy.TradePlanPreviewStatus;
import com.tradeguide.domain.strategy.UnavailableAsset;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.domain.valuation.PortfolioValuation;
import com.tradeguide.exception.PortfolioNotFoundException;
import com.tradeguide.repository.portfolio.PortfolioRepository;
import com.tradeguide.service.holding.HoldingService;
import com.tradeguide.service.valuation.PortfolioValuationService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Track A 전략 가이드를 바탕으로 주문을 전송하지 않는 검토용 매매 계획을 계산한다.
 * 매수 후보는 사용자 설정 손절가와 포트폴리오 위험 한도로 위험 기준 수량을 계산하고,
 * 보유 종목은 손절 기준 가격 도달 여부를 별도로 표시한다. 각 종목은 기존 요약 필드에
 * 더해, 손절가·부분 이익·포지션 추가에 대한 구체적 행동 초안을 담은
 * {@link com.tradeguide.domain.strategy.PlannedTradeAction} 순서 목록도 함께 반환한다.
 * 이 서비스는 어떤 계획도 저장하거나 증권사에 전송하지 않으며,
 * {@link com.tradeguide.domain.strategy.TradePlan}의 증권사 전송 준비 상태에도 관여하지
 * 않는다.
 */
@Service
public class TradePlanPreviewService {

    private static final int QUANTITY_SCALE = 6;
    private static final int AMOUNT_SCALE = 2;

    private static final String MISSING_RISK_POLICY_REASON =
            "포트폴리오 위험 한도 정책이 설정되지 않아 검토용 매수 계획을 계산할 수 없습니다.";
    private static final String MISSING_STOP_LOSS_REASON =
            "사용자 설정 손절가가 없어 위험 기준 매수 수량을 계산할 수 없습니다.";
    private static final String BUY_PLAN_REASON =
            "포트폴리오 위험 한도와 사용자 설정 손절가를 기준으로 계산한 검토용 매수 계획입니다. "
                    + "실제 주문 여부와 수량은 사용자가 최종 확인해야 합니다.";
    private static final String STOP_LOSS_EXIT_REVIEW_REASON =
            "전략 기준 가격이 사용자 설정 손절가에 도달해 보유 수량 전체에 대한 손절 검토가 필요합니다. "
                    + "이 계획은 검토용이며 자동으로 주문을 전송하지 않습니다.";
    private static final String SELL_REVIEW_REASON =
            "손절 기준 도달과 무관하게 하락 추세에 따라 보유 수량 매도를 검토합니다. "
                    + "임의로 산출한 매도 수량은 제공하지 않습니다.";
    private static final String PROTECTIVE_EXIT_ACTION_REASON =
            "사용자 설정 손절가에 도달하면 보유 수량 전체의 청산을 검토합니다. "
                    + "이 계획은 검토용이며 자동으로 주문을 전송하지 않고, 시세 스냅샷이 갱신되면 만료됩니다.";
    private static final String PARTIAL_PROFIT_NOT_REGISTERED_REASON =
            "현재 가격이 평균 매입가 대비 이익 구간에 있어 부분 이익 실현을 검토할 수 있으나, "
                    + "검증·백테스트된 익절 목표가 규칙이 아직 등록되지 않아 목표가와 수량을 제시하지 않습니다. "
                    + "이 항목은 검토용 참고이며 시세 스냅샷이 갱신되면 만료됩니다.";
    private static final String POSSIBLE_ADD_REASON =
            "이미 보유 중인 종목에 Track A 신규 매수 신호가 발생해 매도가 아니라 포지션 추가를 검토합니다. "
                    + "수량은 남은 종목당 노출 한도와 주문당 최대 손실 한도로 제한한 값이며, "
                    + "가용 현금 잔고는 아직 확인되지 않았습니다. 이 계획은 검토용이며 시세 스냅샷이 갱신되면 만료됩니다.";
    private static final String POSSIBLE_ADD_MISSING_RISK_POLICY_REASON =
            "포트폴리오 위험 한도 정책이 설정되지 않아 포지션 추가 검토 수량을 계산할 수 없습니다.";
    private static final String POSSIBLE_ADD_MISSING_STOP_LOSS_REASON =
            "사용자 설정 손절가가 없어 포지션 추가 검토 수량을 계산할 수 없습니다.";
    private static final String POSSIBLE_ADD_NOT_ACTIONABLE_REASON =
            "보유 수량 정보가 없거나 손절가가 전략 기준 가격보다 낮지 않아 포지션 추가 검토 수량을 계산할 수 없습니다. "
                    + "이 계획은 검토용이며 임의의 수량을 산출하지 않습니다.";

    private final PortfolioStrategyGuideService portfolioStrategyGuideService;
    private final PortfolioCandidateStrategyGuideService portfolioCandidateStrategyGuideService;
    private final PortfolioValuationService portfolioValuationService;
    private final HoldingService holdingService;
    private final PortfolioRepository portfolioRepository;

    public TradePlanPreviewService(
            PortfolioStrategyGuideService portfolioStrategyGuideService,
            PortfolioCandidateStrategyGuideService portfolioCandidateStrategyGuideService,
            PortfolioValuationService portfolioValuationService,
            HoldingService holdingService,
            PortfolioRepository portfolioRepository
    ) {
        this.portfolioStrategyGuideService = portfolioStrategyGuideService;
        this.portfolioCandidateStrategyGuideService = portfolioCandidateStrategyGuideService;
        this.portfolioValuationService = portfolioValuationService;
        this.holdingService = holdingService;
        this.portfolioRepository = portfolioRepository;
    }

    public TradePlanPreviewBatch getTradePlanPreview(Long memberId, Long portfolioId) {
        Portfolio portfolio = portfolioRepository.findByMember_IdAndId(memberId, portfolioId)
                .orElseThrow(() -> new PortfolioNotFoundException("포트폴리오를 찾을 수 없습니다."));

        PortfolioRiskPolicy riskPolicy = portfolio.getRiskPolicy();

        StrategyGuideBatch heldBatch = portfolioStrategyGuideService
                .getPortfolioStrategyGuides(memberId, portfolioId);
        StrategyGuideBatch candidateBatch = portfolioCandidateStrategyGuideService
                .getCandidateStrategyGuides(memberId, portfolioId);

        Map<String, Holding> holdingsByKey = new HashMap<>();
        for (Holding holding : holdingService.getHoldings(memberId, portfolioId)) {
            holdingsByKey.put(key(holding.getMarket(), holding.getTicker()), holding);
        }

        BigDecimal portfolioMarketValue = null;
        if (riskPolicy != null && riskPolicy.getStopLossRatio() != null) {
            PortfolioValuation valuation = portfolioValuationService
                    .getPortfolioValuation(memberId, portfolioId);
            portfolioMarketValue = valuation.getTotalMarketValue();
        }

        List<AssetTradePlanPreview> heldAssetPlans = new ArrayList<>();
        for (AssetStrategyGuide guide : heldBatch.getGuides()) {
            heldAssetPlans.add(buildHeldAssetPlan(guide, holdingsByKey, riskPolicy, portfolioMarketValue));
        }

        List<AssetTradePlanPreview> candidatePlans = new ArrayList<>();
        for (AssetStrategyGuide guide : candidateBatch.getGuides()) {
            candidatePlans.add(buildCandidatePlan(guide, riskPolicy, portfolioMarketValue));
        }

        List<UnavailableAsset> unavailableAssets = new ArrayList<>();
        unavailableAssets.addAll(heldBatch.getUnavailableAssets());
        unavailableAssets.addAll(candidateBatch.getUnavailableAssets());

        return new TradePlanPreviewBatch(candidatePlans, heldAssetPlans, unavailableAssets);
    }

    private AssetTradePlanPreview buildHeldAssetPlan(
            AssetStrategyGuide guide,
            Map<String, Holding> holdingsByKey,
            PortfolioRiskPolicy riskPolicy,
            BigDecimal portfolioMarketValue
    ) {
        StrategyDecision decision = guide.getStrategyDecision();
        StrategySignal signal = decision.getSignal();
        StrategyDecisionGuidance guidance = decision.getGuidance();
        StrategyMetadata metadata = signal.getMetadata();
        BigDecimal referencePrice = signal.getReferencePrice();
        BigDecimal stopLossPrice = guidance.getStopLossPrice();

        Holding holding = holdingsByKey.get(key(guide.getMarket(), guide.getTicker()));
        BigDecimal heldQuantity = holding == null ? null : holding.getQuantity();
        boolean hasValidHolding = heldQuantity != null && heldQuantity.compareTo(BigDecimal.ZERO) > 0;

        BigDecimal averagePurchasePrice = holding == null ? null : holding.getAveragePurchasePrice();
        boolean hasValidAveragePurchasePrice = averagePurchasePrice != null
                && averagePurchasePrice.compareTo(BigDecimal.ZERO) > 0;

        List<PlannedTradeAction> plannedActions = new ArrayList<>();
        if (hasValidHolding && stopLossPrice != null) {
            plannedActions.add(buildProtectiveExitAction(stopLossPrice, heldQuantity, metadata));
        }

        if (decision.getAction() == StrategyAction.BUY) {
            return buildPossibleAddPlan(
                    guide,
                    riskPolicy,
                    portfolioMarketValue,
                    referencePrice,
                    stopLossPrice,
                    heldQuantity,
                    hasValidHolding,
                    metadata,
                    plannedActions
            );
        }

        boolean stopLossTriggered = stopLossPrice != null
                && referencePrice != null
                && referencePrice.compareTo(stopLossPrice) <= 0;
        boolean isProfitable = hasValidAveragePurchasePrice
                && referencePrice != null
                && referencePrice.compareTo(averagePurchasePrice) > 0;

        if (decision.getAction() == StrategyAction.HOLD) {
            if (hasValidHolding && isProfitable) {
                plannedActions.add(buildPartialProfitNotRegisteredAction(metadata));
            }

            return new AssetTradePlanPreview(
                    guide.getMarket(),
                    guide.getTicker(),
                    TradePlanPreviewStatus.HOLD,
                    null,
                    referencePrice,
                    stopLossPrice,
                    null,
                    null,
                    null,
                    List.of(),
                    decision.getReason(),
                    metadata,
                    plannedActions
            );
        }

        if (stopLossTriggered) {
            return new AssetTradePlanPreview(
                    guide.getMarket(),
                    guide.getTicker(),
                    TradePlanPreviewStatus.STOP_LOSS_EXIT_REVIEW,
                    null,
                    referencePrice,
                    stopLossPrice,
                    heldQuantity,
                    null,
                    null,
                    List.of(),
                    STOP_LOSS_EXIT_REVIEW_REASON,
                    metadata,
                    plannedActions
            );
        }

        if (hasValidHolding && isProfitable) {
            plannedActions.add(buildPartialProfitNotRegisteredAction(metadata));
        }

        return new AssetTradePlanPreview(
                guide.getMarket(),
                guide.getTicker(),
                TradePlanPreviewStatus.SELL_REVIEW,
                null,
                referencePrice,
                stopLossPrice,
                null,
                null,
                null,
                List.of(),
                SELL_REVIEW_REASON,
                metadata,
                plannedActions
        );
    }

    /**
     * 사용자 설정 손절가가 있는 보유 종목이면 현재 가격 도달 여부와 무관하게 항상 전량
     * 청산 검토 행동을 제시한다.
     */
    private PlannedTradeAction buildProtectiveExitAction(
            BigDecimal stopLossPrice,
            BigDecimal heldQuantity,
            StrategyMetadata metadata
    ) {
        BigDecimal amount = heldQuantity.multiply(stopLossPrice).setScale(AMOUNT_SCALE, RoundingMode.DOWN);
        return new PlannedTradeAction(
                PlannedTradeActionType.PROTECTIVE_EXIT_REVIEW,
                stopLossPrice,
                heldQuantity,
                amount,
                PROTECTIVE_EXIT_ACTION_REASON,
                metadata
        );
    }

    /**
     * 유효한 평균 매입가가 있고 전략 기준 가격이 평균 매입가보다 높은(실제 이익 구간에
     * 있는) 보유 포지션에서 부분 이익 실현을 검토할 수 있음을 알린다. 검증·백테스트된
     * 목표가 규칙이 아직 등록되지 않아 목표가와 수량은 산출하지 않는다.
     */
    private PlannedTradeAction buildPartialProfitNotRegisteredAction(StrategyMetadata metadata) {
        return new PlannedTradeAction(
                PlannedTradeActionType.PARTIAL_PROFIT_REVIEW,
                null,
                null,
                null,
                PARTIAL_PROFIT_NOT_REGISTERED_REASON,
                metadata
        );
    }

    /**
     * 이미 보유 중인 종목에 Track A 신규 매수 신호가 발생한 경우, 매도가 아니라 포지션
     * 추가 검토로 다룬다. 위험 한도나 손절가가 없으면 {@link TradePlanPreviewStatus#NOT_READY}로
     * 명시적 사유를 반환하고, 계산에 필요한 데이터(보유 수량, 포트폴리오 평가액, 유효한
     * 위험폭)가 없으면 가격·수량을 만들어 내지 않는 비실행 항목으로 반환한다.
     */
    private AssetTradePlanPreview buildPossibleAddPlan(
            AssetStrategyGuide guide,
            PortfolioRiskPolicy riskPolicy,
            BigDecimal portfolioMarketValue,
            BigDecimal referencePrice,
            BigDecimal stopLossPrice,
            BigDecimal heldQuantity,
            boolean hasValidHolding,
            StrategyMetadata metadata,
            List<PlannedTradeAction> plannedActions
    ) {
        if (riskPolicy == null) {
            return new AssetTradePlanPreview(
                    guide.getMarket(),
                    guide.getTicker(),
                    TradePlanPreviewStatus.NOT_READY,
                    TradePlanPreviewNotReadyReason.MISSING_RISK_POLICY,
                    referencePrice,
                    stopLossPrice,
                    null,
                    null,
                    null,
                    List.of(),
                    POSSIBLE_ADD_MISSING_RISK_POLICY_REASON,
                    metadata,
                    plannedActions
            );
        }

        if (stopLossPrice == null) {
            return new AssetTradePlanPreview(
                    guide.getMarket(),
                    guide.getTicker(),
                    TradePlanPreviewStatus.NOT_READY,
                    TradePlanPreviewNotReadyReason.MISSING_STOP_LOSS,
                    referencePrice,
                    null,
                    null,
                    null,
                    null,
                    List.of(),
                    POSSIBLE_ADD_MISSING_STOP_LOSS_REASON,
                    metadata,
                    plannedActions
            );
        }

        AddPlanCalculation calculation = null;
        if (hasValidHolding
                && portfolioMarketValue != null
                && referencePrice != null
                && referencePrice.compareTo(stopLossPrice) > 0) {
            calculation = calculateAddPlan(riskPolicy, portfolioMarketValue, referencePrice, stopLossPrice, heldQuantity);
        }

        if (calculation == null) {
            plannedActions.add(new PlannedTradeAction(
                    PlannedTradeActionType.POSSIBLE_ADD_REVIEW,
                    referencePrice,
                    null,
                    null,
                    POSSIBLE_ADD_NOT_ACTIONABLE_REASON,
                    metadata
            ));

            return new AssetTradePlanPreview(
                    guide.getMarket(),
                    guide.getTicker(),
                    TradePlanPreviewStatus.POSSIBLE_ADD_REVIEW,
                    null,
                    referencePrice,
                    stopLossPrice,
                    null,
                    null,
                    null,
                    List.of(),
                    POSSIBLE_ADD_NOT_ACTIONABLE_REASON,
                    metadata,
                    plannedActions
            );
        }

        plannedActions.add(new PlannedTradeAction(
                PlannedTradeActionType.POSSIBLE_ADD_REVIEW,
                referencePrice,
                calculation.quantity(),
                calculation.amount(),
                POSSIBLE_ADD_REASON,
                metadata
        ));

        return new AssetTradePlanPreview(
                guide.getMarket(),
                guide.getTicker(),
                TradePlanPreviewStatus.POSSIBLE_ADD_REVIEW,
                null,
                referencePrice,
                stopLossPrice,
                calculation.quantity(),
                calculation.amount(),
                calculation.estimatedMaxLoss(),
                calculation.constraints(),
                POSSIBLE_ADD_REASON,
                metadata,
                plannedActions
        );
    }

    /**
     * 추가 매수 검토 수량 = min(위험 금액 / 손절폭, 남은 종목당 노출 한도 / 전략 기준 가격).
     * 남은 노출 한도는 종목당 최대 노출 금액에서 현재 보유 평가 금액(보유 수량 * 전략
     * 기준 가격)을 뺀 값이며 음수가 되지 않는다. 가용 현금 잔고는 신뢰할 수 있는 공급자
     * 간 계약이 아니므로 항상 {@link TradePlanPreviewConstraint#AVAILABLE_CASH_NOT_SYNCED}를
     * 반환한다.
     */
    private AddPlanCalculation calculateAddPlan(
            PortfolioRiskPolicy riskPolicy,
            BigDecimal portfolioMarketValue,
            BigDecimal referencePrice,
            BigDecimal stopLossPrice,
            BigDecimal heldQuantity
    ) {
        BigDecimal stopWidth = referencePrice.subtract(stopLossPrice);

        BigDecimal riskAmount = portfolioMarketValue.multiply(riskPolicy.getMaxLossPerTradeRatio());
        BigDecimal riskLimitedQuantity = riskAmount.divide(stopWidth, QUANTITY_SCALE, RoundingMode.DOWN);

        BigDecimal maxExposureAmount = portfolioMarketValue.multiply(riskPolicy.getMaxSingleAssetExposureRatio());
        BigDecimal currentHoldingValue = heldQuantity.multiply(referencePrice);
        BigDecimal remainingExposureAmount = maxExposureAmount.subtract(currentHoldingValue);
        if (remainingExposureAmount.compareTo(BigDecimal.ZERO) < 0) {
            remainingExposureAmount = BigDecimal.ZERO;
        }
        BigDecimal remainingExposureQuantity = remainingExposureAmount
                .divide(referencePrice, QUANTITY_SCALE, RoundingMode.DOWN);

        BigDecimal quantity = riskLimitedQuantity.min(remainingExposureQuantity);

        List<TradePlanPreviewConstraint> constraints = new ArrayList<>();
        constraints.add(TradePlanPreviewConstraint.AVAILABLE_CASH_NOT_SYNCED);
        if (quantity.compareTo(riskLimitedQuantity) < 0) {
            constraints.add(TradePlanPreviewConstraint.SINGLE_ASSET_EXPOSURE_CAP_APPLIED);
        }

        BigDecimal amount = quantity.multiply(referencePrice).setScale(AMOUNT_SCALE, RoundingMode.DOWN);
        BigDecimal estimatedMaxLoss = quantity.multiply(stopWidth).setScale(AMOUNT_SCALE, RoundingMode.DOWN);

        return new AddPlanCalculation(quantity, amount, estimatedMaxLoss, constraints);
    }

    private record AddPlanCalculation(
            BigDecimal quantity,
            BigDecimal amount,
            BigDecimal estimatedMaxLoss,
            List<TradePlanPreviewConstraint> constraints
    ) {
    }

    private AssetTradePlanPreview buildCandidatePlan(
            AssetStrategyGuide guide,
            PortfolioRiskPolicy riskPolicy,
            BigDecimal portfolioMarketValue
    ) {
        StrategyDecision decision = guide.getStrategyDecision();
        StrategySignal signal = decision.getSignal();
        StrategyDecisionGuidance guidance = decision.getGuidance();
        StrategyMetadata metadata = signal.getMetadata();

        if (decision.getAction() != StrategyAction.BUY) {
            return new AssetTradePlanPreview(
                    guide.getMarket(),
                    guide.getTicker(),
                    TradePlanPreviewStatus.WATCH,
                    null,
                    signal.getReferencePrice(),
                    guidance.getStopLossPrice(),
                    null,
                    null,
                    null,
                    List.of(),
                    decision.getReason(),
                    metadata
            );
        }

        if (riskPolicy == null) {
            return new AssetTradePlanPreview(
                    guide.getMarket(),
                    guide.getTicker(),
                    TradePlanPreviewStatus.NOT_READY,
                    TradePlanPreviewNotReadyReason.MISSING_RISK_POLICY,
                    signal.getReferencePrice(),
                    null,
                    null,
                    null,
                    null,
                    List.of(),
                    MISSING_RISK_POLICY_REASON,
                    metadata
            );
        }

        if (guidance.getStopLossPrice() == null) {
            return new AssetTradePlanPreview(
                    guide.getMarket(),
                    guide.getTicker(),
                    TradePlanPreviewStatus.NOT_READY,
                    TradePlanPreviewNotReadyReason.MISSING_STOP_LOSS,
                    signal.getReferencePrice(),
                    null,
                    null,
                    null,
                    null,
                    List.of(),
                    MISSING_STOP_LOSS_REASON,
                    metadata
            );
        }

        return buildBuyPlan(guide, riskPolicy, portfolioMarketValue, signal, guidance, metadata);
    }

    /**
     * 위험 기준 수량 = 포트폴리오 평가액 * 주문당 최대 손실 비율 / (전략 기준 가격 - 손절가).
     * 후보는 보유 중이 아니므로 남은 종목당 노출 한도는 항상 종목당 최대 노출 비율 전체이며,
     * 이를 초과하면 그 한도로 수량을 줄인다. 가용 현금 잔고는 아직 공급자 간 신뢰할 수 있는
     * 계약이 아니므로 항상 {@link TradePlanPreviewConstraint#AVAILABLE_CASH_NOT_SYNCED}를
     * 반환하고 매수 가능 여부를 임의로 가정하지 않는다.
     */
    private AssetTradePlanPreview buildBuyPlan(
            AssetStrategyGuide guide,
            PortfolioRiskPolicy riskPolicy,
            BigDecimal portfolioMarketValue,
            StrategySignal signal,
            StrategyDecisionGuidance guidance,
            StrategyMetadata metadata
    ) {
        BigDecimal referencePrice = signal.getReferencePrice();
        BigDecimal stopLossPrice = guidance.getStopLossPrice();
        BigDecimal stopWidth = referencePrice.subtract(stopLossPrice);

        BigDecimal riskAmount = portfolioMarketValue.multiply(riskPolicy.getMaxLossPerTradeRatio());
        BigDecimal riskLimitedQuantity = riskAmount.divide(stopWidth, QUANTITY_SCALE, RoundingMode.DOWN);

        BigDecimal maxExposureAmount = portfolioMarketValue
                .multiply(riskPolicy.getMaxSingleAssetExposureRatio());
        BigDecimal maxExposureQuantity = maxExposureAmount
                .divide(referencePrice, QUANTITY_SCALE, RoundingMode.DOWN);

        BigDecimal quantity = riskLimitedQuantity.min(maxExposureQuantity);

        List<TradePlanPreviewConstraint> constraints = new ArrayList<>();
        constraints.add(TradePlanPreviewConstraint.AVAILABLE_CASH_NOT_SYNCED);
        if (quantity.compareTo(riskLimitedQuantity) < 0) {
            constraints.add(TradePlanPreviewConstraint.SINGLE_ASSET_EXPOSURE_CAP_APPLIED);
        }

        BigDecimal amount = quantity.multiply(referencePrice).setScale(AMOUNT_SCALE, RoundingMode.DOWN);
        BigDecimal estimatedMaxLoss = quantity.multiply(stopWidth).setScale(AMOUNT_SCALE, RoundingMode.DOWN);

        return new AssetTradePlanPreview(
                guide.getMarket(),
                guide.getTicker(),
                TradePlanPreviewStatus.BUY,
                null,
                referencePrice,
                stopLossPrice,
                quantity,
                amount,
                estimatedMaxLoss,
                constraints,
                BUY_PLAN_REASON,
                metadata
        );
    }

    private String key(Market market, String ticker) {
        return market.name() + ":" + ticker;
    }
}
