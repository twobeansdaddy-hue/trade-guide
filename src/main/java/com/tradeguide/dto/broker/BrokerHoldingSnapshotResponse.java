package com.tradeguide.dto.broker;

import com.tradeguide.domain.broker.BrokerProvider;
import com.tradeguide.domain.broker.PortfolioBrokerHoldingSnapshot;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 저장된 읽기 전용 증권사 보유 종목 스냅샷 응답이다. 자격 증명, 토큰, 계좌 일련번호는
 * 포함하지 않는다.
 */
public record BrokerHoldingSnapshotResponse(
        Long id,
        BrokerProvider provider,
        Long brokerConnectionId,
        String maskedAccountNumber,
        LocalDateTime syncedAt,
        List<BrokerHoldingSnapshotItemResponse> items,
        int unsupportedMarketCount
) {
    public static BrokerHoldingSnapshotResponse from(PortfolioBrokerHoldingSnapshot snapshot) {
        return new BrokerHoldingSnapshotResponse(
                snapshot.getId(),
                snapshot.getBrokerConnection().getProvider(),
                snapshot.getBrokerConnection().getId(),
                snapshot.getBrokerAccount().getMaskedAccountNumber(),
                snapshot.getSyncedAt(),
                snapshot.getItems().stream().map(BrokerHoldingSnapshotItemResponse::from).toList(),
                snapshot.getUnsupportedMarketCount()
        );
    }
}
