package com.tradeguide.service.broker;

import com.tradeguide.config.BrokerKeyRotationProperties;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 증권사 자격 증명 암호화 키 로테이션의 유일한 실행 진입점이다
 * (docs/agent-tasks/broker-credential-encryption-key-rotation.md §1 결정 1, §9).
 *
 * <p>{@code tradeguide.broker.rotation.run-on-startup}의 기본값은 {@code false}다. 운영자가
 * 로컬 셸이나 배포 파이프라인에서 이 값을 명시적으로 켜고 애플리케이션을 기동했을 때만
 * {@link BrokerKeyRotationService#rotate()}가 실행된다. HTTP 엔드포인트, 스케줄러,
 * 프런트엔드 어디에도 연결하지 않는다 — 브라우저에서 도달 가능한 경로가 전혀 없다.
 *
 * <p>실행 결과는 {@code broker_key_rotation_runs} 표에만 남는다(§8). 이 진입점은 콘솔에
 * 아무것도 출력하지 않는다 — 이 저장소에는 애초에 로깅 프레임워크가 없고
 * ({@code docs/agent-tasks/broker-credential-encryption-key-rotation.md} §2.4), 유일하게
 * 확립된 감사 흔적 방식은 실행 기록 행이다. 운영자는 실행 후 그 표를 조회해 결과를 확인한다.
 */
@Component
public class BrokerKeyRotationRunner implements ApplicationRunner {

    private final BrokerKeyRotationProperties rotationProperties;
    private final BrokerKeyRotationService rotationService;

    public BrokerKeyRotationRunner(
            BrokerKeyRotationProperties rotationProperties,
            BrokerKeyRotationService rotationService
    ) {
        this.rotationProperties = rotationProperties;
        this.rotationService = rotationService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!rotationProperties.runOnStartup()) {
            return;
        }
        rotationService.rotate();
    }
}
