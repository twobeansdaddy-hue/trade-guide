package com.tradeguide.dto.broker;

import com.tradeguide.domain.broker.BrokerOpeningBalanceBatchResult;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 일괄 개시 잔고 반영 결과 응답이다.
 *
 * <p>어떤 스냅샷을 근거로 판단했는지 함께 돌려줘, 화면이 방금 보여 준 비교 결과와 서버가
 * 반영한 근거가 같은 스냅샷인지 확인할 수 있게 한다.
 *
 * <p>{@code approved}가 비어 있어도 실패가 아니다. 반영할 것이 없었다는 사실과 종목별 이유가
 * {@code skipped}에 그대로 담긴다.
 */
public record BrokerHoldingImportBatchResponse(
        Long snapshotId,
        LocalDateTime snapshotSyncedAt,
        int approvedCount,
        int skippedCount,
        List<PortfolioBrokerHoldingImportResponse> approved,
        List<BrokerOpeningBalanceSkipResponse> skipped
) {
    public static BrokerHoldingImportBatchResponse from(BrokerOpeningBalanceBatchResult result) {
        return new BrokerHoldingImportBatchResponse(
                result.snapshotId(),
                result.snapshotSyncedAt(),
                result.approved().size(),
                result.skipped().size(),
                result.approved().stream().map(PortfolioBrokerHoldingImportResponse::from).toList(),
                result.skipped().stream().map(BrokerOpeningBalanceSkipResponse::from).toList()
        );
    }
}
