package com.tradeguide.domain.broker;

/**
 * 사람 판단이 필요한 의심 항목에 대해 사용자가 내린 재판정 결정이다.
 *
 * <p>결정은 새 스테이징 상태를 만들지 않는다. 결과는 언제나 기존 {@link BrokerOrderStagingStatus}
 * 값 가운데 하나이고 그 의미도 그대로다. {@link #ALLOW_LEDGER_WRITE}의 결과인
 * {@link BrokerOrderStagingStatus#STAGED}는 원래 뜻 그대로 "반영 후보"일 뿐이며, 원장 반영은
 * 여전히 실행 단위 승인에서만 일어난다.
 */
public enum BrokerOrderOverrideDecision {
    /** 별개 거래로 확인했다. 반영 후보로 올려 실행 승인 시 원장 반영 대상에 포함한다. */
    ALLOW_LEDGER_WRITE,
    /** 의심이 맞거나 반영하지 않기로 했다. 원래 의심 상태를 유지하며 원장에 반영하지 않는다. */
    KEEP_EXCLUDED;

    /** 원래 스테이징 상태에 이 결정을 적용한 결과 상태다. */
    public BrokerOrderStagingStatus resultingStatusFrom(BrokerOrderStagingStatus originalStatus) {
        return this == ALLOW_LEDGER_WRITE ? BrokerOrderStagingStatus.STAGED : originalStatus;
    }
}
