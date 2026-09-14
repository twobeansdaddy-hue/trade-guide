package com.tradeguide.dto.broker;

import com.tradeguide.domain.broker.BrokerOrderImportReconciliationLine;
import com.tradeguide.domain.trade.Market;

import java.math.BigDecimal;

/**
 * 종목 한 개의 대조 결과다. 재구성 수량과 증권사 스냅샷 수량을 나란히 두고 차이를 그대로 보여 준다.
 * 차이를 메우는 값은 만들지 않으므로, 이 응답이 사용자가 볼 수 있는 전부이자 판단 근거다.
 */
public record BrokerOrderImportReconciliationLineResponse(
        Market market,
        String ticker,
        BigDecimal reconstructedQuantity,
        BigDecimal snapshotQuantity,
        BigDecimal quantityDifference
) {
    public static BrokerOrderImportReconciliationLineResponse from(BrokerOrderImportReconciliationLine line) {
        return new BrokerOrderImportReconciliationLineResponse(
                line.getMarket(),
                line.getTicker(),
                line.getReconstructedQuantity(),
                line.getSnapshotQuantity(),
                line.getQuantityDifference()
        );
    }
}
