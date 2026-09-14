package com.tradeguide.dto.broker;

import jakarta.validation.constraints.NotNull;

public class BrokerHoldingAdjustmentCreateRequest {

    @NotNull(message = "스냅샷 항목 ID는 필수입니다.")
    private final Long snapshotItemId;

    public BrokerHoldingAdjustmentCreateRequest(Long snapshotItemId) {
        this.snapshotItemId = snapshotItemId;
    }

    public Long getSnapshotItemId() {
        return snapshotItemId;
    }
}
