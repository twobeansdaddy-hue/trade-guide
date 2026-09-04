package com.tradeguide.dto.broker;

import com.tradeguide.domain.broker.BrokerHoldingPreview;
import com.tradeguide.domain.broker.BrokerProvider;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 읽기 전용 보유 종목 미리보기 응답이다. 출처와 조회 시각을 함께 반환하고,
 * 매매 기록 반영 여부는 사용자의 명시적 결정으로 남긴다.
 */
public record BrokerHoldingPreviewResponse(
        BrokerProvider provider,
        Long brokerConnectionId,
        String maskedAccountNumber,
        LocalDateTime syncedAt,
        List<BrokerHoldingPreviewItemResponse> items,
        int unsupportedMarketCount
) {
    public static BrokerHoldingPreviewResponse from(BrokerHoldingPreview preview) {
        return new BrokerHoldingPreviewResponse(
                preview.provider(),
                preview.brokerConnectionId(),
                preview.maskedAccountNumber(),
                preview.syncedAt(),
                preview.items().stream().map(BrokerHoldingPreviewItemResponse::from).toList(),
                preview.unsupportedMarketCount()
        );
    }
}
