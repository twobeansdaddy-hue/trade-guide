package com.tradeguide.dto.strategy;

import com.tradeguide.domain.strategy.EmptyHoldingsGuidance;

public class EmptyHoldingsGuidanceResponse {

    private final String reason;
    private final String message;

    public EmptyHoldingsGuidanceResponse(
            String reason,
            String message
    ) {
        this.reason = reason;
        this.message = message;
    }

    public static EmptyHoldingsGuidanceResponse from(EmptyHoldingsGuidance guidance) {
        if (guidance == null) {
            return null;
        }

        return new EmptyHoldingsGuidanceResponse(
                guidance.getReason().name(),
                guidance.getMessage()
        );
    }

    public String getReason() {
        return reason;
    }

    public String getMessage() {
        return message;
    }
}
