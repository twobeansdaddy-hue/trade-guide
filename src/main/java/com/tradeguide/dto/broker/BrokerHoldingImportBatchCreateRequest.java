package com.tradeguide.dto.broker;

import jakarta.validation.constraints.NotNull;

/**
 * 일괄 개시 잔고 반영 요청이다.
 *
 * <p>{@code snapshotId}는 사용자가 화면에서 실제로 검토한 스냅샷이다. 서버는 이 값이 최신
 * 스냅샷과 같을 때만 반영하고, 다르면 {@code 409}로 막는다. 검토 이후 스냅샷이 갱신됐다면
 * 사용자가 보지 않은 종목까지 한 번에 들어갈 수 있고, 그것은 명시적 승인이 아니다.
 */
public class BrokerHoldingImportBatchCreateRequest {

    @NotNull(message = "스냅샷 ID는 필수입니다.")
    private final Long snapshotId;

    public BrokerHoldingImportBatchCreateRequest(Long snapshotId) {
        this.snapshotId = snapshotId;
    }

    public Long getSnapshotId() {
        return snapshotId;
    }
}
