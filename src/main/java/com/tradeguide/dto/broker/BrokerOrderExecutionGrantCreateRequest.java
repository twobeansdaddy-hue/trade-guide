package com.tradeguide.dto.broker;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * 오토매매 opt-in 동의 생성 요청이다.
 *
 * <p>{@code maxPositionSizePerOrderPercent}/{@code maxDailyOrderCount}는 이 DTO
 * 수준에서도 넉넉한 상한(각각 20%, 10건)을 검증하지만, 이는 명백히 잘못된 입력을
 * 조기에 거절하기 위한 것이다. 실제 시스템 하드 상한 적용과 그 근거는
 * {@code BrokerOrderExecutionGrantService}가 한 번 더 수행하는 것을 신뢰 경계로
 * 삼는다 - DTO 검증만으로는 우회 가능한 클라이언트 값이기 때문이다.
 */
public class BrokerOrderExecutionGrantCreateRequest {

    @NotBlank(message = "전략 ID는 필수입니다.")
    private String strategyId;

    @NotNull(message = "포지션 한도는 필수입니다.")
    @DecimalMin(value = "0.0001", message = "포지션 한도는 0보다 커야 합니다.")
    @DecimalMax(value = "0.20", message = "포지션 한도는 계좌 자산의 20%를 넘을 수 없습니다.")
    private BigDecimal maxPositionSizePerOrderPercent;

    @Min(value = 1, message = "일일 주문 한도는 1건 이상이어야 합니다.")
    @Max(value = 10, message = "일일 주문 한도는 10건을 넘을 수 없습니다.")
    private int maxDailyOrderCount;

    @NotBlank(message = "동의 버전 정보는 필수입니다.")
    private String consentVersion;

    public String getStrategyId() {
        return strategyId;
    }

    public void setStrategyId(String strategyId) {
        this.strategyId = strategyId;
    }

    public BigDecimal getMaxPositionSizePerOrderPercent() {
        return maxPositionSizePerOrderPercent;
    }

    public void setMaxPositionSizePerOrderPercent(BigDecimal maxPositionSizePerOrderPercent) {
        this.maxPositionSizePerOrderPercent = maxPositionSizePerOrderPercent;
    }

    public int getMaxDailyOrderCount() {
        return maxDailyOrderCount;
    }

    public void setMaxDailyOrderCount(int maxDailyOrderCount) {
        this.maxDailyOrderCount = maxDailyOrderCount;
    }

    public String getConsentVersion() {
        return consentVersion;
    }

    public void setConsentVersion(String consentVersion) {
        this.consentVersion = consentVersion;
    }
}
