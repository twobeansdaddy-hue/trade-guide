package com.tradeguide.exception;

import com.tradeguide.dto.ApiErrorCode;

/**
 * 같은 포트폴리오의 같은 증권사 조회 동작(미리보기·스냅샷 갱신·주문 이력 가져오기)이 이미
 * 진행 중일 때 발생한다. 409로 매핑된다.
 *
 * <p>증권사 호출이 끝나면(성공이든 실패든) 잠금이 풀리므로 잠시 후 재시도하면 통과할 수 있다.
 */
public class BrokerCallInProgressException extends RuntimeException {

    public BrokerCallInProgressException(String message) {
        super(message);
    }

    public ApiErrorCode getCode() {
        return ApiErrorCode.BROKER_CALL_IN_PROGRESS;
    }
}
