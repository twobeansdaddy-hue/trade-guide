package com.tradeguide.domain.strategy;

/**
 * 보유 종목 가이드 목록이 비어 있을 때, 원인과 다음 행동을 안내하는 문구를 함께 전달한다.
 * 전략 프로필 저장 자체는 보유 수량을 만들지 않는다는 전제를 화면에 명확히 전달하기 위한
 * 값 객체다.
 */
public class EmptyHoldingsGuidance {

    private final EmptyHoldingsReason reason;
    private final String message;

    public EmptyHoldingsGuidance(
            EmptyHoldingsReason reason,
            String message
    ) {
        this.reason = reason;
        this.message = message;
    }

    public EmptyHoldingsReason getReason() {
        return reason;
    }

    public String getMessage() {
        return message;
    }
}
