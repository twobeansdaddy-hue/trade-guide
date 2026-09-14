package com.tradeguide.migration;

import com.tradeguide.domain.broker.BrokerAccount;
import com.tradeguide.domain.broker.BrokerAccountStatus;
import com.tradeguide.domain.broker.BrokerConnection;
import com.tradeguide.domain.broker.BrokerConnectionSecretValue;
import com.tradeguide.domain.broker.BrokerKeyRotationRun;
import com.tradeguide.domain.broker.BrokerKeyRotationRunStatus;
import com.tradeguide.domain.broker.BrokerProvider;
import com.tradeguide.domain.member.Member;
import com.tradeguide.repository.broker.BrokerAccountRepository;
import com.tradeguide.repository.broker.BrokerConnectionRepository;
import com.tradeguide.repository.broker.BrokerConnectionSecretValueRepository;
import com.tradeguide.repository.broker.BrokerKeyRotationRunRepository;
import com.tradeguide.repository.member.MemberRepository;
import com.tradeguide.service.broker.AesGcmBrokerCredentialCipher;
import com.tradeguide.config.BrokerCredentialKeyringProperties;
import com.tradeguide.service.broker.BrokerCredentialCipher;
import com.tradeguide.service.broker.BrokerKeyRotationBatchProcessor;
import com.tradeguide.service.broker.BrokerKeyRotationService;
import com.tradeguide.service.broker.EncryptedBrokerCredential;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 증권사 자격 증명 암호화 키 로테이션을 실제 PostgreSQL에서 검증한다
 * (docs/agent-tasks/broker-credential-encryption-key-rotation.md §11).
 *
 * <p>키는 전부 0/1로 채운 더미 32바이트다. 실제 키나 실제 자격 증명은 쓰지 않는다. Docker가
 * 필요하며 기본 {@code test} 태스크에서 제외된다.
 */
@Tag("postgres")
@Testcontainers
@ActiveProfiles("test")
@SpringBootTest
class PostgresBrokerKeyRotationIntegrationTest {

