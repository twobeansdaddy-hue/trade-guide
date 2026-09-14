package com.tradeguide.domain.broker;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 일괄 개시 잔고 반영 한 번의 결과다.
 *
 * <p>어떤 스냅샷을 근거로 판단했는지({@code snapshotId}, {@code snapshotSyncedAt}) 함께 돌려준다.
 * 사용자가 화면에서 본 비교 결과와 서버가 실제로 반영한 근거가 같은 스냅샷인지 확인할 수 있어야
 * 하기 때문이다.
 *
 * <p>{@code approved}가 비어 있어도 실패가 아니다. 반영할 것이 없었다는 사실과 그 이유가
 * {@code skipped}에 그대로 담긴다.
 */
public record BrokerOpeningBalanceBatchResult(
        Long snapshotId,
        LocalDateTime snapshotSyncedAt,
        List<PortfolioBrokerHoldingImport> approved,
        List<BrokerOpeningBalanceSkip> skipped
) {
    public BrokerOpeningBalanceBatchResult {
        if (snapshotId == null || snapshotSyncedAt == null || approved == null || skipped == null) {
            throw new IllegalArgumentException("일괄 개시 잔고 반영 결과가 올바르지 않습니다.");
        }
        approved = List.copyOf(approved);
        skipped = List.copyOf(skipped);
    }

    public boolean hasApproved() {
        return !approved.isEmpty();
    }
}
