package com.tradeguide.service.broker;

import com.tradeguide.config.BrokerCredentialKeyringProperties;
import com.tradeguide.config.BrokerKeyRotationProperties;
import com.tradeguide.domain.broker.BrokerAccount;
import com.tradeguide.domain.broker.BrokerConnectionSecretValue;
import com.tradeguide.domain.broker.BrokerKeyRotationRun;
import com.tradeguide.domain.broker.BrokerKeyRotationRunStatus;
import com.tradeguide.repository.broker.BrokerAccountRepository;
import com.tradeguide.repository.broker.BrokerConnectionSecretValueRepository;
import com.tradeguide.repository.broker.BrokerKeyRotationRunRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 증권사 자격 증명 암호화 키를 오프라인으로 재암호화하는 로테이션 배치의 진입점이다
 * (docs/agent-tasks/broker-credential-encryption-key-rotation.md).
 *
 * <p>이 서비스는 어떤 HTTP 경로에도 연결하지 않는다. 유일한 호출자는
 * {@link BrokerKeyRotationRunner}이며, 그마저도 운영자가 명시적으로 켠
 * {@code tradeguide.broker.rotation.run-on-startup}이 참일 때만 실행된다(§1 결정 1, §9).
 *
 * <p>로테이션은 평문을 바꾸지 않는다. 저장된 {@code encryption_key_version}으로 복호화한 뒤
 * 항상 {@link BrokerCredentialKeyringProperties#encryptionCurrentVersion()}(현재 키링 버전)
 * 으로 재암호화한다 — "같은 값, 다른 봉투"다(§3, §6.1-2). 목표 버전을 별도 파라미터로 받지
 * 않는 이유는, 그렇게 하면 임의 버전으로 재암호화하는 도구가 되어 §5-1의 거부 규칙을
 * 코드가 스스로 어기게 되기 때문이다.
 *
 * <p>실제 배치 한 건(값 단위/계좌 단위)은 {@link BrokerKeyRotationBatchProcessor}가
 * 별도 빈으로 수행한다. 이 서비스가 그 메서드를 같은 인스턴스에서 직접 호출하면 스프링
 * 자기 호출 한계 때문에 {@code @Transactional(REQUIRES_NEW)}가 적용되지 않으므로, 반드시
 * 주입받은 배치 처리기를 통해서만 호출한다.
 */
@Service
public class BrokerKeyRotationService {

    private static final int LEGACY_KEY_VERSION = 1;

    /** 원인 예외 메시지가 아니라 이 고정 사유 코드만 감사 기록에 남는다(§8). */
    private static final String REASON_ENCRYPTION_NOT_CONFIGURED = "ENCRYPTION_NOT_CONFIGURED";
    private static final String REASON_TARGET_KEY_MISSING = "TARGET_KEY_MISSING";
    private static final String REASON_MISSING_SOURCE_KEY = "MISSING_SOURCE_KEY";
    private static final String REASON_SAMPLE_ROUNDTRIP_MISMATCH = "SAMPLE_ROUNDTRIP_MISMATCH";
    private static final String REASON_ANOTHER_ROTATION_IN_PROGRESS = "ANOTHER_ROTATION_IN_PROGRESS";

    private final BrokerConnectionSecretValueRepository secretValueRepository;
    private final BrokerAccountRepository accountRepository;
    private final BrokerKeyRotationRunRepository runRepository;
    private final BrokerKeyRotationBatchProcessor batchProcessor;
    private final BrokerCredentialCipher cipher;
    private final BrokerCredentialKeyringProperties keyringProperties;
    private final BrokerKeyRotationProperties rotationProperties;
    private final Clock clock;

    public BrokerKeyRotationService(
            BrokerConnectionSecretValueRepository secretValueRepository,
            BrokerAccountRepository accountRepository,
            BrokerKeyRotationRunRepository runRepository,
            BrokerKeyRotationBatchProcessor batchProcessor,
            BrokerCredentialCipher cipher,
            BrokerCredentialKeyringProperties keyringProperties,
            BrokerKeyRotationProperties rotationProperties,
            Clock clock
    ) {
        this.secretValueRepository = secretValueRepository;
        this.accountRepository = accountRepository;
        this.runRepository = runRepository;
        this.batchProcessor = batchProcessor;
        this.cipher = cipher;
        this.keyringProperties = keyringProperties;
        this.rotationProperties = rotationProperties;
        this.clock = clock;
    }

    /**
     * 로테이션 실행 진입점이다. 이미 진행 중인 실행이 있고
     * {@code tradeguide.broker.rotation.resume-in-progress}가 꺼져 있으면(기본값) 아무 행도
     * 손대지 않고 거부한다 — 자동 재시작은 하지 않는다는 원칙 때문이다(§6.2, §7). 운영자가
     * 그 값을 켰을 때만 같은 실행을 이어서 진행한다.
     */
    public BrokerKeyRotationRun rotate() {
        int targetVersion = keyringProperties.encryptionCurrentVersion();
        LocalDateTime now = LocalDateTime.now(clock);

        Optional<BrokerKeyRotationRun> inProgress = runRepository.findFirstByStatus(BrokerKeyRotationRunStatus.IN_PROGRESS);
        BrokerKeyRotationRun run;
        if (inProgress.isPresent()) {
            if (!rotationProperties.resumeInProgress()) {
                return runRepository.save(BrokerKeyRotationRun.failedPreflight(
                        targetVersion, rotationProperties.triggeredBy(), now, REASON_ANOTHER_ROTATION_IN_PROGRESS));
            }
            run = inProgress.get();
            if (run.getTargetKeyVersion() != targetVersion) {
                throw new IllegalStateException(
                        "이미 진행 중인 로테이션 실행의 목표 버전이 현재 설정과 다릅니다. "
                                + "운영자가 직접 실행 상태를 확인해야 합니다."
                );
            }
        } else {
            Optional<String> preflightFailure = preflightFailureReason(targetVersion);
            if (preflightFailure.isPresent()) {
                return runRepository.save(BrokerKeyRotationRun.failedPreflight(
                        targetVersion, rotationProperties.triggeredBy(), now, preflightFailure.get()));
            }

            long secretValuesTotal = secretValueRepository.countByEncryptionKeyVersionNot(targetVersion);
            long accountsTotal = accountRepository.countByEncryptionKeyVersionNot(targetVersion);
            List<Integer> observedVersions = observedSourceVersions(targetVersion);

            run = BrokerKeyRotationRun.start(
                    targetVersion, observedVersions, (int) secretValuesTotal, (int) accountsTotal,
                    rotationProperties.triggeredBy(), now);
            run = runRepository.saveAndFlush(run);
        }

        // fetched == 0이면 남은 행이 없다는 뜻이고, madeProgress()가 거짓이면(조회된 행 전부가
        // 실패) 영구 손상된 행이 매 페이지 앞쪽에 다시 걸려 무한 재시도를 만드는 것을 막기 위해
        // 멈춘다(§6.4, BrokerKeyRotationBatchProcessor.BatchResult 참고).
        BrokerKeyRotationBatchProcessor.BatchResult secretValueResult;
        do {
            secretValueResult = batchProcessor.processNextSecretValueBatch(run);
        } while (secretValueResult.fetched() > 0 && secretValueResult.madeProgress());

        BrokerKeyRotationBatchProcessor.BatchResult accountResult;
        do {
            accountResult = batchProcessor.processNextAccountBatch(run);
        } while (accountResult.fetched() > 0 && accountResult.madeProgress());

        // 남은 행이 있다면(위 두 반복 모두 진전 없이 멈췄다는 뜻) 영구 손상된 행이 남아 있는
        // 것이다. 완료로 표시하지 않고 IN_PROGRESS로 남겨 둔다 — 부분 유니크 인덱스가 다음
        // 실행을 계속 막으므로, 운영자가 원인을 고치고 resume-in-progress로 명시적으로
        // 이어서 실행하기 전에는 아무도 이 상태를 조용히 지나치지 못한다(§6.2, §6.4).
        boolean remaining = secretValueRepository.countByEncryptionKeyVersionNot(targetVersion) > 0
                || accountRepository.countByEncryptionKeyVersionNot(targetVersion) > 0;
        if (remaining) {
            return run;
        }

        run.markCompleted(LocalDateTime.now(clock));
        return runRepository.save(run);
    }

    /**
     * §5의 사전 점검이다. 하나라도 실패하면 사유 코드만 반환하고, 호출자는 어떤 행도 손대지
     * 않은 채 {@code FAILED_PREFLIGHT} 실행 기록만 남긴다.
     */
    private Optional<String> preflightFailureReason(int targetVersion) {
        if (!cipher.isConfigured()) {
            return Optional.of(REASON_ENCRYPTION_NOT_CONFIGURED);
        }

        Set<Integer> availableVersions = availableKeyVersions();
        if (!availableVersions.contains(targetVersion)) {
            return Optional.of(REASON_TARGET_KEY_MISSING);
        }

        List<Integer> observedVersions = observedSourceVersions(targetVersion);
        for (Integer observedVersion : observedVersions) {
            if (!availableVersions.contains(observedVersion)) {
                return Optional.of(REASON_MISSING_SOURCE_KEY);
            }
        }

        if (!sampleRoundTripMatches(targetVersion)) {
            return Optional.of(REASON_SAMPLE_ROUNDTRIP_MISMATCH);
        }

        return Optional.empty();
    }

    /**
     * 대상 행 중 임의 1건(값 단위 표에 있으면 그 행, 없으면 계좌 표의 행)을 복호화 → 재암호화
     * → 다시 복호화해 원래 평문과 일치하는지 확인한다. 대상 행이 아예 없으면(로테이션할 것이
     * 없음) 검증할 대상이 없으므로 통과로 취급한다(§5-5).
     */
    private boolean sampleRoundTripMatches(int targetVersion) {
        Page<BrokerConnectionSecretValue> secretValueSample = secretValueRepository
                .findByEncryptionKeyVersionNotOrderByIdAsc(targetVersion, PageRequest.of(0, 1));
        if (!secretValueSample.isEmpty()) {
            BrokerConnectionSecretValue sample = secretValueSample.getContent().getFirst();
            return roundTripMatches(sample.getCiphertext(), sample.getInitializationVector(), sample.getEncryptionKeyVersion());
        }

        Page<BrokerAccount> accountSample = accountRepository
                .findByEncryptionKeyVersionNotOrderByIdAsc(targetVersion, PageRequest.of(0, 1));
        if (!accountSample.isEmpty()) {
            BrokerAccount sample = accountSample.getContent().getFirst();
            return roundTripMatches(
                    sample.getEncryptedAccountSequence(), sample.getAccountSequenceInitializationVector(), sample.getEncryptionKeyVersion());
        }

        return true;
    }

    private boolean roundTripMatches(String ciphertext, String initializationVector, int keyVersion) {
        try {
            String originalPlaintext = cipher.decrypt(new EncryptedBrokerCredential(ciphertext, initializationVector, keyVersion));
            EncryptedBrokerCredential reEncrypted = cipher.encrypt(originalPlaintext);
            String roundTrippedPlaintext = cipher.decrypt(reEncrypted);
            return originalPlaintext.equals(roundTrippedPlaintext);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private List<Integer> observedSourceVersions(int targetVersion) {
        Set<Integer> observed = new HashSet<>();
        observed.addAll(secretValueRepository.findDistinctEncryptionKeyVersionsExcluding(targetVersion));
        observed.addAll(accountRepository.findDistinctEncryptionKeyVersionsExcluding(targetVersion));
        return observed.stream().sorted().toList();
    }

    /**
     * {@link AesGcmBrokerCredentialCipher}의 생성자와 같은 규칙으로 "값이 실제로 설정된"
     * 버전 집합을 계산한다. 암호기는 이 정보를 별도로 노출하지 않으므로(§4의 설정 계약은
     * 이름만 다루는 레코드다) 여기서 같은 규칙을 다시 계산한다.
     */
    private Set<Integer> availableKeyVersions() {
        Set<Integer> versions = new HashSet<>();
        for (BrokerCredentialKeyringProperties.KeyEntry entry : keyringProperties.encryptionKeys()) {
            if (!entry.value().isBlank()) {
                versions.add(entry.version());
            }
        }
        if (versions.isEmpty() && !keyringProperties.encryptionKey().isBlank()) {
            versions.add(LEGACY_KEY_VERSION);
        }
        return versions;
    }
}
