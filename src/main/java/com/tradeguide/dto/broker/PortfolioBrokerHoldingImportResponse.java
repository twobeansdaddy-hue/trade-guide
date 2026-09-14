package com.tradeguide.dto.broker;

import com.tradeguide.domain.broker.PortfolioBrokerHoldingImport;
import com.tradeguide.domain.broker.PortfolioBrokerHoldingImportStatus;
import com.tradeguide.domain.broker.PortfolioBrokerHoldingSnapshotItem;
import com.tradeguide.domain.trade.Market;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 증권사 개시 잔고 승인 감사 이력의 응답이다.
 *
 * <p>{@code snapshotItemId}는 널일 수 있다. 증권사 연결을 삭제하면 그 연결에서 파생된
 * 스냅샷 항목도 함께 사라지고, 이미 취소({@code REVOKED})된 이력은 스냅샷 참조만 끊긴 채
 * 보존되기 때문이다. 종목·표시명·수량·평단가·스냅샷 기준 시각·매매 기록 ID·상태는 승인
 * 시점 값이 이력 자체에 복사되어 있으므로, 참조가 끊긴 뒤에도 그대로 응답한다. 즉 널인
 * {@code snapshotItemId}는 "원본 스냅샷 항목이 더 이상 존재하지 않는다"는 뜻이며, 이력이
 * 불완전하다는 뜻이 아니다.
 */
public record PortfolioBrokerHoldingImportResponse(
        Long id,
        Long snapshotItemId,
        Market market,
        String ticker,
        String displayName,
        BigDecimal quantity,
        BigDecimal averagePurchasePrice,
        LocalDateTime snapshotSyncedAt,
        Long tradeTransactionId,
        Long approvedByMemberId,
        LocalDateTime approvedAt,
        PortfolioBrokerHoldingImportStatus status
) {
    public static PortfolioBrokerHoldingImportResponse from(PortfolioBrokerHoldingImport importRecord) {
        PortfolioBrokerHoldingSnapshotItem snapshotItem = importRecord.getSnapshotItem();

        return new PortfolioBrokerHoldingImportResponse(
                importRecord.getId(),
                snapshotItem == null ? null : snapshotItem.getId(),
                importRecord.getMarket(),
                importRecord.getTicker(),
                importRecord.getDisplayName(),
                importRecord.getQuantity(),
                importRecord.getAveragePurchasePrice(),
                importRecord.getSnapshotSyncedAt(),
                importRecord.getTradeTransactionId(),
                importRecord.getApprovedByMemberId(),
                importRecord.getApprovedAt(),
                importRecord.getStatus()
        );
    }
}
