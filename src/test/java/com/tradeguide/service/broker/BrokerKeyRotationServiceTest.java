package com.tradeguide.service.broker;

import com.tradeguide.config.BrokerCredentialKeyringProperties;
import com.tradeguide.config.BrokerKeyRotationProperties;
import com.tradeguide.domain.broker.BrokerKeyRotationRun;
import com.tradeguide.domain.broker.BrokerKeyRotationRunStatus;
import com.tradeguide.repository.broker.BrokerAccountRepository;
import com.tradeguide.repository.broker.BrokerConnectionSecretValueRepository;
import com.tradeguide.repository.broker.BrokerKeyRotationRunRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 사전 점검 분기와 진행 중 실행 처리 규칙만 다룬다
 * (docs/agent-tasks/broker-credential-encryption-key-rotation.md §5, §6.2, §6.3). 실제
 * 페이지 조회·조건부 UPDATE·복호화 실패 복구는 더미 PostgreSQL을 쓰는
 * {@code PostgresBrokerKeyRotationIntegrationTest}가 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class BrokerKeyRotationServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-09-10T09:00:00Z"), ZoneOffset.UTC);

    @Mock
    private BrokerConnectionSecretValueRepository secretValueRepository;

    @Mock
    private BrokerAccountRepository accountRepository;

    @Mock
    private BrokerKeyRotationRunRepository runRepository;

    @Mock
    private BrokerKeyRotationBatchProcessor batchProcessor;

    @Mock
    private BrokerCredentialCipher cipher;

    @Test
    void recordsFailedPreflightWhenEncryptionIsNotConfigured() {
        BrokerKeyRotationService service = service(legacyKeyringProperties(""), defaultRotationProperties());
        when(runRepository.findFirstByStatus(BrokerKeyRotationRunStatus.IN_PROGRESS)).thenReturn(Optional.empty());
        when(cipher.isConfigured()).thenReturn(false);
        stubSaveReturnsArgument();

        BrokerKeyRotationRun run = service.rotate();

        assertThat(run.getStatus()).isEqualTo(BrokerKeyRotationRunStatus.FAILED_PREFLIGHT);
        assertThat(run.getFailureReason()).isEqualTo("ENCRYPTION_NOT_CONFIGURED");
        verifyNoInteractions(batchProcessor);
    }

    @Test
    void recordsFailedPreflightWhenAnObservedSourceVersionHasNoConfiguredKey() {
        BrokerKeyRotationService service = service(legacyKeyringProperties(testKey(1)), defaultRotationProperties());
        when(runRepository.findFirstByStatus(BrokerKeyRotationRunStatus.IN_PROGRESS)).thenReturn(Optional.empty());
        when(cipher.isConfigured()).thenReturn(true);
        when(secretValueRepository.findDistinctEncryptionKeyVersionsExcluding(1)).thenReturn(List.of(2));
        when(accountRepository.findDistinctEncryptionKeyVersionsExcluding(1)).thenReturn(List.of());
        stubSaveReturnsArgument();

        BrokerKeyRotationRun run = service.rotate();

        assertThat(run.getStatus()).isEqualTo(BrokerKeyRotationRunStatus.FAILED_PREFLIGHT);
        assertThat(run.getFailureReason()).isEqualTo("MISSING_SOURCE_KEY");
        verifyNoInteractions(batchProcessor);
    }

    @Test
    void rejectsANewRunWhileAnotherIsInProgressAndResumeIsDisabledByDefault() {
        BrokerKeyRotationService service = service(legacyKeyringProperties(testKey(1)), defaultRotationProperties());
        BrokerKeyRotationRun inProgress = BrokerKeyRotationRun.start(1, List.of(), 5, 0, "OPERATOR", nowFixed());
        when(runRepository.findFirstByStatus(BrokerKeyRotationRunStatus.IN_PROGRESS)).thenReturn(Optional.of(inProgress));
        stubSaveReturnsArgument();

        BrokerKeyRotationRun run = service.rotate();

        assertThat(run.getStatus()).isEqualTo(BrokerKeyRotationRunStatus.FAILED_PREFLIGHT);
        assertThat(run.getFailureReason()).isEqualTo("ANOTHER_ROTATION_IN_PROGRESS");
        verifyNoInteractions(batchProcessor);
        verifyNoInteractions(cipher);
    }

    @Test
    void refusesToResumeWhenTheInProgressRunTargetsADifferentVersionThanTheCurrentConfiguration() {
        BrokerKeyRotationProperties resumeEnabled = new BrokerKeyRotationProperties(false, 200, "OPERATOR", true);
        BrokerKeyRotationService service = service(legacyKeyringProperties(testKey(1)), resumeEnabled);
        BrokerKeyRotationRun inProgress = BrokerKeyRotationRun.start(2, List.of(), 5, 0, "OPERATOR", nowFixed());
        when(runRepository.findFirstByStatus(BrokerKeyRotationRunStatus.IN_PROGRESS)).thenReturn(Optional.of(inProgress));

        assertThatThrownBy(service::rotate).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(batchProcessor);
    }

    @Test
    void createsAndCompletesARunImmediatelyWhenNothingNeedsRotating() {
        BrokerKeyRotationService service = service(legacyKeyringProperties(testKey(1)), defaultRotationProperties());
        when(runRepository.findFirstByStatus(BrokerKeyRotationRunStatus.IN_PROGRESS)).thenReturn(Optional.empty());
        when(cipher.isConfigured()).thenReturn(true);
        when(secretValueRepository.findDistinctEncryptionKeyVersionsExcluding(1)).thenReturn(List.of());
        when(accountRepository.findDistinctEncryptionKeyVersionsExcluding(1)).thenReturn(List.of());
        when(secretValueRepository.countByEncryptionKeyVersionNot(1)).thenReturn(0L);
        when(accountRepository.countByEncryptionKeyVersionNot(1)).thenReturn(0L);
        when(secretValueRepository.findByEncryptionKeyVersionNotOrderByIdAsc(anyInt(), any()))
                .thenReturn(Page.empty());
        when(accountRepository.findByEncryptionKeyVersionNotOrderByIdAsc(anyInt(), any()))
                .thenReturn(Page.empty());
        when(batchProcessor.processNextSecretValueBatch(any()))
                .thenReturn(new BrokerKeyRotationBatchProcessor.BatchResult(0, 0, 0, 0));
        when(batchProcessor.processNextAccountBatch(any()))
                .thenReturn(new BrokerKeyRotationBatchProcessor.BatchResult(0, 0, 0, 0));
        stubSaveReturnsArgument();
        when(runRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        BrokerKeyRotationRun run = service.rotate();

        assertThat(run.getStatus()).isEqualTo(BrokerKeyRotationRunStatus.COMPLETED);
        assertThat(run.getSecretValuesTotal()).isZero();
        assertThat(run.getAccountsTotal()).isZero();
        assertThat(run.getCompletedAt()).isNotNull();
        verify(batchProcessor).processNextSecretValueBatch(any());
        verify(batchProcessor).processNextAccountBatch(any());
    }

    private void stubSaveReturnsArgument() {
        when(runRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private BrokerKeyRotationService service(
            BrokerCredentialKeyringProperties keyringProperties,
            BrokerKeyRotationProperties rotationProperties
    ) {
        return new BrokerKeyRotationService(
                secretValueRepository,
                accountRepository,
                runRepository,
                batchProcessor,
                cipher,
                keyringProperties,
                rotationProperties,
                FIXED_CLOCK
        );
    }

    private BrokerKeyRotationProperties defaultRotationProperties() {
        return new BrokerKeyRotationProperties(false, 200, "OPERATOR", false);
    }

    private BrokerCredentialKeyringProperties legacyKeyringProperties(String legacyKey) {
        return new BrokerCredentialKeyringProperties(legacyKey, List.of(), 1);
    }

    private java.time.LocalDateTime nowFixed() {
        return java.time.LocalDateTime.ofInstant(FIXED_CLOCK.instant(), FIXED_CLOCK.getZone());
    }

    private String testKey(int seed) {
        byte[] key = new byte[32];
        key[0] = (byte) seed;
        return Base64.getEncoder().encodeToString(key);
    }
}
