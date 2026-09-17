package com.tradeguide.service.broker;

import com.tradeguide.domain.broker.BrokerCredentials;
import com.tradeguide.domain.broker.BrokerAccount;
import com.tradeguide.domain.broker.BrokerAccountStatus;
import com.tradeguide.domain.broker.BrokerConnection;
import com.tradeguide.domain.broker.BrokerConnectionCandidateAccount;
import com.tradeguide.domain.broker.BrokerConnectionSecretValue;
import com.tradeguide.domain.broker.BrokerProvider;
import com.tradeguide.domain.broker.PortfolioBrokerHoldingAdjustment;
import com.tradeguide.domain.broker.PortfolioBrokerHoldingAdjustmentStatus;
import com.tradeguide.domain.broker.PortfolioBrokerHoldingImport;
import com.tradeguide.domain.broker.PortfolioBrokerHoldingImportStatus;
import com.tradeguide.domain.member.Member;
import com.tradeguide.exception.BrokerConnectionDeletionBlockedException;
import com.tradeguide.exception.BrokerConnectionUnavailableException;
import com.tradeguide.repository.broker.BrokerConnectionRepository;
import com.tradeguide.repository.broker.PortfolioBrokerHoldingAdjustmentRepository;
import com.tradeguide.repository.broker.PortfolioBrokerHoldingImportRepository;
import com.tradeguide.repository.broker.PortfolioBrokerHoldingSnapshotRepository;
import com.tradeguide.repository.broker.PortfolioBrokerLinkRepository;
import com.tradeguide.repository.member.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BrokerConnectionServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private BrokerConnectionRepository brokerConnectionRepository;

    @Mock
    private PortfolioBrokerLinkRepository portfolioBrokerLinkRepository;

    @Mock
    private PortfolioBrokerHoldingSnapshotRepository portfolioBrokerHoldingSnapshotRepository;

    @Mock
    private PortfolioBrokerHoldingImportRepository portfolioBrokerHoldingImportRepository;

    @Mock
    private PortfolioBrokerHoldingAdjustmentRepository portfolioBrokerHoldingAdjustmentRepository;

    @Mock
    private BrokerCredentialCipher brokerCredentialCipher;

    /** 실제 자격 증명 검증은 하지 않는 가짜 토스증권 구현체로 채운 실제 레지스트리다. */
    private final FakeTossConnectionVerifier connectionVerifier = new FakeTossConnectionVerifier();

    private final BrokerProviderRegistry brokerProviderRegistry =
            new BrokerProviderRegistry(List.of(connectionVerifier), List.of(), List.of(), List.of());

    private BrokerConnectionService brokerConnectionService;

    @BeforeEach
    void setUp() {
        brokerConnectionService = new BrokerConnectionService(
                memberRepository,
                brokerConnectionRepository,
                portfolioBrokerLinkRepository,
                portfolioBrokerHoldingSnapshotRepository,
                portfolioBrokerHoldingImportRepository,
                portfolioBrokerHoldingAdjustmentRepository,
                brokerCredentialCipher,
                new BrokerCredentialLoader(brokerCredentialCipher),
                brokerProviderRegistry
        );
    }

    @Test
    void createsConnectionWithEncryptedCredentialsOnly() {
        Member member = new Member("broker@example.com", "broker-user");
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        when(brokerCredentialCipher.encrypt("test-client-id"))
                .thenReturn(new EncryptedBrokerCredential("encrypted-id", "id-iv", 1));
        when(brokerCredentialCipher.encrypt("test-client-secret"))
                .thenReturn(new EncryptedBrokerCredential("encrypted-secret", "secret-iv", 1));
        when(brokerConnectionRepository.save(any(BrokerConnection.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        BrokerConnection connection = brokerConnectionService.createBrokerConnection(
                1L,
                BrokerProvider.TOSS_SECURITIES,
                "개인 토스증권",
                Map.of("clientId", "test-client-id", "clientSecret", "test-client-secret")
        );

        ArgumentCaptor<BrokerConnection> connectionCaptor =
                ArgumentCaptor.forClass(BrokerConnection.class);
        verify(brokerConnectionRepository).save(connectionCaptor.capture());
        assertThat(connection).isSameAs(connectionCaptor.getValue());
        assertThat(connection.getProvider()).isEqualTo(BrokerProvider.TOSS_SECURITIES);
        assertThat(connection.getDisplayName()).isEqualTo("개인 토스증권");
        verify(brokerCredentialCipher).encrypt("test-client-id");
        verify(brokerCredentialCipher).encrypt("test-client-secret");

        // 항목마다 한 행씩 저장한다. 열이 아니라 행이므로 항목이 늘어도 스키마가 그대로다.
        assertThat(connection.getSecretValues())
                .extracting(
                        BrokerConnectionSecretValue::getFieldKey,
                        BrokerConnectionSecretValue::getCiphertext,
                        BrokerConnectionSecretValue::getInitializationVector,
                        BrokerConnectionSecretValue::getEncryptionKeyVersion
                )
                .containsExactlyInAnyOrder(
                        tuple("clientId", "encrypted-id", "id-iv", 1),
                        tuple("clientSecret", "encrypted-secret", "secret-iv", 1)
                );
    }

    /**
     * 명세에 없는 키는 조용히 버리지 않고 거부한다. 무시하면 오타 난 키가 통과해 연결이
     * 만들어지고, 검증 단계에 가서야 이유를 알 수 없는 실패로 나타난다.
     * 저장은 한 행도 일어나지 않아야 한다.
     */
    @Test
    void rejectsCredentialKeyThatIsNotDeclaredByTheProviderWithoutPersistingAnything() {
        assertThatThrownBy(() -> brokerConnectionService.createBrokerConnection(
                1L,
                BrokerProvider.TOSS_SECURITIES,
                "개인 토스증권",
                Map.of(
                        "clientId", "test-client-id",
                        "clientSecret", "test-client-secret",
                        "clientSecrets", "typo-value"
                )
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("지원하지 않는 자격 증명 항목입니다: clientSecrets");

        verify(brokerCredentialCipher, never()).encrypt(any());
        verify(brokerConnectionRepository, never()).save(any(BrokerConnection.class));
    }

    @Test
    void rejectsMissingRequiredCredentialFieldWithoutExposingTheSubmittedValue() {
        Map<String, String> credentials = new java.util.LinkedHashMap<>();
        credentials.put("clientId", "leak-marker-id");
        credentials.put("clientSecret", "   ");

        assertThatThrownBy(() -> brokerConnectionService.createBrokerConnection(
                1L, BrokerProvider.TOSS_SECURITIES, "개인 토스증권", credentials))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("자격 증명 항목이 비어 있습니다: clientSecret")
                .hasMessageNotContaining("leak-marker-id");

        verify(brokerCredentialCipher, never()).encrypt(any());
        verify(brokerConnectionRepository, never()).save(any(BrokerConnection.class));
    }

    /**
     * 상한이 없으면 요청 본문 하나로 저장소를 채울 수 있다. 오류 메시지에는 길이를 넘긴
     * 값이 아니라 항목 키만 담는다.
     */
    @Test
    void rejectsCredentialValueLongerThanTheStorageLimit() {
        String tooLong = "x".repeat(BrokerConnectionService.MAX_CREDENTIAL_VALUE_LENGTH + 1);

        assertThatThrownBy(() -> brokerConnectionService.createBrokerConnection(
                1L,
                BrokerProvider.TOSS_SECURITIES,
                "개인 토스증권",
                Map.of("clientId", tooLong, "clientSecret", "test-client-secret")
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("자격 증명 항목이 512자를 넘습니다: clientId")
                .hasMessageNotContaining(tooLong);

        verify(brokerConnectionRepository, never()).save(any(BrokerConnection.class));
    }

    @Test
    void rejectsEmptyCredentialMap() {
        assertThatThrownBy(() -> brokerConnectionService.createBrokerConnection(
                1L, BrokerProvider.TOSS_SECURITIES, "개인 토스증권", Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("자격 증명은 필수입니다.");

        verify(brokerConnectionRepository, never()).save(any(BrokerConnection.class));
    }

    @Test
    void doesNotPersistConnectionWhenEncryptionKeyIsUnavailable() {
        Member member = new Member("broker@example.com", "broker-user");
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        doThrow(new BrokerConnectionUnavailableException("증권사 연결 암호화 키가 설정되지 않았습니다."))
                .when(brokerCredentialCipher)
                .encrypt("test-client-id");

        assertThatThrownBy(() -> brokerConnectionService.createBrokerConnection(
                1L,
                BrokerProvider.TOSS_SECURITIES,
                "개인 토스증권",
                Map.of("clientId", "test-client-id", "clientSecret", "test-client-secret")
        ))
                .isInstanceOf(BrokerConnectionUnavailableException.class);

        verify(brokerConnectionRepository, never()).save(any(BrokerConnection.class));
    }

    @Test
    void removesPortfolioLinksAndDerivedSnapshotsWhenBrokerConnectionIsDeleted() {
        BrokerConnection connection = connection();
        when(brokerConnectionRepository.findByMember_IdAndId(1L, 5L)).thenReturn(Optional.of(connection));
        when(portfolioBrokerHoldingImportRepository
                .findAllBySnapshotItem_Snapshot_BrokerConnection_Id(5L)).thenReturn(List.of());

        brokerConnectionService.deleteBrokerConnection(1L, 5L);

        // 스냅샷은 함께 삭제되는 계좌 행을 참조하므로 연결보다 먼저 지워야 한다.
        InOrder deletionOrder = inOrder(
                portfolioBrokerLinkRepository,
                portfolioBrokerHoldingSnapshotRepository,
                brokerConnectionRepository
        );
        deletionOrder.verify(portfolioBrokerLinkRepository).deleteAllByBrokerConnection_Id(5L);
        deletionOrder.verify(portfolioBrokerHoldingSnapshotRepository).deleteAllByBrokerConnection_Id(5L);
        deletionOrder.verify(brokerConnectionRepository).delete(connection);
    }

    @Test
    void blocksDeleteWhenActiveOpeningBalanceImportStillExists() {
        BrokerConnection connection = connection();
        List<PortfolioBrokerHoldingImport> active =
                List.of(importRecord(PortfolioBrokerHoldingImportStatus.ACTIVE));
        when(brokerConnectionRepository.findByMember_IdAndId(1L, 5L)).thenReturn(Optional.of(connection));
        when(portfolioBrokerHoldingImportRepository
                .findAllBySnapshotItem_Snapshot_BrokerConnection_Id(5L)).thenReturn(active);

        assertThatThrownBy(() -> brokerConnectionService.deleteBrokerConnection(1L, 5L))
                .isInstanceOf(BrokerConnectionDeletionBlockedException.class)
                .hasMessageContaining("개시 잔고 매매 기록이 1건 남아 있어 연결을 삭제할 수 없습니다.")
                .hasMessageContaining("먼저 취소한 뒤");

        // 원장과 파생 데이터 중 어느 것도 손대지 않는다.
        verify(portfolioBrokerHoldingSnapshotRepository, never()).deleteAllByBrokerConnection_Id(any());
        verify(portfolioBrokerLinkRepository, never()).deleteAllByBrokerConnection_Id(any());
        verify(brokerConnectionRepository, never()).delete(any(BrokerConnection.class));
    }

    @Test
    void keepsRevokedImportHistoryByDetachingSnapshotItemOnly() {
        BrokerConnection connection = connection();
        PortfolioBrokerHoldingImport revoked = importRecord(PortfolioBrokerHoldingImportStatus.REVOKED);
        when(brokerConnectionRepository.findByMember_IdAndId(1L, 5L)).thenReturn(Optional.of(connection));
        when(portfolioBrokerHoldingImportRepository
                .findAllBySnapshotItem_Snapshot_BrokerConnection_Id(5L)).thenReturn(List.of(revoked));

        brokerConnectionService.deleteBrokerConnection(1L, 5L);

        // 감사 이력 행은 지우지 않고 스냅샷 참조만 끊는다.
        verify(revoked).detachSnapshotItem();
        verify(portfolioBrokerHoldingImportRepository, never()).delete(any());
        verify(portfolioBrokerHoldingImportRepository, never()).deleteAll(any());
        verify(portfolioBrokerHoldingSnapshotRepository).deleteAllByBrokerConnection_Id(5L);
        verify(brokerConnectionRepository).delete(connection);
    }

    private BrokerConnection connection() {
        return new BrokerConnection(
                new Member("broker@example.com", "broker-user"),
                BrokerProvider.TOSS_SECURITIES,
                "개인 토스증권"
        );
    }

    @Test
    void blocksDeleteWhenActiveHoldingAdjustmentStillExists() {
        BrokerConnection connection = connection();
        List<PortfolioBrokerHoldingAdjustment> active =
                List.of(adjustmentRecord(PortfolioBrokerHoldingAdjustmentStatus.ACTIVE));
        when(brokerConnectionRepository.findByMember_IdAndId(1L, 5L)).thenReturn(Optional.of(connection));
        when(portfolioBrokerHoldingAdjustmentRepository
                .findAllBySnapshotItem_Snapshot_BrokerConnection_Id(5L)).thenReturn(active);

        assertThatThrownBy(() -> brokerConnectionService.deleteBrokerConnection(1L, 5L))
                .isInstanceOf(BrokerConnectionDeletionBlockedException.class)
                .hasMessageContaining("잔고 조정 매매 기록이 1건 남아 있어 연결을 삭제할 수 없습니다.")
                .hasMessageContaining("먼저 취소한 뒤");

        verify(portfolioBrokerHoldingSnapshotRepository, never()).deleteAllByBrokerConnection_Id(any());
        verify(portfolioBrokerLinkRepository, never()).deleteAllByBrokerConnection_Id(any());
        verify(brokerConnectionRepository, never()).delete(any(BrokerConnection.class));
    }

    @Test
    void keepsRevokedHoldingAdjustmentHistoryByDetachingSnapshotItemOnly() {
        BrokerConnection connection = connection();
        PortfolioBrokerHoldingAdjustment revoked =
                adjustmentRecord(PortfolioBrokerHoldingAdjustmentStatus.REVOKED);
        when(brokerConnectionRepository.findByMember_IdAndId(1L, 5L)).thenReturn(Optional.of(connection));
        when(portfolioBrokerHoldingAdjustmentRepository
                .findAllBySnapshotItem_Snapshot_BrokerConnection_Id(5L)).thenReturn(List.of(revoked));

        brokerConnectionService.deleteBrokerConnection(1L, 5L);

        verify(revoked).detachSnapshotItem();
        verify(portfolioBrokerHoldingAdjustmentRepository, never()).delete(any());
        verify(portfolioBrokerHoldingAdjustmentRepository, never()).deleteAll(any());
        verify(portfolioBrokerHoldingSnapshotRepository).deleteAllByBrokerConnection_Id(5L);
        verify(brokerConnectionRepository).delete(connection);
    }

    private PortfolioBrokerHoldingImport importRecord(PortfolioBrokerHoldingImportStatus status) {
        PortfolioBrokerHoldingImport importRecord = mock(PortfolioBrokerHoldingImport.class);
        when(importRecord.getStatus()).thenReturn(status);
        return importRecord;
    }

    private PortfolioBrokerHoldingAdjustment adjustmentRecord(PortfolioBrokerHoldingAdjustmentStatus status) {
        PortfolioBrokerHoldingAdjustment adjustment = mock(PortfolioBrokerHoldingAdjustment.class);
        when(adjustment.getStatus()).thenReturn(status);
        return adjustment;
    }

    @Test
    void rejectsUnsupportedBrokerProviderBeforeEncryptingCredentials() {
        assertThatThrownBy(() -> brokerConnectionService.createBrokerConnection(
                1L,
                null,
                "연결",
                Map.of("clientId", "test-client-id", "clientSecret", "test-client-secret")
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("지원하지 않는 증권사 제공자입니다.");

        verify(brokerCredentialCipher, never()).encrypt(any());
    }

    @Test
    void verifyBrokerConnectionDispatchesToRegisteredProviderVerifier() {
        BrokerConnection connection = new BrokerConnection(
                new Member("broker@example.com", "broker-user"),
                BrokerProvider.TOSS_SECURITIES,
                "개인 토스증권"
        );
        connection.replaceSecretValues(List.of(
                new BrokerConnectionSecretValue("clientId", "encrypted-id", "id-iv", 1),
                new BrokerConnectionSecretValue("clientSecret", "encrypted-secret", "secret-iv", 1)
        ));
        when(brokerConnectionRepository.findByMember_IdAndId(1L, 5L)).thenReturn(Optional.of(connection));
        when(brokerCredentialCipher.decrypt(any())).thenReturn("decrypted-value");
        when(brokerCredentialCipher.encrypt("candidate-sequence"))
                .thenReturn(new EncryptedBrokerCredential("encrypted-sequence", "sequence-iv", 1));

        BrokerConnection verified = brokerConnectionService.verifyBrokerConnection(1L, 5L);

        assertThat(verified.getAccounts()).hasSize(1);
        assertThat(verified.getAccounts().getFirst().getMaskedAccountNumber()).isEqualTo("****1234");
        assertThat(verified.getMaskedAccountLabel()).isEqualTo("****1234");
        // 계좌 행을 다시 만들지 않으므로 링크를 일괄로 지울 이유가 없다.
        verify(portfolioBrokerLinkRepository, never()).deleteAllByBrokerConnection_Id(any());
        verify(portfolioBrokerLinkRepository, never()).deleteAllByBrokerAccount_IdIn(any());
    }

    @Test
    void reusesExistingAccountRowWhenProviderReturnsTheSameAccountAgain() {
        BrokerConnection connection = verifiableConnection();
        BrokerAccount existing = accountWithId(41L, "candidate-sequence", "*****0000", "위탁");
        connection.reconcileVerifiedAccounts(List.of(existing));
        when(brokerConnectionRepository.findByMember_IdAndId(1L, 5L)).thenReturn(Optional.of(connection));
        when(brokerCredentialCipher.decrypt(any())).thenReturn("candidate-sequence");
        when(brokerCredentialCipher.encrypt("candidate-sequence"))
                .thenReturn(new EncryptedBrokerCredential("re-encrypted-sequence", "new-iv", 1));
        connectionVerifier.respondWith(
                new BrokerConnectionCandidateAccount("candidate-sequence", "****1234", "종합"));

        BrokerConnection verified = brokerConnectionService.verifyBrokerConnection(1L, 5L);

        // 같은 계좌는 새 행이 아니라 같은 행을 갱신해야 과거 스냅샷 참조가 살아남는다.
        assertThat(verified.getAccounts()).containsExactly(existing);
        assertThat(existing.getStatus()).isEqualTo(BrokerAccountStatus.ACTIVE);
        assertThat(existing.getMaskedAccountNumber()).isEqualTo("****1234");
        assertThat(existing.getAccountType()).isEqualTo("종합");
        assertThat(existing.getEncryptedAccountSequence()).isEqualTo("re-encrypted-sequence");
        verify(portfolioBrokerLinkRepository, never()).deleteAllByBrokerAccount_IdIn(any());
    }

    @Test
    void detachesAccountsMissingFromProviderInsteadOfDeletingTheirRows() {
        BrokerConnection connection = verifiableConnection();
        BrokerAccount kept = accountWithId(41L, "kept-sequence", "*****1111", "위탁");
        BrokerAccount missing = accountWithId(42L, "missing-sequence", "*****2222", "위탁");
        connection.reconcileVerifiedAccounts(List.of(kept, missing));
        when(brokerConnectionRepository.findByMember_IdAndId(1L, 5L)).thenReturn(Optional.of(connection));
        when(brokerCredentialCipher.decrypt(any())).thenAnswer(invocation -> {
            EncryptedBrokerCredential credential = invocation.getArgument(0);
            return credential.ciphertext().replace("encrypted-", "");
        });
        when(brokerCredentialCipher.encrypt("kept-sequence"))
                .thenReturn(new EncryptedBrokerCredential("encrypted-kept-sequence", "iv", 1));
        connectionVerifier.respondWith(
                new BrokerConnectionCandidateAccount("kept-sequence", "*****1111", "위탁"));

        BrokerConnection verified = brokerConnectionService.verifyBrokerConnection(1L, 5L);

        // 사라진 계좌 행은 남기고 상태만 바꾼다. 과거 스냅샷이 이 행을 참조하기 때문이다.
        assertThat(verified.getAccounts()).containsExactly(kept, missing);
        assertThat(verified.getActiveAccounts()).containsExactly(kept);
        assertThat(missing.getStatus()).isEqualTo(BrokerAccountStatus.DETACHED);
        assertThat(missing.getDetachedAt()).isNotNull();
        // 조회할 수 없게 된 계좌의 링크만 끊고 나머지 링크는 건드리지 않는다.
        verify(portfolioBrokerLinkRepository).deleteAllByBrokerAccount_IdIn(List.of(42L));
        verify(portfolioBrokerLinkRepository, never()).deleteAllByBrokerConnection_Id(any());
    }

    private BrokerConnection verifiableConnection() {
        BrokerConnection connection = connection();
        connection.replaceSecretValues(List.of(
                new BrokerConnectionSecretValue("clientId", "encrypted-id", "id-iv", 1),
                new BrokerConnectionSecretValue("clientSecret", "encrypted-secret", "secret-iv", 1)
        ));
        return connection;
    }

    private BrokerAccount accountWithId(Long id, String sequence, String maskedAccountNumber, String accountType) {
        BrokerAccount account = new BrokerAccount(
                "encrypted-" + sequence, "sequence-iv", maskedAccountNumber, accountType, 1
        );
        ReflectionTestUtils.setField(account, "id", id);
        return account;
    }

    @Test
    void rejectsVerifyWhenNoConnectionVerifierIsRegisteredForProvider() {
        BrokerConnectionService serviceWithEmptyRegistry = new BrokerConnectionService(
                memberRepository,
                brokerConnectionRepository,
                portfolioBrokerLinkRepository,
                portfolioBrokerHoldingSnapshotRepository,
                portfolioBrokerHoldingImportRepository,
                portfolioBrokerHoldingAdjustmentRepository,
                brokerCredentialCipher,
                new BrokerCredentialLoader(brokerCredentialCipher),
                new BrokerProviderRegistry(List.of(), List.of(), List.of(), List.of())
        );
        BrokerConnection connection = new BrokerConnection(
                new Member("broker@example.com", "broker-user"),
                BrokerProvider.TOSS_SECURITIES,
                "개인 토스증권"
        );
        when(brokerConnectionRepository.findByMember_IdAndId(1L, 5L)).thenReturn(Optional.of(connection));

        assertThatThrownBy(() -> serviceWithEmptyRegistry.verifyBrokerConnection(1L, 5L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("지원하지 않는 증권사 제공자입니다.");
    }

    /** 실제 자격 증명 검증 없이 지정한 계좌 후보를 반환하는 테스트 전용 가짜 구현체다. */
    private static final class FakeTossConnectionVerifier implements BrokerConnectionVerifier {

        private List<BrokerConnectionCandidateAccount> candidates = new ArrayList<>(List.of(
                new BrokerConnectionCandidateAccount("candidate-sequence", "****1234", "일반")
        ));

        void respondWith(BrokerConnectionCandidateAccount... accounts) {
            this.candidates = List.of(accounts);
        }

        @Override
        public BrokerProvider getProvider() {
            return BrokerProvider.TOSS_SECURITIES;
        }

        @Override
        public List<BrokerConnectionCandidateAccount> verify(BrokerCredentials credentials) {
            return candidates;
        }
    }

}
