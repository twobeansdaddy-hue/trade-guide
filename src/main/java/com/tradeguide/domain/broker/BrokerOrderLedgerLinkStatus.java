package com.tradeguide.domain.broker;

/**
 * 주문 한 건이 매매 원장에 반영된 상태다. 취소해도 행을 지우지 않고 상태로만 구분해
 * 무엇을 언제 반영했다가 되돌렸는지 남긴다.
 */
public enum BrokerOrderLedgerLinkStatus {
    ACTIVE,
    REVOKED
}
