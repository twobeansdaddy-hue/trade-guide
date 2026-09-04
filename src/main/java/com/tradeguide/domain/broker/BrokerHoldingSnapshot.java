package com.tradeguide.domain.broker;

import java.util.List;

/**
 * 증권사 계좌 한 개의 보유 종목 스냅샷이다.
 * {@code unsupportedMarketCount}는 Trade Guide가 아직 지원하지 않는 시장이라
 * 스냅샷에서 제외한 항목 수이며, 조용한 데이터 누락을 막기 위해 함께 보고한다.
 */
public record BrokerHoldingSnapshot(
        List<BrokerHolding> holdings,
        int unsupportedMarketCount
) {
    public BrokerHoldingSnapshot {
        if (holdings == null) {
            throw new IllegalArgumentException("증권사 보유 종목 목록은 필수입니다.");
        }
        if (unsupportedMarketCount < 0) {
            throw new IllegalArgumentException("미지원 시장 항목 수는 0 이상이어야 합니다.");
        }

        holdings = List.copyOf(holdings);
    }
}
