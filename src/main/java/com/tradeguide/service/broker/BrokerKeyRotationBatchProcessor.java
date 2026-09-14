package com.tradeguide.service.broker;

import com.tradeguide.config.BrokerKeyRotationProperties;
import com.tradeguide.domain.broker.BrokerAccount;
import com.tradeguide.domain.broker.BrokerConnectionSecretValue;
import com.tradeguide.domain.broker.BrokerKeyRotationRun;
import com.tradeguide.repository.broker.BrokerAccountRepository;
import com.tradeguide.repository.broker.BrokerConnectionSecretValueRepository;
import com.tradeguide.repository.broker.BrokerKeyRotationRunRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 암호화 키 로테이션의 배치 한 건을 실행한다
 * (docs/agent-tasks/broker-credential-encryption-key-rotation.md §6.1-2, §6.1-3, §6.2).
 *
 * <p>{@link BrokerKeyRotationService}와 별도 빈으로 분리한 이유는 스프링 자기 호출
 * 한계 때문이다. {@code rotate()}가 이 클래스의 메서드를 같은 인스턴스에서 직접 호출하면
 * {@code @Transactional(REQUIRES_NEW)}가 프록시를 거치지 않아 적용되지 않는다. 별도 빈으로
 * 두면 호출이 항상 스프링이 관리하는 프록시를 거치므로 각 배치가 실제로 독립된 트랜잭션이
 * 된다.
 */
@Service
public class BrokerKeyRotationBatchProcessor {

    private final BrokerConnectionSecretValueRepository secretValueRepository;
    private final BrokerAccountRepository accountRepository;
    private final BrokerKeyRotationRunRepository runRepository;
    private final BrokerCredentialCipher cipher;
    private final BrokerKeyRotationProperties rotationProperties;

    public BrokerKeyRotationBatchProcessor(
            BrokerConnectionSecretValueRepository secretValueRepository,
            BrokerAccountRepository accountRepository,
            BrokerKeyRotationRunRepository runRepository,
            BrokerCredentialCipher cipher,
            BrokerKeyRotationProperties rotationProperties
    ) {
        this.secretValueRepository = secretValueRepository;
        this.accountRepository = accountRepository;
        this.runRepository = runRepository;
        this.cipher = cipher;
        this.rotationProperties = rotationProperties;
    }

    /**
     * 배치 한 건의 결과다. {@code fetched == 0}이면 더 처리할 행이 없다는 뜻이고,
     * {@code processed == 0 && skipped == 0}이면(즉 조회된 행 전부가 실패) 그 배치는
     * 아무 진전도 만들지 못했다는 뜻이다. 실패하는 행은 버전이 바뀌지 않으므로 항상
     * id 오름차순 조회의 앞쪽에 남아 매 페이지에 다시 걸린다 — 호출자가 이 신호로
     * 반복을 멈추지 않으면 영구 손상된 행 하나가 무한 재시도를 만든다(§6.4).
     */
    public record BatchResult(int fetched, int processed, int skipped, int failed) {
        public boolean madeProgress() {
            return processed > 0 || skipped > 0;
        }
    }

    /**
     * 값 단위 배치 한 건이다. 독립된 트랜잭션으로 커밋되어 프로세스가 중간에 죽어도 이미 처리한
     * 배치는 그대로 남는다(§6.1-2, §6.2).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BatchResult processNextSecretValueBatch(BrokerKeyRotationRun run) {
        int targetVersion = run.getTargetKeyVersion();
        Page<BrokerConnectionSecretValue> page = secretValueRepository.findByEncryptionKeyVersionNotOrderByIdAsc(
                targetVersion, PageRequest.of(0, rotationProperties.batchSize()));
        if (page.isEmpty()) {
            return new BatchResult(0, 0, 0, 0);
        }

        int processed = 0;
        int skipped = 0;
        int failed = 0;
        for (BrokerConnectionSecretValue value : page) {
            int readVersion = value.getEncryptionKeyVersion();
            try {
                // 복호화가 애플리케이션 코드 안에서 실패하면 어떤 SQL도 실행하지 않으므로,
                // 이 행을 실패로 기록하고 다음 행을 계속 처리해도 트랜잭션이 손상되지 않는다.
                // (PostgreSQL은 SQL 오류가 실제로 발생하면 트랜잭션 전체를 중단시키므로, 이
                // catch는 조건부 UPDATE 자체의 실패가 아니라 그 앞의 복호화·암호화 실패만
                // 안전하게 흡수한다는 전제로 설계했다.)
                String plaintext = cipher.decrypt(new EncryptedBrokerCredential(
                        value.getCiphertext(), value.getInitializationVector(), readVersion));
                EncryptedBrokerCredential reEncrypted = cipher.encrypt(plaintext);
                int updated = secretValueRepository.rotateEncryptionIfVersionMatches(
                        value.getId(),
                        reEncrypted.ciphertext(),
                        reEncrypted.initializationVector(),
                        reEncrypted.keyVersion(),
                        readVersion
                );
                if (updated == 1) {
                    processed++;
                } else {
                    skipped++;
                }
            } catch (RuntimeException exception) {
                failed++;
            }
        }

        run.recordSecretValueBatch(processed, skipped, failed);
        runRepository.save(run);
        return new BatchResult(page.getNumberOfElements(), processed, skipped, failed);
    }

    /** 계좌 일련번호 배치 한 건이다. {@link #processNextSecretValueBatch}와 같은 원칙을 따른다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BatchResult processNextAccountBatch(BrokerKeyRotationRun run) {
        int targetVersion = run.getTargetKeyVersion();
        Page<BrokerAccount> page = accountRepository.findByEncryptionKeyVersionNotOrderByIdAsc(
                targetVersion, PageRequest.of(0, rotationProperties.batchSize()));
        if (page.isEmpty()) {
            return new BatchResult(0, 0, 0, 0);
        }

        int processed = 0;
        int skipped = 0;
        int failed = 0;
        for (BrokerAccount account : page) {
            int readVersion = account.getEncryptionKeyVersion();
            try {
                String plaintext = cipher.decrypt(new EncryptedBrokerCredential(
                        account.getEncryptedAccountSequence(),
                        account.getAccountSequenceInitializationVector(),
                        readVersion
                ));
                EncryptedBrokerCredential reEncrypted = cipher.encrypt(plaintext);
                // status/detached_at/masked_account_number는 SET 절에 없다 — DETACHED 계좌가
                // 로테이션만으로 ACTIVE로 되돌아가는 부작용을 막는다(§6.1-3).
                int updated = accountRepository.rotateEncryptionIfVersionMatches(
                        account.getId(),
                        reEncrypted.ciphertext(),
                        reEncrypted.initializationVector(),
                        reEncrypted.keyVersion(),
                        readVersion
                );
                if (updated == 1) {
                    processed++;
                } else {
                    skipped++;
                }
            } catch (RuntimeException exception) {
                failed++;
            }
        }

        run.recordAccountBatch(processed, skipped, failed);
        runRepository.save(run);
        return new BatchResult(page.getNumberOfElements(), processed, skipped, failed);
    }
}
