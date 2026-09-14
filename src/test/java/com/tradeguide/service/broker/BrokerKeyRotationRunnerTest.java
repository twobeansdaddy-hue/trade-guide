package com.tradeguide.service.broker;

import com.tradeguide.config.BrokerKeyRotationProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 기본값이 꺼져 있으면 로테이션이 전혀 실행되지 않는지 확인한다
 * (docs/agent-tasks/broker-credential-encryption-key-rotation.md §1 결정 1, §9).
 */
@ExtendWith(MockitoExtension.class)
class BrokerKeyRotationRunnerTest {

    @Mock
    private BrokerKeyRotationService rotationService;

    @Test
    void doesNotRotateWhenRunOnStartupIsDisabledByDefault() {
        BrokerKeyRotationRunner runner = new BrokerKeyRotationRunner(
                new BrokerKeyRotationProperties(false, null, null, false),
                rotationService
        );

        runner.run(null);

        verify(rotationService, never()).rotate();
    }

    @Test
    void rotatesWhenOperatorExplicitlyEnablesRunOnStartup() {
        BrokerKeyRotationRunner runner = new BrokerKeyRotationRunner(
                new BrokerKeyRotationProperties(true, null, null, false),
                rotationService
        );

        runner.run(null);

        verify(rotationService).rotate();
    }
}
