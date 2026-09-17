package com.tradeguide.domain.broker;

/**
 * 오토매매 opt-in 동의(grant)의 생애주기 상태다.
 *
 * <p>{@code REVOKED}는 종단 상태다 - 한 번 철회하면 같은 행을 다시 {@code ACTIVE}로
 * 되돌릴 수 없고, 재동의는 새 동의 절차(신규 {@code consentedAt}/{@code consentVersion})를
 * 거쳐야 한다. {@code PAUSED}는 일시정지로, {@code ACTIVE}로 재개할 수 있다.
 */
public enum BrokerOrderExecutionGrantStatus {
    ACTIVE,
    PAUSED,
    REVOKED
}
