package com.tradeguide.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 증권사 자격 증명 암호화 키 로테이션 실행 진입점 설정이다
 * (docs/agent-tasks/broker-credential-encryption-key-rotation.md §1 결정 1, §9).
 *
 * <p>값이 아니라 실행 여부·배치 크기·실행자 식별자만 다룬다. 이 프로퍼티는 브라우저에서
 * 도달 가능한 어떤 HTTP 경로에도 연결하지 않는다 — 운영자가 로컬 셸이나 배포 파이프라인에서
 * 애플리케이션을 기동할 때만 값을 준다.
 */
@ConfigurationProperties(prefix = "tradeguide.broker.rotation")
public record BrokerKeyRotationProperties(
        boolean runOnStartup,
        Integer batchSize,
        String triggeredBy,
        boolean resumeInProgress
) {
    private static final int DEFAULT_BATCH_SIZE = 200;
    private static final String DEFAULT_TRIGGERED_BY = "OPERATOR";

    public BrokerKeyRotationProperties {
        if (batchSize == null) {
            batchSize = DEFAULT_BATCH_SIZE;
        }
        if (batchSize < 1) {
            throw new IllegalStateException(
                    "tradeguide.broker.rotation.batch-size must be a positive integer but was " + batchSize + "."
            );
        }
        triggeredBy = (triggeredBy == null || triggeredBy.isBlank()) ? DEFAULT_TRIGGERED_BY : triggeredBy;
    }
}
