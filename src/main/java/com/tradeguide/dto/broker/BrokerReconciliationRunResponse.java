package com.tradeguide.dto.broker;

import com.tradeguide.domain.broker.BrokerProvider;
import com.tradeguide.domain.broker.BrokerReconciliationOverallStatus;
import com.tradeguide.domain.broker.BrokerReconciliationRun;

import java.time.LocalDateTime;

/**
 * 정합성 점검 실행 한 건의 요약 응답이다. 목록에 쓰며 종목별 줄은 담지 않는다.
 *
 * <p>계좌번호는 마스킹된 형태만 담고 계좌 일련번호·자격 증명은 담지 않는다.
 */
public record BrokerReconciliationRunResponse(
        Long id,
        BrokerProvider provider,
        Long brokerConnectionId,
        String maskedAccountNumber,
        Long snapshotId,
        LocalDateTime snapshotSyncedAt,
        Long executedByMemberId,
        LocalDateTime executedAt,
        BrokerReconciliationOverallStatus overallStatus,
        int matchedCount,
        int quantityMismatchCount,
        int onlyInBrokerCount,
        int onlyInTradeGuideCount
) {
    public static BrokerReconciliationRunResponse from(BrokerReconciliationRun run) {
        return new BrokerReconciliationRunResponse(
                run.getId(),
                run.getBrokerConnection().getProvider(),
                run.getBrokerConnection().getId(),
                run.getBrokerAccount().getMaskedAccountNumber(),
                run.getSnapshotId(),
                run.getSnapshotSyncedAt(),
                run.getExecutedByMemberId(),
                run.getExecutedAt(),
                run.getOverallStatus(),
                run.getMatchedCount(),
                run.getQuantityMismatchCount(),
                run.getOnlyInBrokerCount(),
                run.getOnlyInTradeGuideCount()
        );
    }
}
