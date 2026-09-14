package com.tradeguide.domain.broker;

import com.tradeguide.domain.trade.Market;

/**
 * 일괄 개시 잔고 반영에서 제외된 종목 한 건과 그 사유다.
 * 화면이 사유별로 다른 안내를 할 수 있도록 종목 식별 값과 사유만 담는다.
 */
public record BrokerOpeningBalanceSkip(
        Long snapshotItemId,
        Market market,
        String ticker,
        String displayName,
        BrokerOpeningBalanceSkipReason reason
) {
    public BrokerOpeningBalanceSkip {
        if (market == null || ticker == null || reason == null) {
            throw new IllegalArgumentException("일괄 개시 잔고 제외 정보가 올바르지 않습니다.");
        }
    }
}
