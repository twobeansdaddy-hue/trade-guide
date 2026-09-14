package com.tradeguide.dto.broker;

import com.tradeguide.domain.broker.BrokerHoldingComparison;
import com.tradeguide.domain.broker.BrokerReconciliationLine;
import com.tradeguide.domain.broker.BrokerReconciliationReasonCode;
import com.tradeguide.domain.trade.Market;

import java.math.BigDecimal;
import java.util.List;

/**
 * 정합성 점검 실행에 속한 종목 한 줄의 응답이다. {@code reasonCandidates}는 판정이 아니라
 * 후보이며, 사용자가 다음에 확인할 행동을 제시하는 용도다.
 */
public record BrokerReconciliationLineResponse(
        Market market,
        String ticker,
        String displayName,
        BigDecimal brokerQuantity,
        BigDecimal tradeGuideQuantity,
        BrokerHoldingComparison comparison,
        List<BrokerReconciliationReasonCode> reasonCandidates
) {
    public static BrokerReconciliationLineResponse from(BrokerReconciliationLine line) {
        return new BrokerReconciliationLineResponse(
                line.getMarket(),
                line.getTicker(),
                line.getDisplayName(),
                line.getBrokerQuantity(),
                line.getTradeGuideQuantity(),
                line.getComparison(),
                line.getReasonCandidates().stream().sorted().toList()
        );
    }
}
