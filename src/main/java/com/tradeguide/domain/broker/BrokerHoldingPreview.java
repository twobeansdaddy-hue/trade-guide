package com.tradeguide.domain.broker;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 포트폴리오에 연결된 증권사 계좌의 읽기 전용 보유 종목 미리보기다.
 * 출처와 조회 시각을 함께 담아 사용자가 데이터 제공자를 구분할 수 있게 한다.
 */
public record BrokerHoldingPreview(
        BrokerProvider provider,
        Long brokerConnectionId,
        String maskedAccountNumber,
        LocalDateTime syncedAt,
        List<BrokerHoldingPreviewItem> items,
        int unsupportedMarketCount
) {
    public BrokerHoldingPreview {
        items = List.copyOf(items);
    }
}
