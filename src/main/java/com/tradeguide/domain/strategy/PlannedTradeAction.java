package com.tradeguide.domain.strategy;

import java.math.BigDecimal;

/**
 * 종목 검토용 매매 계획 미리보기에 속한 구체적 행동 초안 한 건이다. 트리거 가격, 권장
 * 수량, 계산 가능할 때의 금액, 판단 근거, 전략 메타데이터를 담는다. 어떤 인스턴스도
 * 증권사 주문 전송이나 저장을 뜻하지 않으며 항상 {@link #isRequiresUserConfirmation()}이
 * {@code true}다. 트리거 가격과 수량은 실행 가능한 규칙으로 계산할 수 없을 때 {@code null}
 * 일 수 있으며, 이 경우 {@link #getReason()}이 그 사유를 설명한다.
 */
public class PlannedTradeAction {

    private final PlannedTradeActionType type;
    private final BigDecimal triggerPrice;
    private final BigDecimal quantity;
    private final BigDecimal amount;
    private final String reason;
    private final StrategyMetadata strategyMetadata;

    public PlannedTradeAction(
            PlannedTradeActionType type,
            BigDecimal triggerPrice,
            BigDecimal quantity,
            BigDecimal amount,
            String reason,
            StrategyMetadata strategyMetadata
    ) {
        if (type == null) {
            throw new IllegalArgumentException("행동 유형은 필수입니다.");
        }

        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("판단 근거는 비어 있을 수 없습니다.");
        }

        if (strategyMetadata == null) {
            throw new IllegalArgumentException("전략 메타데이터는 필수입니다.");
        }

        this.type = type;
        this.triggerPrice = triggerPrice;
        this.quantity = quantity;
        this.amount = amount;
        this.reason = reason;
        this.strategyMetadata = strategyMetadata;
    }

    public PlannedTradeActionType getType() {
        return type;
    }

    public BigDecimal getTriggerPrice() {
        return triggerPrice;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getReason() {
        return reason;
    }

    public StrategyMetadata getStrategyMetadata() {
        return strategyMetadata;
    }

    /**
     * 이 행동 초안은 항상 사용자 확인이 필요한 검토용이다. 자동 주문 전송을 뜻하지 않는다.
     */
    public boolean isRequiresUserConfirmation() {
        return true;
    }
}
