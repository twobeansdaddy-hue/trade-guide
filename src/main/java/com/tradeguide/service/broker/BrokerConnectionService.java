package com.tradeguide.service.broker;

import com.tradeguide.domain.broker.BrokerConnection;
import com.tradeguide.domain.broker.BrokerAccount;
import com.tradeguide.domain.broker.BrokerConnectionSecret;
import com.tradeguide.domain.broker.BrokerProvider;
import com.tradeguide.domain.member.Member;
import com.tradeguide.repository.broker.BrokerConnectionRepository;
import com.tradeguide.repository.broker.PortfolioBrokerLinkRepository;
import com.tradeguide.repository.member.MemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class BrokerConnectionService {

    private final MemberRepository memberRepository;
    private final BrokerConnectionRepository brokerConnectionRepository;
    private final PortfolioBrokerLinkRepository portfolioBrokerLinkRepository;
    private final BrokerCredentialCipher brokerCredentialCipher;
    private final TossSecuritiesConnectionVerifier tossSecuritiesConnectionVerifier;

    public BrokerConnectionService(
            MemberRepository memberRepository,
            BrokerConnectionRepository brokerConnectionRepository,
            PortfolioBrokerLinkRepository portfolioBrokerLinkRepository,
            BrokerCredentialCipher brokerCredentialCipher,
            TossSecuritiesConnectionVerifier tossSecuritiesConnectionVerifier
    ) {
        this.memberRepository = memberRepository;
        this.brokerConnectionRepository = brokerConnectionRepository;
        this.portfolioBrokerLinkRepository = portfolioBrokerLinkRepository;
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

    @Transactional
    public void deleteBrokerConnection(Long memberId, Long connectionId) {
        BrokerConnection connection = brokerConnectionRepository
                .findByMember_IdAndId(memberId, connectionId)
                .orElseThrow(() -> new IllegalArgumentException("증권사 연결 정보를 찾을 수 없습니다."));

        // 연결을 끊으면 이 연결을 사용하던 포트폴리오 링크도 함께 사라진다.
        portfolioBrokerLinkRepository.deleteAllByBrokerConnection_Id(connectionId);
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
        List<TossSecuritiesConnectionVerifier.TossAccount> accounts = tossSecuritiesConnectionVerifier.verify(clientId, clientSecret);

        // 재검증은 계좌 행을 새로 만들기 때문에 기존 포트폴리오 링크는 유효하지 않다.
        // 사용자가 계좌를 다시 선택하도록 링크를 제거한다.
        portfolioBrokerLinkRepository.deleteAllByBrokerConnection_Id(connectionId);

        connection.replaceAccounts(accounts.stream().map(account -> {
            EncryptedBrokerCredential sequence = brokerCredentialCipher.encrypt(String.valueOf(account.accountSequence()));
            return new BrokerAccount(sequence.ciphertext(), sequence.initializationVector(),
                    account.maskedAccountNumber(), account.accountType(), sequence.keyVersion());
        }).toList());
        connection.markConnected(accounts.isEmpty() ? null : accounts.getFirst().maskedAccountNumber());
        return connection;
    }

    private Member requireMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("회원을 찾을 수 없습니다."));
    }
}
