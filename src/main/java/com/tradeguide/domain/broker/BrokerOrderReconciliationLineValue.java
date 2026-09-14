package com.tradeguide.domain.broker;

import com.tradeguide.domain.trade.Market;

import java.math.BigDecimal;

/**
 * 종목 한 개의 대조 결과다. 기존 원장에 스테이징 대상을 더해 재구성한 수량과, 증권사가 보고한
 * 최신 보유 스냅샷 수량을 나란히 둔다.
 *
 * <p>차이를 메우는 값을 만들지 않는다. 취득 단가를 알 수 없는 합성 매수를 끼워 넣으면
 * 평단이 조용히 틀리고, 그 틀린 평단이 그대로 전략 판단의 입력이 된다.
 */
public record BrokerOrderReconciliationLineValue(
        Market market,
        String ticker,
        BigDecimal reconstructedQuantity,
        BigDecimal snapshotQuantity
) {
    public BrokerOrderReconciliationLineValue {
        if (market == null || ticker == null || ticker.isBlank()) {
            throw new IllegalArgumentException("대조 결과의 시장과 종목 코드는 필수입니다.");
        }
        if (reconstructedQuantity == null || snapshotQuantity == null) {
            throw new IllegalArgumentException("대조 결과의 수량은 필수입니다.");
        }
    }

    /** 재구성 − 스냅샷. 양수면 이중 계상 의심, 음수면 이력 누락 의심이다. */
    public BigDecimal quantityDifference() {
        return reconstructedQuantity.subtract(snapshotQuantity);
    }

    public boolean matches() {
        return reconstructedQuantity.compareTo(snapshotQuantity) == 0;
    }
}