    private static final String LEGACY_ACCOUNT_TYPE = "위탁";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
        // 버전 1과 2 모두 더미 키를 준다. 현재 버전은 2이므로 로테이션은 1 -> 2로 재암호화한다.
        registry.add("tradeguide.broker.encryption-keys[0].version", () -> "1");
        registry.add("tradeguide.broker.encryption-keys[0].value", () -> dummyKey((byte) 1));
        registry.add("tradeguide.broker.encryption-keys[1].version", () -> "2");
        registry.add("tradeguide.broker.encryption-keys[1].value", () -> dummyKey((byte) 2));
        registry.add("tradeguide.broker.encryption-current-version", () -> "2");
        // 오프라인 로테이션 진입점은 이 테스트에서 기동 시 자동 실행되지 않는다 — 서비스를
        // 직접 호출한다. 배치 크기를 작게 둬 "중단 후 재개" 시나리오를 여러 배치로 나눈다.
        registry.add("tradeguide.broker.rotation.batch-size", () -> "1");
        // 기본값은 거부이지만(§6.2), 이 테스트 클래스는 "운영자가 재개를 명시적으로 선택한"
        // 상황만 다룬다. 기본 거부 동작 자체는 BrokerKeyRotationServiceTest(Mockito)가 검증한다.
        registry.add("tradeguide.broker.rotation.resume-in-progress", () -> "true");
    }

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private BrokerConnectionRepository brokerConnectionRepository;

    @Autowired
    private BrokerConnectionSecretValueRepository secretValueRepository;

    @Autowired
    private BrokerAccountRepository accountRepository;

    @Autowired
    private BrokerKeyRotationRunRepository runRepository;

    @Autowired
    private BrokerKeyRotationService rotationService;

    @Autowired
    private BrokerKeyRotationBatchProcessor batchProcessor;

    @Autowired
    private BrokerCredentialCipher currentCipher;

    @Autowired
    private PlatformTransactionManager transactionManager;

    /** 버전 1로만 저장하는 더미 픽스처 전용 암호기다. 스프링 빈이 아니라 이 테스트가 직접 만든다. */
    private final BrokerCredentialCipher legacyOnlyCipher = new AesGcmBrokerCredentialCipher(
            new BrokerCredentialKeyringProperties(dummyKey((byte) 1), List.of(), 1));

    private Member member;

    @BeforeEach
    void setUp() {
        runRepository.deleteAll();
        accountRepository.deleteAll();
        secretValueRepository.deleteAll();
        brokerConnectionRepository.deleteAll();
        memberRepository.deleteAll();

        member = memberRepository.save(new Member("rotation@example.com", "rotation-user"));
    }

    @Test
    void reencryptsSecretValuesAndAccountsWhilePreservingPlaintextAndAccountStatus() {
        BrokerConnection connection = new BrokerConnection(member, BrokerProvider.TOSS_SECURITIES, "로테이션 연결");
        connection.replaceSecretValues(List.of(
                legacySecretValue("clientId", "dummy-client-id"),
                legacySecretValue("clientSecret", "dummy-client-secret")
        ));
        EncryptedBrokerCredential legacySequence = legacyOnlyCipher.encrypt("dummy-account-sequence");
        BrokerAccount account = new BrokerAccount(
                legacySequence.ciphertext(), legacySequence.initializationVector(),
                "*****1234", LEGACY_ACCOUNT_TYPE, legacySequence.keyVersion());
        connection.reconcileVerifiedAccounts(List.of(account));
        // 두 번째 검증에서 증권사가 이 계좌를 더 이상 반환하지 않았다고 가정해 DETACHED로
        // 만든다(detach()는 패키지 전용이라 이 경로로만 만들 수 있다).
        connection.reconcileVerifiedAccounts(List.of());
        brokerConnectionRepository.saveAndFlush(connection);
        LocalDateTime detachedAt = accountRepository.findAll().getFirst().getDetachedAt();
        assertThat(detachedAt).isNotNull();

        BrokerKeyRotationRun run = rotationService.rotate();

        assertThat(run.getStatus()).isEqualTo(BrokerKeyRotationRunStatus.COMPLETED);
        assertThat(run.getSecretValuesTotal()).isEqualTo(2);
        assertThat(run.getSecretValuesProcessed()).isEqualTo(2);
        assertThat(run.getAccountsTotal()).isEqualTo(1);
        assertThat(run.getAccountsProcessed()).isEqualTo(1);
        assertThat(run.isPartiallySkipped()).isFalse();

        List<BrokerConnectionSecretValue> rotatedValues = secretValueRepository.findAll();
        assertThat(rotatedValues).hasSize(2)
                .allSatisfy(value -> assertThat(value.getEncryptionKeyVersion()).isEqualTo(2));
        assertThat(currentCipher.decrypt(new EncryptedBrokerCredential(
                rotatedValues.stream().filter(v -> v.getFieldKey().equals("clientId")).findFirst().orElseThrow().getCiphertext(),
                rotatedValues.stream().filter(v -> v.getFieldKey().equals("clientId")).findFirst().orElseThrow().getInitializationVector(),
                2
        ))).isEqualTo("dummy-client-id");

        BrokerAccount rotatedAccount = accountRepository.findAll().getFirst();
        assertThat(rotatedAccount.getEncryptionKeyVersion()).isEqualTo(2);
        assertThat(currentCipher.decrypt(new EncryptedBrokerCredential(
                rotatedAccount.getEncryptedAccountSequence(),
                rotatedAccount.getAccountSequenceInitializationVector(),
                2
        ))).isEqualTo("dummy-account-sequence");
        // DETACHED 상태와 분리 시각은 로테이션이 절대 건드리지 않는다(§6.1-3).
        assertThat(rotatedAccount.getStatus()).isEqualTo(BrokerAccountStatus.DETACHED);
        assertThat(rotatedAccount.getDetachedAt()).isEqualTo(detachedAt);
    }

    @Test
    void idempotentlyResumesTheSameRunAfterASimulatedInterruption() {
        BrokerConnection connection = new BrokerConnection(member, BrokerProvider.TOSS_SECURITIES, "재개 연결");
        connection.replaceSecretValues(List.of(
                legacySecretValue("clientId", "dummy-client-id"),
                legacySecretValue("clientSecret", "dummy-client-secret")
        ));
        brokerConnectionRepository.saveAndFlush(connection);

        // 사전 점검을 거치지 않고 IN_PROGRESS 실행을 직접 만든다 — 이전 프로세스가 죽기 전에
        // 이미 사전 점검을 통과하고 실행을 시작해 둔 상태를 흉내낸다.
        BrokerKeyRotationRun interruptedRun = runRepository.saveAndFlush(
                BrokerKeyRotationRun.start(2, List.of(1), 2, 0, "OPERATOR", LocalDateTime.now()));

        // 배치 크기가 1이므로 한 번만 호출하면 값 하나만 처리하고 멈춘 것과 같다 — "죽기 전에
        // 커밋된 배치 하나"를 흉내낸다(§6.1-2, §6.2).
        BrokerKeyRotationBatchProcessor.BatchResult firstBatch =
                batchProcessor.processNextSecretValueBatch(interruptedRun);
        assertThat(firstBatch.processed()).isEqualTo(1);

        BrokerKeyRotationRun stillInProgress = runRepository.findById(interruptedRun.getId()).orElseThrow();
        assertThat(stillInProgress.getStatus()).isEqualTo(BrokerKeyRotationRunStatus.IN_PROGRESS);
        assertThat(stillInProgress.getSecretValuesProcessed()).isEqualTo(1);
        assertThat(secretValueRepository.countByEncryptionKeyVersionNot(2)).isEqualTo(1);

        // 이 테스트 클래스는 resume-in-progress=true로 기동했으므로(운영자가 재개를 명시적으로
        // 선택한 상황을 흉내낸다, §6.2, §7) rotate()는 새 실행을 만들지 않고 같은 실행을
        // 이어서 진행한다.
        BrokerKeyRotationRun completed = rotationService.rotate();

        assertThat(completed.getId()).isEqualTo(interruptedRun.getId());
        assertThat(completed.getStatus()).isEqualTo(BrokerKeyRotationRunStatus.COMPLETED);
        assertThat(completed.getSecretValuesProcessed()).isEqualTo(2);
        assertThat(completed.getSecretValuesTotal()).isEqualTo(2);
        assertThat(secretValueRepository.countByEncryptionKeyVersionNot(2)).isZero();
    }

    @Test
    void countsAPermanentlyCorruptedRowAsFailedWithoutBlockingOtherRowsOrLoopingForever() {
        BrokerConnection connection = new BrokerConnection(member, BrokerProvider.TOSS_SECURITIES, "손상 연결");
        connection.replaceSecretValues(List.of(
                legacySecretValue("clientId", "dummy-client-id"),
                // encryption_key_version은 1(사용 가능한 버전)이지만 ciphertext/iv가 그 키로
                // 만든 값이 아니므로 인증 태그 검증에서 항상 실패한다 — "복호화 실패" 시나리오다.
                new BrokerConnectionSecretValue("clientSecret", "not-a-real-ciphertext", "not-a-real-iv", 1)
        ));
        brokerConnectionRepository.saveAndFlush(connection);

        BrokerKeyRotationRun run = rotationService.rotate();

        // 영구 손상 행이 남아 있으므로 완료로 표시하지 않는다 — 부분 유니크 인덱스가 다음 실행을
        // 막아 운영자가 원인을 고칠 때까지 조용히 지나치지 못하게 한다(§6.2, §6.4).
        assertThat(run.getStatus()).isEqualTo(BrokerKeyRotationRunStatus.IN_PROGRESS);
        assertThat(run.getSecretValuesProcessed()).isEqualTo(1);
        assertThat(run.getSecretValuesFailed()).isEqualTo(1);

        List<BrokerConnectionSecretValue> values = secretValueRepository.findAll();
        assertThat(values).extracting(BrokerConnectionSecretValue::getFieldKey, BrokerConnectionSecretValue::getEncryptionKeyVersion)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("clientId", 2),
                        org.assertj.core.groups.Tuple.tuple("clientSecret", 1)
                );
    }

    /**
     * 조건부 UPDATE 자체(@Modifying 쿼리)는 실행 중인 트랜잭션이 있어야 한다. 실제 로테이션
     * 경로에서는 {@code BrokerKeyRotationBatchProcessor}의 {@code REQUIRES_NEW} 트랜잭션이 그
     * 경계를 제공한다. 테스트 메서드 자체에 {@code @Transactional}을 붙이면 스프링 테스트가
     * 끝에서 자동 롤백하면서 그 안의 {@code @BeforeEach} 정리 작업까지 되돌려 다른 테스트와
     * 간섭하므로, 대신 {@link TransactionTemplate}으로 실제 커밋되는 트랜잭션을 만든다.
     */
    @Test
    void conditionalUpdateAffectsNoRowWhenTheExpectedVersionNoLongerMatches() {
        BrokerConnection connection = new BrokerConnection(member, BrokerProvider.TOSS_SECURITIES, "경합 연결");
        connection.replaceSecretValues(List.of(legacySecretValue("clientId", "dummy-client-id")));
        brokerConnectionRepository.saveAndFlush(connection);
        Long valueId = secretValueRepository.findAll().getFirst().getId();
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        // 저장된 버전은 1인데, 다른 트랜잭션이 그사이 값을 바꿔 이미 버전 2가 됐다고 가정하고
        // 잘못된 기대 버전(99)으로 조건부 UPDATE를 시도한다 — 사용자 쓰기와의 경합을
        // 흉내낸다(§6.3).
        int affectedWithWrongExpectedVersion = transactionTemplate.execute(status ->
                secretValueRepository.rotateEncryptionIfVersionMatches(valueId, "new-ciphertext", "new-iv", 2, 99));
        assertThat(affectedWithWrongExpectedVersion).isZero();
        assertThat(secretValueRepository.findById(valueId).orElseThrow().getEncryptionKeyVersion()).isEqualTo(1);

        int affectedWithCorrectExpectedVersion = transactionTemplate.execute(status ->
                secretValueRepository.rotateEncryptionIfVersionMatches(valueId, "new-ciphertext", "new-iv", 2, 1));
        assertThat(affectedWithCorrectExpectedVersion).isEqualTo(1);
        assertThat(secretValueRepository.findById(valueId).orElseThrow().getEncryptionKeyVersion()).isEqualTo(2);
    }

    @Test
    void enforcesAtMostOneInProgressRunAtTheDatabaseLevel() {
        runRepository.saveAndFlush(BrokerKeyRotationRun.start(2, List.of(), 0, 0, "OPERATOR", LocalDateTime.now()));

        assertThatThrownBy(() -> runRepository.saveAndFlush(
                BrokerKeyRotationRun.start(2, List.of(), 0, 0, "OPERATOR", LocalDateTime.now())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private BrokerConnectionSecretValue legacySecretValue(String fieldKey, String dummyPlaintext) {
        EncryptedBrokerCredential encrypted = legacyOnlyCipher.encrypt(dummyPlaintext);
        return new BrokerConnectionSecretValue(
                fieldKey, encrypted.ciphertext(), encrypted.initializationVector(), encrypted.keyVersion());
    }

    private static String dummyKey(byte seed) {
        byte[] key = new byte[32];
        key[0] = seed;
        return Base64.getEncoder().encodeToString(key);
    }
}
