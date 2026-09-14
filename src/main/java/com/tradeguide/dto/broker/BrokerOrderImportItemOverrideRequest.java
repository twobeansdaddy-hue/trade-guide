package com.tradeguide.dto.broker;

import com.tradeguide.domain.broker.BrokerOrderImportItemOverride;
import com.tradeguide.domain.broker.BrokerOrderOverrideDecision;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 의심 항목 재판정 요청이다. 결정과 사유는 둘 다 필수다.
 *
 * <p>사유는 감사 기록에 그대로 남는다. 자격 증명이나 계좌번호를 적는 칸이 아니며, 서버는
 * 사유를 로그에 쓰지 않는다.
 */
public class BrokerOrderImportItemOverrideRequest {

    @NotNull(message = "재판정 결정은 필수입니다.")
    private final BrokerOrderOverrideDecision decision;

    @NotBlank(message = "재판정 사유는 필수입니다.")
    @Size(max = BrokerOrderImportItemOverride.REASON_MAX_LENGTH, message = "재판정 사유는 500자 이하여야 합니다.")
    private final String reason;

    public BrokerOrderImportItemOverrideRequest(BrokerOrderOverrideDecision decision, String reason) {
        this.decision = decision;
        this.reason = reason;
    }

    public BrokerOrderOverrideDecision getDecision() {
        return decision;
    }

    public String getReason() {
        return reason;
    }
}
