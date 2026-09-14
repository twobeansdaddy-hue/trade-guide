package com.tradeguide.exception;

import com.tradeguide.dto.ApiErrorCode;

/**
 * 증권사 주문 이력 승인을 이 상태로는 진행할 수 없을 때 발생한다. 활성 개시 잔고 기준점이
 * 반영 후보를 모두 걷어냈거나, 실행의 대조 결과가 일치하지 않거나, 승인 직전 재생 검증에서
 * 초과 매도가 나온 경우다. 어느 경우든 매매 원장에는 한 행도 쓰지 않는다.
 *
 * <p>{@link #getCode()}는 선택 값이다. 사유별로 화면이 다른 안내를 보여줘야 하는 경우에만
 * 부여하고({@link ApiErrorCode#RECONCILIATION_MISMATCH},
 * {@link ApiErrorCode#BASELINE_EXCLUDED}, {@link ApiErrorCode#REPLAY_VALIDATION_FAILED}),
 * 상장 상태처럼 문구가 매번 달라지는 사유는 코드 없이 문구로만 남긴다. HTTP 상태는 409로
 * 고정이며 코드는 그 안에서 사유만 구분한다.
 */
public class BrokerOrderApprovalConflictException extends RuntimeException {

    private final ApiErrorCode code;

    public BrokerOrderApprovalConflictException(String message) {
        this(message, null);
    }

    public BrokerOrderApprovalConflictException(String message, ApiErrorCode code) {
        super(message);
        this.code = code;
    }

    public ApiErrorCode getCode() {
        return code;
    }
}
