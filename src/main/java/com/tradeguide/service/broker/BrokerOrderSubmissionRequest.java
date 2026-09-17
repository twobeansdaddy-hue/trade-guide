package com.tradeguide.service.broker;

import com.tradeguide.domain.broker.BrokerOrderSide;

import java.math.BigDecimal;

/**
 * 브로커에 제출(했거나, dry-run이면 제출했을) 시장가 주문 한 건의 내용이다.
 *
 * <p>이 슬라이스는 시장가 주문만 다룬다 - 지정가·조건부 주문은 범위 밖이다.
 */
public record BrokerOrderSubmissionRequest(
        String accountSequence,
        String ticker,
        BrokerOrderSide side,
        BigDecimal quantity
) {
    public BrokerOrderSubmissionRequest {
        if (accountSequence == null || accountSequence.isBlank()) {
            throw new IllegalArgumentException("계좌 일련번호가 필요합니다.");
        }
        if (ticker == null || ticker.isBlank()) {
            throw new IllegalArgumentException("티커가 필요합니다.");
        }
        if (side == null) {
            throw new IllegalArgumentException("매수/매도 방향이 필요합니다.");
        }
        if (quantity == null || quantity.signum() <= 0) {
            throw new IllegalArgumentException("주문 수량은 0보다 커야 합니다.");
        }
    }
}
