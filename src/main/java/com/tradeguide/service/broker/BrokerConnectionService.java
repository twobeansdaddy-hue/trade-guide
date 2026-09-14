package com.tradeguide.service.broker;

import com.tradeguide.domain.broker.BrokerConnection;
import com.tradeguide.domain.broker.BrokerAccount;
import com.tradeguide.domain.broker.BrokerConnectionCandidateAccount;
import com.tradeguide.domain.broker.BrokerConnectionSecretValue;
import com.tradeguide.domain.broker.BrokerCredentialField;
import com.tradeguide.domain.broker.BrokerCredentials;
import com.tradeguide.domain.broker.BrokerProvider;
import com.tradeguide.domain.broker.PortfolioBrokerHoldingAdjustment;
import com.tradeguide.domain.broker.PortfolioBrokerHoldingAdjustmentStatus;
import com.tradeguide.domain.broker.PortfolioBrokerHoldingImport;
import com.tradeguide.domain.broker.PortfolioBrokerHoldingImportStatus;
import com.tradeguide.domain.member.Member;
import com.tradeguide.exception.BrokerConnectionDeletionBlockedException;
import com.tradeguide.repository.broker.BrokerConnectionRepository;
import com.tradeguide.repository.broker.PortfolioBrokerHoldingAdjustmentRepository;
import com.tradeguide.repository.broker.PortfolioBrokerHoldingImportRepository;
import com.tradeguide.repository.broker.PortfolioBrokerHoldingSnapshotRepository;
import com.tradeguide.repository.broker.PortfolioBrokerLinkRepository;
import com.tradeguide.repository.member.MemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class BrokerConnectionService {

    /**
     * 자격 증명 한 항목의 평문 길이 상한이다. 암호화 후에도 {@code VARCHAR(4096)}에 여유 있게
     * 들어가는 값이며, 상한이 없으면 요청 본문 하나로 저장소를 채울 수 있다.
     */
    static final int MAX_CREDENTIAL_VALUE_LENGTH = 512;

    private final MemberRepository memberRepository;
    private final BrokerConnectionRepository brokerConnectionRepository;
    private final PortfolioBrokerLinkRepository portfolioBrokerLinkRepository;
    private final PortfolioBrokerHoldingSnapshotRepository portfolioBrokerHoldingSnapshotRepository;
    private final PortfolioBrokerHoldingImportRepository portfolioBrokerHoldingImportRepository;
    private final PortfolioBrokerHoldingAdjustmentRepository portfolioBrokerHoldingAdjustmentRepository;
    private final BrokerCredentialCipher brokerCredentialCipher;
    private final BrokerCredentialLoader brokerCredentialLoader;
    private final BrokerProviderRegistry brokerProviderRegistry;

    public BrokerConnectionService(
            MemberRepository memberRepository,
            BrokerConnectionRepository brokerConnectionRepository,
            PortfolioBrokerLinkRepository portfolioBrokerLinkRepository,
            PortfolioBrokerHoldingSnapshotRepository portfolioBrokerHoldingSnapshotRepository,
            PortfolioBrokerHoldingImportRepository portfolioBrokerHoldingImportRepository,
            PortfolioBrokerHoldingAdjustmentRepository portfolioBrokerHoldingAdjustmentRepository,
            BrokerCredentialCipher brokerCredentialCipher,
            BrokerCredentialLoader brokerCredentialLoader,
            BrokerProviderRegistry brokerProviderRegistry
    ) {
        this.memberRepository = memberRepository;
        this.brokerConnectionRepository = brokerConnectionRepository;
        this.portfolioBrokerLinkRepository = portfolioBrokerLinkRepository;
        this.portfolioBrokerHoldingSnapshotRepository = portfolioBrokerHoldingSnapshotRepository;
        this.portfolioBrokerHoldingImportRepository = portfolioBrokerHoldingImportRepository;
        this.portfolioBrokerHoldingAdjustmentRepository = portfolioBrokerHoldingAdjustmentRepository;
        this.brokerCredentialCipher = brokerCredentialCipher;
        this.brokerCredentialLoader = brokerCredentialLoader;
        this.brokerProviderRegistry = brokerProviderRegistry;
    }

    public List<BrokerConnection> getBrokerConnections(Long memberId) {
        requireMember(memberId);
        return brokerConnectionRepository.findAllByMember_IdOrderByCreatedAtDesc(memberId);
    }

    /**
     * 증권사 연결을 만들고 자격 증명을 항목별로 암호화해 저장한다.
     *
     * <p>{@code credentialValues}의 키는 제공자 명세
     * ({@link BrokerProvider#getCredentialFields()})의 키여야 한다. 명세에 없는 키를 조용히
     * 버리지 않고 거부하는 이유는, 무시하면 오타 난 키가 통과해 연결이 만들어지고
     * 검증 단계에 가서야 이유를 알 수 없는 실패로 나타나기 때문이다.
     *
     * <p>검증은 암호화보다 먼저 전부 끝낸다. 항목을 하나씩 검사하며 저장하면 일부만 저장된
     * 연결이 남는다.
     */
    public BrokerConnection createBrokerConnection(
            Long memberId,
            BrokerProvider provider,
            String displayName,
            Map<String, String> credentialValues
    ) {
        brokerProviderRegistry.requireConnectionVerifier(provider);
        Map<String, String> validated = validateCredentialValues(provider, credentialValues);

        Member member = requireMember(memberId);
        List<BrokerConnectionSecretValue> secretValues = new ArrayList<>();
        validated.forEach((fieldKey, value) -> {
            EncryptedBrokerCredential encrypted = brokerCredentialCipher.encrypt(value);
            secretValues.add(new BrokerConnectionSecretValue(
                    fieldKey,
                    encrypted.ciphertext(),
                    encrypted.initializationVector(),
                    encrypted.keyVersion()
            ));
        });

        BrokerConnection connection = new BrokerConnection(member, provider, displayName);
        connection.replaceSecretValues(secretValues);

        return brokerConnectionRepository.save(connection);
    }

    /**
     * 입력 자격 증명을 제공자 명세와 대조한다.
     *
     * <p>어떤 오류 메시지에도 입력값을 담지 않는다. 항목 키 이름까지만 노출한다.
     * 자격 증명이 새는 가장 흔한 경로가 오류 응답과 그 응답을 그대로 남기는 로그다.
     *
     * @return 명세 순서로 정렬된 저장 대상 값. 명세에 없는 선택 항목은 값이 비어 있으면 제외한다.
     */
    private Map<String, String> validateCredentialValues(
            BrokerProvider provider,
            Map<String, String> credentialValues
    ) {
        if (credentialValues == null || credentialValues.isEmpty()) {
            throw new IllegalArgumentException("자격 증명은 필수입니다.");
        }

        Set<String> declaredKeys = provider.getCredentialFields().stream()
                .map(BrokerCredentialField::key)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        credentialValues.keySet().stream()
                .filter(key -> !declaredKeys.contains(key))
                .findFirst()
                .ifPresent(key -> {
                    throw new IllegalArgumentException("지원하지 않는 자격 증명 항목입니다: " + key);
                });

        Map<String, String> validated = new LinkedHashMap<>();
        for (BrokerCredentialField field : provider.getCredentialFields()) {
            String value = credentialValues.get(field.key());
            boolean blank = value == null || value.isBlank();

            if (blank) {
                if (field.required()) {
                    throw new IllegalArgumentException("자격 증명 항목이 비어 있습니다: " + field.key());
                }
                continue;
            }
            if (value.length() > MAX_CREDENTIAL_VALUE_LENGTH) {
                throw new IllegalArgumentException(
                        "자격 증명 항목이 " + MAX_CREDENTIAL_VALUE_LENGTH + "자를 넘습니다: " + field.key());
            }

            validated.put(field.key(), value);
        }

        return validated;
    }

    /**
     * 증권사 연결과 그 연결에서 파생된 데이터만 삭제한다.
     *
     * <p>삭제 범위는 이 연결의 포트폴리오 링크, 계좌, 자격 증명, 그리고 이 연결로
     * 조회한 읽기 전용 보유 종목 스냅샷과 항목이다. {@code TradeTransaction}은
     * 출처가 수기든 개시 잔고든 이 경로에서 절대 삭제하지 않는다.
     *
     * <p>아직 유효한({@code ACTIVE}) 개시 잔고 승인 이력이 남아 있으면, 그 승인으로
     * 만들어진 원장 행을 되돌릴 수 있는 유일한 경로가 전용 취소 API이므로 삭제를
     * 차단하고 도메인 오류를 던진다. 이미 취소된 이력은 스냅샷 참조만 끊어 감사
     * 이력으로 보존한다.
     */
    @Transactional
    public void deleteBrokerConnection(Long memberId, Long connectionId) {
        BrokerConnection connection = brokerConnectionRepository
                .findByMember_IdAndId(memberId, connectionId)
                .orElseThrow(() -> new IllegalArgumentException("증권사 연결 정보를 찾을 수 없습니다."));

        List<PortfolioBrokerHoldingImport> imports = portfolioBrokerHoldingImportRepository
                .findAllBySnapshotItem_Snapshot_BrokerConnection_Id(connectionId);
        List<PortfolioBrokerHoldingAdjustment> adjustments = portfolioBrokerHoldingAdjustmentRepository
                .findAllBySnapshotItem_Snapshot_BrokerConnection_Id(connectionId);

        long activeImportCount = imports.stream()
                .filter(importRecord -> importRecord.getStatus() == PortfolioBrokerHoldingImportStatus.ACTIVE)
                .count();
        if (activeImportCount > 0) {
            throw new BrokerConnectionDeletionBlockedException(
                    "이 증권사 연결로 반영한 개시 잔고 매매 기록이 " + activeImportCount + "건 남아 있어 연결을 삭제할 수 없습니다. "
                            + "포트폴리오의 개시 잔고 이력에서 해당 승인을 먼저 취소한 뒤 다시 삭제해 주세요."
            );
        }

        long activeAdjustmentCount = adjustments.stream()
                .filter(adjustment -> adjustment.getStatus() == PortfolioBrokerHoldingAdjustmentStatus.ACTIVE)
                .count();
        if (activeAdjustmentCount > 0) {
            throw new BrokerConnectionDeletionBlockedException(
                    "이 증권사 연결로 반영한 잔고 조정 매매 기록이 " + activeAdjustmentCount + "건 남아 있어 연결을 삭제할 수 없습니다. "
                            + "포트폴리오의 잔고 조정 이력에서 해당 승인을 먼저 취소한 뒤 다시 삭제해 주세요."
            );
        }

        // 취소된 승인 이력은 감사 목적으로 남긴다. 승인 당시 값은 이력 행에 이미
        // 복사되어 있으므로, 사라질 스냅샷 항목 참조만 끊는다.
        imports.forEach(PortfolioBrokerHoldingImport::detachSnapshotItem);
        portfolioBrokerHoldingImportRepository.flush();

        adjustments.forEach(PortfolioBrokerHoldingAdjustment::detachSnapshotItem);
        portfolioBrokerHoldingAdjustmentRepository.flush();

        // 연결을 끊으면 이 연결을 사용하던 포트폴리오 링크도 함께 사라진다.
        portfolioBrokerLinkRepository.deleteAllByBrokerConnection_Id(connectionId);

        // 스냅샷은 이 연결로만 만들어지는 읽기 전용 파생 데이터다. 계좌 행이 함께
        // 삭제되므로 연결보다 먼저 지운다.
        portfolioBrokerHoldingSnapshotRepository.deleteAllByBrokerConnection_Id(connectionId);
        portfolioBrokerHoldingSnapshotRepository.flush();

        brokerConnectionRepository.delete(connection);
    }

    /**
     * 증권사 자격 증명을 다시 검증하고 계좌 목록을 최신 상태로 맞춘다.
     *
     * <p>재검증은 계좌 행을 지우지 않는다. 계좌 식별자는 과거 보유 종목 스냅샷과 개시 잔고
     * 승인 이력이 참조하므로, 행을 지우면 외래키 제약에 걸려 재검증 자체가 실패한다.
     * 증권사 계좌 일련번호로 기존 행을 다시 찾아 갱신하고, 이번 응답에 없는 계좌는
     * {@code DETACHED}로 표시해 후보에서만 제외한다. 분리된 계좌를 가리키던 포트폴리오
     * 링크만 정리하고, 여전히 유효한 계좌의 링크는 그대로 둔다.
     */
    @Transactional
    public BrokerConnection verifyBrokerConnection(Long memberId, Long connectionId) {
        BrokerConnection connection = brokerConnectionRepository.findByMember_IdAndId(memberId, connectionId)
                .orElseThrow(() -> new IllegalArgumentException("증권사 연결 정보를 찾을 수 없습니다."));
        BrokerConnectionVerifier connectionVerifier = brokerProviderRegistry.requireConnectionVerifier(connection.getProvider());
        BrokerCredentials credentials = brokerCredentialLoader.load(connection);
        List<BrokerConnectionCandidateAccount> accounts = connectionVerifier.verify(credentials);

        Map<String, BrokerAccount> existingAccountsBySequence = indexBySequence(connection.getAccounts());
        List<BrokerAccount> providerAccounts = accounts.stream()
                .map(candidate -> resolveAccount(existingAccountsBySequence, candidate))
                .toList();

        connection.reconcileVerifiedAccounts(providerAccounts);
        connection.markConnected(accounts.isEmpty() ? null : accounts.getFirst().maskedAccountNumber());

        // 증권사가 더 이상 반환하지 않는 계좌는 조회할 수 없으므로 그 계좌를 가리키던 링크만 끊는다.
        // 계좌 행 자체는 남기 때문에 이 계좌를 참조하는 과거 스냅샷은 그대로 조회할 수 있다.
        List<Long> detachedAccountIds = connection.getAccounts().stream()
                .filter(account -> !account.isActive())
                .map(BrokerAccount::getId)
                .filter(Objects::nonNull)
                .toList();
        if (!detachedAccountIds.isEmpty()) {
            portfolioBrokerLinkRepository.deleteAllByBrokerAccount_IdIn(detachedAccountIds);
        }

        return connection;
    }

    /**
     * 증권사 계좌 일련번호를 키로 기존 계좌 행을 찾는다. 암호문은 검증할 때마다 초기화 벡터가
     * 달라져 그대로 비교할 수 없으므로 복호화한 값으로 대조한다. 복호화된 일련번호는 이 대조
     * 구간에서만 사용하고 저장하거나 응답, 로그에 남기지 않는다.
     *
     * <p>키 교체 등으로 복호화할 수 없는 행은 대조 대상에서 제외한다. 그 행은 분리된 계좌로
     * 남고 증권사가 반환한 계좌는 새 행으로 추가되므로, 재검증이 실패하는 대신 이력이 보존된다.
     */
    private Map<String, BrokerAccount> indexBySequence(List<BrokerAccount> existingAccounts) {
        Map<String, BrokerAccount> accountsBySequence = new HashMap<>();
        existingAccounts.forEach(account -> {
            try {
                accountsBySequence.putIfAbsent(brokerCredentialLoader.loadAccountSequence(account), account);
            } catch (RuntimeException exception) {
                // 복호화 실패는 이 계좌를 새 행으로 만들 뿐 재검증을 막지 않는다.
            }
        });
        return accountsBySequence;
    }

    private BrokerAccount resolveAccount(
            Map<String, BrokerAccount> existingAccountsBySequence,
            BrokerConnectionCandidateAccount candidate
    ) {
        EncryptedBrokerCredential sequence = brokerCredentialCipher.encrypt(candidate.accountSequence());
        BrokerAccount existing = existingAccountsBySequence.get(candidate.accountSequence());
        if (existing == null) {
            return new BrokerAccount(sequence.ciphertext(), sequence.initializationVector(),
                    candidate.maskedAccountNumber(), candidate.accountType(), sequence.keyVersion());
        }

        existing.refreshFromProvider(sequence.ciphertext(), sequence.initializationVector(),
                candidate.maskedAccountNumber(), candidate.accountType(), sequence.keyVersion());
        return existing;
    }

    private Member requireMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("회원을 찾을 수 없습니다."));
    }
}
