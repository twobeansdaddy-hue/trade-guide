package com.tradeguide.domain.strategy;

import com.tradeguide.domain.trade.Market;

import java.math.BigDecimal;
import java.util.List;

/**
 * 한 종목에 대한 검토용 매매 계획 미리보기다. 어떤 상태도 주문 전송, 저장, 확정된 체결을
 * 뜻하지 않는다 - 사용자가 직접 검토하고 확인해야 하는 초안이며 항상
 * {@link #isRequiresUserConfirmation()}이 {@code true}다.
 */
public class AssetTradePlanPreview {

    private final Market market;
    private final String ticker;
    private final TradePlanPreviewStatus status;
    private final TradePlanPreviewNotReadyReason notReadyReason;
    private final BigDecimal referencePrice;
    private final BigDecimal stopLossPrice;
    private final BigDecimal quantity;
    private final BigDecimal amount;
    private final BigDecimal estimatedMaxLoss;
    private final List<TradePlanPreviewConstraint> constraints;
    private final String reason;
    private final StrategyMetadata strategyMetadata;
    private final List<PlannedTradeAction> plannedActions;

    public AssetTradePlanPreview(
            Market market,
            String ticker,
            TradePlanPreviewStatus status,
            TradePlanPreviewNotReadyReason notReadyReason,
            BigDecimal referencePrice,
            BigDecimal stopLossPrice,
            BigDecimal quantity,
            BigDecimal amount,
            BigDecimal estimatedMaxLoss,
            List<TradePlanPreviewConstraint> constraints,
            String reason,
            StrategyMetadata strategyMetadata
    ) {
        this(
                market,
                ticker,
                status,
                notReadyReason,
                referencePrice,
                stopLossPrice,
                quantity,
                amount,
                estimatedMaxLoss,
                constraints,
                reason,
                strategyMetadata,
                List.of()
        );
    }

    public AssetTradePlanPreview(
            Market market,
            String ticker,
            TradePlanPreviewStatus status,
            TradePlanPreviewNotReadyReason notReadyReason,
            BigDecimal referencePrice,
            BigDecimal stopLossPrice,
            BigDecimal quantity,
            BigDecimal amount,
            BigDecimal estimatedMaxLoss,
            List<TradePlanPreviewConstraint> constraints,
            String reason,
            StrategyMetadata strategyMetadata,
            List<PlannedTradeAction> plannedActions
    ) {
        if (market == null) {
            throw new IllegalArgumentException("시장은 필수입니다.");
        }

        if (ticker == null || ticker.isBlank()) {
            throw new IllegalArgumentException("티커는 비어 있을 수 없습니다.");
        }

        if (status == null) {
            throw new IllegalArgumentException("매매 계획 미리보기 상태는 필수입니다.");
        }

        if (status == TradePlanPreviewStatus.NOT_READY && notReadyReason == null) {
            throw new IllegalArgumentException("준비되지 않은 상태에는 사유가 필요합니다.");
        }

        if (status != TradePlanPreviewStatus.NOT_READY && notReadyReason != null) {
            throw new IllegalArgumentException("준비되지 않은 상태가 아니면 미준비 사유를 둘 수 없습니다.");
        }

        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("판단 근거는 비어 있을 수 없습니다.");
        }

        if (strategyMetadata == null) {
            throw new IllegalArgumentException("전략 메타데이터는 필수입니다.");
        }

        this.market = market;
        this.ticker = ticker;
        this.status = status;
        this.notReadyReason = notReadyReason;
        this.referencePrice = referencePrice;
        this.stopLossPrice = stopLossPrice;
        this.quantity = quantity;
        this.amount = amount;
        this.estimatedMaxLoss = estimatedMaxLoss;
        this.constraints = constraints == null ? List.of() : List.copyOf(constraints);
        this.reason = reason;
        this.strategyMetadata = strategyMetadata;
        this.plannedActions = plannedActions == null ? List.of() : List.copyOf(plannedActions);
    }

    public Market getMarket() {
        return market;
    }

    public String getTicker() {
        return ticker;
    }

    public TradePlanPreviewStatus getStatus() {
        return status;
    }

    public TradePlanPreviewNotReadyReason getNotReadyReason() {
        return notReadyReason;
    }

    public BigDecimal getReferencePrice() {
        return referencePrice;
    }

    public BigDecimal getStopLossPrice() {
        return stopLossPrice;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getEstimatedMaxLoss() {
        return estimatedMaxLoss;
    }

    public List<TradePlanPreviewConstraint> getConstraints() {
        return constraints;
    }

    public String getReason() {
        return reason;
    }

    public StrategyMetadata getStrategyMetadata() {
        return strategyMetadata;
    }

    /**
     * 이 종목에 대한 구체적 행동 초안을 우선순위 순서대로 담은 목록이다. 비어 있을 수
     * 있으며, 각 항목도 어떤 상태도 자동 주문 전송을 뜻하지 않는다.
     */
    public List<PlannedTradeAction> getPlannedActions() {
        return plannedActions;
    }

    /**
     * 이 미리보기는 항상 사용자 확인이 필요한 검토용 초안이다. 어떤 상태도 자동 주문
     * 전송이나 확정된 계획을 뜻하지 않는다.
     */
    public boolean isRequiresUserConfirmation() {
        return true;
    }
}
