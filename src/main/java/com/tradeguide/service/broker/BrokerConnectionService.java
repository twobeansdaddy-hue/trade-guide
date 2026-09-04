package com.tradeguide.service.broker;

import com.tradeguide.domain.broker.BrokerConnection;
import com.tradeguide.domain.broker.BrokerConnectionSecret;
import com.tradeguide.domain.broker.BrokerProvider;
import com.tradeguide.domain.member.Member;
import com.tradeguide.repository.broker.BrokerConnectionRepository;
import com.tradeguide.repository.member.MemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class BrokerConnectionService {

    private final MemberRepository memberRepository;
    private final BrokerConnectionRepository brokerConnectionRepository;
    private final BrokerCredentialCipher brokerCredentialCipher;
    private final TossSecuritiesConnectionVerifier tossSecuritiesConnectionVerifier;

    public BrokerConnectionService(
            MemberRepository memberRepository,
            BrokerConnectionRepository brokerConnectionRepository,
            BrokerCredentialCipher brokerCredentialCipher,
            TossSecuritiesConnectionVerifier tossSecuritiesConnectionVerifier
    ) {
        this.memberRepository = memberRepository;
        this.brokerConnectionRepository = brokerConnectionRepository;
        this.brokerCredentialCipher = brokerCredentialCipher;
        this.tossSecuritiesConnectionVerifier = tossSecuritiesConnectionVerifier;
    }

    public List<BrokerConnection> getBrokerConnections(Long memberId) {
        requireMember(memberId);
        return brokerConnectionRepository.findAllByMember_IdOrderByCreatedAtDesc(memberId);
    }

    public BrokerConnection createBrokerConnection(
            Long memberId,
            BrokerProvider provider,
            String displayName,
            String clientId,
            String clientSecret
    ) {
        if (provider != BrokerProvider.TOSS_SECURITIES) {
            throw new IllegalArgumentException("지원하지 않는 증권사 제공자입니다.");
        }

        Member member = requireMember(memberId);
        EncryptedBrokerCredential encryptedClientId = brokerCredentialCipher.encrypt(clientId);
        EncryptedBrokerCredential encryptedClientSecret = brokerCredentialCipher.encrypt(clientSecret);

        if (encryptedClientId.keyVersion() != encryptedClientSecret.keyVersion()) {
            throw new IllegalStateException("증권사 자격 증명 암호화 키 버전이 일치하지 않습니다.");
        }

        BrokerConnection connection = new BrokerConnection(member, provider, displayName);
        connection.attachSecret(new BrokerConnectionSecret(
                encryptedClientId.ciphertext(),
                encryptedClientId.initializationVector(),
                encryptedClientSecret.ciphertext(),
                encryptedClientSecret.initializationVector(),
                encryptedClientId.keyVersion()
        ));

        return brokerConnectionRepository.save(connection);
    }

    public void deleteBrokerConnection(Long memberId, Long connectionId) {
        BrokerConnection connection = brokerConnectionRepository
                .findByMember_IdAndId(memberId, connectionId)
                .orElseThrow(() -> new IllegalArgumentException("증권사 연결 정보를 찾을 수 없습니다."));

        brokerConnectionRepository.delete(connection);
    }

    @Transactional
    public BrokerConnection verifyBrokerConnection(Long memberId, Long connectionId) {
        BrokerConnection connection = brokerConnectionRepository.findByMember_IdAndId(memberId, connectionId)
                .orElseThrow(() -> new IllegalArgumentException("증권사 연결 정보를 찾을 수 없습니다."));
        String clientId = brokerCredentialCipher.decrypt(new EncryptedBrokerCredential(
                connection.getSecret().getEncryptedClientId(), connection.getSecret().getClientIdInitializationVector(),
                connection.getSecret().getEncryptionKeyVersion()));
        String clientSecret = brokerCredentialCipher.decrypt(new EncryptedBrokerCredential(
                connection.getSecret().getEncryptedClientSecret(), connection.getSecret().getClientSecretInitializationVector(),
                connection.getSecret().getEncryptionKeyVersion()));
        List<String> accounts = tossSecuritiesConnectionVerifier.verify(clientId, clientSecret);
        connection.markConnected(accounts.isEmpty() ? null : String.join(", ", accounts));
        return connection;
    }

    private Member requireMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("회원을 찾을 수 없습니다."));
    }
}
