package com.tradeguide.exception;

/**
 * 요청 형식은 옳지만 잔고 조정으로 반영할 수 없다는 판정이다. 422로 매핑된다.
 *
 * <p>증권사 수량이 Trade Guide 보유 수량보다 크지 않거나(조정 수량이 0 이하),
 * 산출된 조정 단가가 0 이하이거나 계산할 수 없는 경우에 발생한다.
 */
public class BrokerHoldingAdjustmentUnprocessableException extends RuntimeException {
    public BrokerHoldingAdjustmentUnprocessableException(String message) {
        super(message);
    }
}
