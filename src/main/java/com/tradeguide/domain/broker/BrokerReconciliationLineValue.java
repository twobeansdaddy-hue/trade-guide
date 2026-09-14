package com.tradeguide.domain.broker;

import com.tradeguide.domain.trade.Market;

import java.math.BigDecimal;
import java.util.Set;

/**
 * 정합성 점검 실행에서 종목 한 건에 계산한 결과다. 저장 전 값 객체이며,
 * {@link BrokerReconciliationLine}이 이 값을 그대로 얼려 보존한다.
 */
public record BrokerReconciliationLineValue(
        Market market,
        String ticker,
        String displayName,
        BigDecimal brokerQuantity,
        BigDecimal tradeGuideQuantity,
        BrokerHoldingComparison comparison,
        Set<BrokerReconciliationReasonCode> reasonCandidates
) {
    public BrokerReconciliationLineValue {
        if (market == null || ticker == null || ticker.isBlank() || displayName == null || comparison == null) {
            throw new IllegalArgumentException("정합성 점검 결과 줄의 정보가 올바르지 않습니다.");
        }
        reasonCandidates = Set.copyOf(reasonCandidates == null ? Set.of() : reasonCandidates);
    }
}
