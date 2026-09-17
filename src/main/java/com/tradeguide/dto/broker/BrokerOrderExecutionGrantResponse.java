package com.tradeguide.dto.broker;

import com.tradeguide.domain.broker.BrokerOrderExecutionGrant;
import com.tradeguide.domain.broker.BrokerOrderExecutionGrantStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class BrokerOrderExecutionGrantResponse {

    private final Long id;
    private final Long brokerConnectionId;
    private final String strategyId;
    private final BigDecimal maxPositionSizePerOrderPercent;
    private final int maxDailyOrderCount;
    private final BrokerOrderExecutionGrantStatus status;
    private final LocalDateTime consentedAt;
    private final String consentVersion;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    private BrokerOrderExecutionGrantResponse(
            Long id,
            Long brokerConnectionId,
            String strategyId,
            BigDecimal maxPositionSizePerOrderPercent,
            int maxDailyOrderCount,
            BrokerOrderExecutionGrantStatus status,
            LocalDateTime consentedAt,
            String consentVersion,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        this.id = id;
        this.brokerConnectionId = brokerConnectionId;
        this.strategyId = strategyId;
        this.maxPositionSizePerOrderPercent = maxPositionSizePerOrderPercent;
        this.maxDailyOrderCount = maxDailyOrderCount;
        this.status = status;
        this.consentedAt = consentedAt;
        this.consentVersion = consentVersion;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static BrokerOrderExecutionGrantResponse from(BrokerOrderExecutionGrant grant) {
        return new BrokerOrderExecutionGrantResponse(
                grant.getId(),
                grant.getBrokerConnection().getId(),
                grant.getStrategyId(),
                grant.getMaxPositionSizePerOrderPercent(),
                grant.getMaxDailyOrderCount(),
                grant.getStatus(),
                grant.getConsentedAt(),
                grant.getConsentVersion(),
                grant.getCreatedAt(),
                grant.getUpdatedAt()
        );
    }

    public Long getId() {
        return id;
    }

    public Long getBrokerConnectionId() {
        return brokerConnectionId;
    }

    public String getStrategyId() {
        return strategyId;
    }

    public BigDecimal getMaxPositionSizePerOrderPercent() {
        return maxPositionSizePerOrderPercent;
    }

    public int getMaxDailyOrderCount() {
        return maxDailyOrderCount;
    }

    public BrokerOrderExecutionGrantStatus getStatus() {
        return status;
    }

    public LocalDateTime getConsentedAt() {
        return consentedAt;
    }

    public String getConsentVersion() {
        return consentVersion;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
