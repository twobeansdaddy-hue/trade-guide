package com.tradeguide.exception;

/**
 * 요청한 주문 이력 가져오기 실행을 이 포트폴리오에서 찾을 수 없을 때 발생한다.
 * 다른 회원·포트폴리오의 실행을 조회하려 한 경우도 같은 예외로 다뤄, 실행의 존재 여부가
 * 응답 차이로 새어 나가지 않게 한다.
 */
public class BrokerOrderImportNotFoundException extends RuntimeException {
    public BrokerOrderImportNotFoundException(String message) {
        super(message);
    }
}
