package com.tradeguide.exception;

/**
 * 요청 자체는 올바르지만 이 조건으로는 주문 이력 가져오기를 끝낼 수 없을 때 발생한다.
 * 조회 구간이 너무 넓거나, 제공자가 커서 순회를 끝내지 못하는 응답을 주는 경우다.
 *
 * <p>{@code failureCode}는 우리가 정의한 값이고 프론트엔드가 분기에 쓴다.
 * 증권사 원문 응답이나 오류 문구는 코드에도 메시지에도 담지 않는다.
 */
public class BrokerOrderImportUnprocessableException extends RuntimeException {

    private final String failureCode;

    public BrokerOrderImportUnprocessableException(String message, String failureCode) {
        super(message);
        this.failureCode = failureCode;
    }

    public String failureCode() {
        return failureCode;
    }
}
