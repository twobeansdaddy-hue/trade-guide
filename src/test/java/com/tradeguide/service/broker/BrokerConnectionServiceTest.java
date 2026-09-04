package com.tradeguide.service.broker;

import com.tradeguide.domain.broker.BrokerConnection;
import com.tradeguide.domain.broker.BrokerProvider;
import com.tradeguide.domain.member.Member;
import com.tradeguide.exception.BrokerConnectionUnavailableException;
import com.tradeguide.repository.broker.BrokerConnectionRepository;
import com.tradeguide.repository.broker.PortfolioBrokerLinkRepository;
import com.tradeguide.repository.member.MemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
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
    private BrokerCredentialCipher brokerCredentialCipher;

    @InjectMocks
    private BrokerConnectionService brokerConnectionService;

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
                "test-client-id",
                "test-client-secret"
        );

        ArgumentCaptor<BrokerConnection> connectionCaptor =
                ArgumentCaptor.forClass(BrokerConnection.class);
        verify(brokerConnectionRepository).save(connectionCaptor.capture());
        assertThat(connection).isSameAs(connectionCaptor.getValue());
        assertThat(connection.getProvider()).isEqualTo(BrokerProvider.TOSS_SECURITIES);
        assertThat(connection.getDisplayName()).isEqualTo("개인 토스증권");
        verify(brokerCredentialCipher).encrypt("test-client-id");
        verify(brokerCredentialCipher).encrypt("test-client-secret");
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
                "test-client-id",
                "test-client-secret"
        ))
                .isInstanceOf(BrokerConnectionUnavailableException.class);

        verify(brokerConnectionRepository, never()).save(any(BrokerConnection.class));
    }

    @Test
    void removesPortfolioLinksWhenBrokerConnectionIsDeleted() {
        BrokerConnection connection = new BrokerConnection(
                new Member("broker@example.com", "broker-user"),
                BrokerProvider.TOSS_SECURITIES,
                "개인 토스증권"
        );
        when(brokerConnectionRepository.findByMember_IdAndId(1L, 5L)).thenReturn(Optional.of(connection));

        brokerConnectionService.deleteBrokerConnection(1L, 5L);

        verify(portfolioBrokerLinkRepository).deleteAllByBrokerConnection_Id(5L);
        verify(brokerConnectionRepository).delete(connection);
    }

    @Test
    void rejectsUnsupportedBrokerProviderBeforeEncryptingCredentials() {
        assertThatThrownBy(() -> brokerConnectionService.createBrokerConnection(
                1L,
                null,
                "연결",
                "test-client-id",
                "test-client-secret"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("지원하지 않는 증권사 제공자입니다.");

        verify(brokerCredentialCipher, never()).encrypt(any());
    }
}
