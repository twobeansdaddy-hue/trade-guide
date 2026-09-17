package com.tradeguide.domain.broker;

/**
 * 자동 주문 실행 시도 한 건의 결과 상태다.
 *
 * <p>이 슬라이스는 실제 브로커 호출을 구현하지 않으므로 지금은 어떤 행도 이 상태들을
 * 실제로 거치지 않는다 - 다음 슬라이스(실제 주문 제출)가 이 스키마를 채우기 시작한다.
 */
public enum BrokerOrderExecutionRunStatus {
    PENDING,
    SUBMITTED,
    FAILED,
    REJECTED_BY_SAFEGUARD
}
