package com.tradeguide.dto.broker;

import com.tradeguide.domain.broker.PortfolioBrokerHoldingAdjustment;
import com.tradeguide.domain.broker.PortfolioBrokerHoldingAdjustmentStatus;
import com.tradeguide.domain.broker.PortfolioBrokerHoldingSnapshotItem;
import com.tradeguide.domain.trade.Market;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 증권사 잔고 조정 승인 감사 이력의 응답이다.
 *
 * <p>{@code snapshotItemId}는 널일 수 있다. 증권사 연결을 삭제하면 그 연결에서 파생된
 * 스냅샷 항목도 함께 사라지고, 이미 취소({@code REVOKED})된 이력은 스냅샷 참조만 끊긴 채
 * 보존되기 때문이다. 승인 시점 값은 이력 자체에 복사되어 있으므로 참조가 끊긴 뒤에도
 * 그대로 응답한다.
 */
public record PortfolioBrokerHoldingAdjustmentResponse(
        Long id,
        Long snapshotItemId,
        Market market,
        String ticker,
        String displayName,
        BigDecimal deltaQuantity,
        BigDecimal unitPrice,
        BigDecimal brokerQuantity,
        BigDecimal brokerAveragePurchasePrice,
        BigDecimal ledgerQuantityBefore,
        BigDecimal ledgerAveragePurchasePriceBefore,
        LocalDateTime snapshotSyncedAt,
        Long tradeTransactionId,
        Long approvedByMemberId,
        LocalDateTime approvedAt,
        PortfolioBrokerHoldingAdjustmentStatus status
) {
    public static PortfolioBrokerHoldingAdjustmentResponse from(PortfolioBrokerHoldingAdjustment adjustment) {
        PortfolioBrokerHoldingSnapshotItem snapshotItem = adjustment.getSnapshotItem();

        return new PortfolioBrokerHoldingAdjustmentResponse(
                adjustment.getId(),
                snapshotItem == null ? null : snapshotItem.getId(),
                adjustment.getMarket(),
                adjustment.getTicker(),
                adjustment.getDisplayName(),
                adjustment.getDeltaQuantity(),
                adjustment.getUnitPrice(),
                adjustment.getBrokerQuantity(),
                adjustment.getBrokerAveragePurchasePrice(),
                adjustment.getLedgerQuantityBefore(),
                adjustment.getLedgerAveragePurchasePriceBefore(),
                adjustment.getSnapshotSyncedAt(),
                adjustment.getTradeTransactionId(),
                adjustment.getApprovedByMemberId(),
                adjustment.getApprovedAt(),
                adjustment.getStatus()
        );
    }
}
