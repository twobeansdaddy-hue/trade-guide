package com.tradeguide.domain.broker;

/**
 * 증권사 연결 검증 결과로 발견된, 아직 포트폴리오에 연결되지 않은 계좌 후보다.
 * 특정 증권사 API 응답 형태에 의존하지 않는 공통 계약 값이다.
 */
public record BrokerConnectionCandidateAccount(
        String accountSequence,
        String maskedAccountNumber,
        String accountType
) {
}
