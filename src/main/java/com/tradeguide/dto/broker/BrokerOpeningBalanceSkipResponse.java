package com.tradeguide.dto.broker;

import com.tradeguide.domain.broker.BrokerOpeningBalanceSkip;
import com.tradeguide.domain.broker.BrokerOpeningBalanceSkipReason;
import com.tradeguide.domain.trade.Market;

/**
 * 일괄 개시 잔고 반영에서 제외된 종목 한 건의 응답이다. 화면이 사유별로 다른 안내를 할 수
 * 있도록 종목 식별 값과 사유만 담고, 증권사 원문 메시지나 계좌 식별 값은 담지 않는다.
 */
public record BrokerOpeningBalanceSkipResponse(
        Long snapshotItemId,
        Market market,
        String ticker,
        String displayName,
        BrokerOpeningBalanceSkipReason reason
) {
    public static BrokerOpeningBalanceSkipResponse from(BrokerOpeningBalanceSkip skip) {
        return new BrokerOpeningBalanceSkipResponse(
                skip.snapshotItemId(),
                skip.market(),
                skip.ticker(),
                skip.displayName(),
                skip.reason()
        );
    }
}
