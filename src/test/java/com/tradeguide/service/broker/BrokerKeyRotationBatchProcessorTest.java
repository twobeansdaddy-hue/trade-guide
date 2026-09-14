package com.tradeguide.service.broker;

import com.tradeguide.config.BrokerKeyRotationProperties;
import com.tradeguide.domain.broker.BrokerConnectionSecretValue;
import com.tradeguide.domain.broker.BrokerKeyRotationRun;
import com.tradeguide.repository.broker.BrokerAccountRepository;
import com.tradeguide.repository.broker.BrokerConnectionSecretValueRepository;
import com.tradeguide.repository.broker.BrokerKeyRotationRunRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 배치 한 건의 처리·스킵·실패 집계 규칙만 다룬다
 * (docs/agent-tasks/broker-credential-encryption-key-rotation.md §6.1-2, §6.3, §6.4).
 * 실제 조건부 UPDATE의 SQL 동작(경합 시 0건 반환)은
 * {@code PostgresBrokerKeyRotationIntegrationTest}가 더미 PostgreSQL로 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class BrokerKeyRotationBatchProcessorTest {

    @Mock
    private BrokerConnectionSecretValueRepository secretValueRepository;

    @Mock
    private BrokerAccountRepository accountRepository;

    @Mock
    private BrokerKeyRotationRunRepository runRepository;

    @Mock
    private BrokerCredentialCipher cipher;

    private final BrokerKeyRotationProperties rotationProperties =
            new BrokerKeyRotationProperties(false, 200, "OPERATOR", false);

    @Test
    void countsAsSkippedWhenTheConditionalUpdateAffectsNoRowBecauseTheVersionChangedConcurrently() {
        BrokerKeyRotationBatchProcessor processor = new BrokerKeyRotationBatchProcessor(
                secretValueRepository, accountRepository, runRepository, cipher, rotationProperties);
        BrokerKeyRotationRun run = BrokerKeyRotationRun.start(2, List.of(1), 1, 0, "OPERATOR", LocalDateTime.now());

        BrokerConnectionSecretValue value = new BrokerConnectionSecretValue("clientId", "cipher-v1", "iv-v1", 1);
        when(secretValueRepository.findByEncryptionKeyVersionNotOrderByIdAsc(eq(2), any()))
                .thenReturn(new PageImpl<>(List.of(value)));
        when(cipher.decrypt(any())).thenReturn("plaintext");
        when(cipher.encrypt("plaintext"))
                .thenReturn(new EncryptedBrokerCredential("cipher-v2", "iv-v2", 2));
        // 사용자가 그사이 자격 증명을 교체해 저장된 버전이 이미 바뀐 상황을 흉내낸다(§6.3).
        when(secretValueRepository.rotateEncryptionIfVersionMatches(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(0);

        BrokerKeyRotationBatchProcessor.BatchResult result = processor.processNextSecretValueBatch(run);

        assertThat(result.fetched()).isEqualTo(1);
        assertThat(result.processed()).isZero();
        assertThat(result.skipped()).isEqualTo(1);
        assertThat(result.failed()).isZero();
        assertThat(result.madeProgress()).isTrue();
        assertThat(run.getSecretValuesSkipped()).isEqualTo(1);
        assertThat(run.getSecretValuesProcessed()).isZero();
    }

    @Test
    void countsAsFailedAndContinuesWhenDecryptionFailsForOneRow() {
        BrokerKeyRotationBatchProcessor processor = new BrokerKeyRotationBatchProcessor(
                secretValueRepository, accountRepository, runRepository, cipher, rotationProperties);
        BrokerKeyRotationRun run = BrokerKeyRotationRun.start(2, List.of(1), 1, 0, "OPERATOR", LocalDateTime.now());

        BrokerConnectionSecretValue corrupted = new BrokerConnectionSecretValue("clientId", "corrupted", "iv", 1);
        when(secretValueRepository.findByEncryptionKeyVersionNotOrderByIdAsc(eq(2), any()))
                .thenReturn(new PageImpl<>(List.of(corrupted)));
        when(cipher.decrypt(any())).thenThrow(new IllegalStateException("증권사 자격 증명을 복호화하지 못했습니다."));

        BrokerKeyRotationBatchProcessor.BatchResult result = processor.processNextSecretValueBatch(run);

        assertThat(result.fetched()).isEqualTo(1);
        assertThat(result.processed()).isZero();
        assertThat(result.skipped()).isZero();
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.madeProgress()).isFalse();
        assertThat(run.getSecretValuesFailed()).isEqualTo(1);
        verify(secretValueRepository, never()).rotateEncryptionIfVersionMatches(any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    void returnsEmptyResultWhenNothingIsLeftToRotate() {
        BrokerKeyRotationBatchProcessor processor = new BrokerKeyRotationBatchProcessor(
                secretValueRepository, accountRepository, runRepository, cipher, rotationProperties);
        BrokerKeyRotationRun run = BrokerKeyRotationRun.start(2, List.of(), 0, 0, "OPERATOR", LocalDateTime.now());

        when(secretValueRepository.findByEncryptionKeyVersionNotOrderByIdAsc(eq(2), any()))
                .thenReturn(Page.empty());

        BrokerKeyRotationBatchProcessor.BatchResult result = processor.processNextSecretValueBatch(run);

        assertThat(result.fetched()).isZero();
        assertThat(result.madeProgress()).isFalse();
    }
}
