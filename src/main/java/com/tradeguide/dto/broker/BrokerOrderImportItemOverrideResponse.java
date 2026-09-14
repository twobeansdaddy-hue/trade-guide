package com.tradeguide.dto.broker;

import com.tradeguide.domain.broker.BrokerOrderImportItemOverride;
import com.tradeguide.domain.broker.BrokerOrderOverrideDecision;
import com.tradeguide.domain.broker.BrokerOrderSkipReason;
import com.tradeguide.domain.broker.BrokerOrderStagingStatus;

import java.time.LocalDateTime;

/**
 * 주문 항목 재판정 한 건의 응답이다. 누가·언제·왜·무엇에서 무엇으로 판단했는지만 담는다.
 * 계좌 식별값·자격 증명·증권사 원문 응답은 담지 않는다. 주문 식별자는 항목 응답에 이미
 * 노출되는 값이라 함께 둔다.
 */
public record BrokerOrderImportItemOverrideResponse(
        Long id,
        Long runId,
        Long itemId,
        String externalOrderId,
        BrokerOrderStagingStatus originalStagingStatus,
        BrokerOrderSkipReason originalSkipReasonCode,
        BrokerOrderOverrideDecision decision,
        BrokerOrderStagingStatus resultingStagingStatus,
        String reason,
        Long createdByMemberId,
        LocalDateTime createdAt
) {
    public static BrokerOrderImportItemOverrideResponse from(BrokerOrderImportItemOverride override) {
        return new BrokerOrderImportItemOverrideResponse(
                override.getId(),
                override.getRun().getId(),
                override.getItem().getId(),
                override.getExternalOrderId(),
                override.getOriginalStagingStatus(),
                override.getOriginalSkipReasonCode(),
                override.getDecision(),
                override.getResultingStagingStatus(),
                override.getReason(),
                override.getCreatedByMemberId(),
                override.getCreatedAt()
        );
    }
}
