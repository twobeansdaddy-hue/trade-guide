package com.tradeguide.domain.broker;

/**
 * 증권사 개시 잔고 가져오기 감사 이력의 상태다.
 * {@code REVOKED}는 전용 취소 API로만 전이되며, 취소된 원장 행은 삭제되지만
 * 이 이력 자체는 감사 목적으로 보존된다.
 */
public enum PortfolioBrokerHoldingImportStatus {
    ACTIVE,
    REVOKED
}
