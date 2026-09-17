package com.tradeguide.service.broker;

/**
 * 브로커 주문 제출 호출 한 번의 결과다.
 *
 * <p>{@code success}가 false면 {@code failureReasonCode}가 채워진다 - 증권사 원문
 * 오류 메시지는 담지 않는다({@code BrokerOrderExecutionRun}이 감사 기록으로 남기 때문에
 * 민감정보·원문을 그대로 흘려보내지 않는다는 원칙과 같다).
 */
public record BrokerOrderSubmissionResult(
        boolean success,
        String providerOrderId,
        String failureReasonCode
) {
    public static BrokerOrderSubmissionResult submitted(String providerOrderId) {
        return new BrokerOrderSubmissionResult(true, providerOrderId, null);
    }

    public static BrokerOrderSubmissionResult failed(String failureReasonCode) {
        return new BrokerOrderSubmissionResult(false, null, failureReasonCode);
    }
}
