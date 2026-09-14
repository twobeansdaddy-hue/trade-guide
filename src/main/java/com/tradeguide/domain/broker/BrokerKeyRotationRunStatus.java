package com.tradeguide.domain.broker;

/**
 * 증권사 자격 증명 암호화 키 로테이션 실행 한 건의 상태다.
 *
 * <p>{@code ABORTED}는 이 슬라이스의 코드가 스스로 설정하지 않는다. 운영자가 진행 중인
 * 실행을 이어가지 않기로 결정했을 때 직접 갱신하는 값이며, {@code broker_key_rotation_runs.status}
 * 컬럼이 허용하는 상태 하나로 남겨 둔다
 * (docs/agent-tasks/broker-credential-encryption-key-rotation.md §7).
 */
public enum BrokerKeyRotationRunStatus {
    IN_PROGRESS,
    COMPLETED,
    FAILED_PREFLIGHT,
    ABORTED
}
