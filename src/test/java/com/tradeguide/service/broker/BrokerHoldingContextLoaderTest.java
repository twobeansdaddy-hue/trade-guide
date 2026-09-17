package com.tradeguide.service.broker;

import com.tradeguide.domain.broker.BrokerAccount;
import com.tradeguide.domain.broker.BrokerConnection;
import com.tradeguide.domain.broker.BrokerConnectionSecretValue;
import com.tradeguide.domain.broker.BrokerCredentials;
import com.tradeguide.domain.broker.BrokerHoldingSnapshot;
import com.tradeguide.domain.broker.BrokerProvider;
import com.tradeguide.domain.member.Member;
import com.tradeguide.domain.portfolio.Portfolio;
import com.tradeguide.domain.portfolio.PortfolioBrokerLink;
import com.tradeguide.dto.ApiErrorCode;
import com.tradeguide.exception.BrokerConnectionReverificationRequiredException;
import com.tradeguide.exception.BrokerConnectionUnavailableException;
import com.tradeguide.exception.PortfolioBrokerLinkNotFoundException;
import com.tradeguide.exception.PortfolioNotFoundException;
import com.tradeguide.repository.broker.PortfolioBrokerLinkRepository;
import com.tradeguide.repository.portfolio.PortfolioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 보유 종목 조회 컨텍스트 로더의 계약을 고정한다.
 *
 * <p>여기서 지키는 것은 세 가지다. 트랜잭션 안에서 필요한 값을 <b>전부</b> 꺼내 오는가,
 * 기능 관문을 <b>복호화 전에</b> 지나는가, 그리고 평문이 로그·문자열로 새지 않는가.
 */
@ExtendWith(MockitoExtension.class)
class BrokerHoldingContextLoaderTest {

    @Mock
    private PortfolioRepository portfolioRepository;

    @Mock
    private PortfolioBrokerLinkRepository portfolioBrokerLinkRepository;

    @Mock
    private BrokerCredentialCipher brokerCredentialCipher;

    private StubHoldingsProvider holdingsProvider;
    private BrokerHoldingContextLoader brokerHoldingContextLoader;

    private Member member;
    private Portfolio portfolio;

    @BeforeEach
    void setUp() {
        holdingsProvider = new StubHoldingsProvider();
        brokerHoldingContextLoader = loaderWith(
                new BrokerProviderRegistry(List.of(), List.of(holdingsProvider), List.of(), List.of()));

        member = new Member("broker@example.com", "broker-user");
        ReflectionTestUtils.setField(member, "id", 10L);
        portfolio = new Portfolio(member, "성장 포트폴리오");
        ReflectionTestUtils.setField(portfolio, "id", 20L);
    }

    /**
     * 로더의 트랜잭션은 선언으로만 존재한다. 애너테이션이 사라지면 증권사 호출이 다시 트랜잭션
     * 안으로 들어오는 것이 아니라, 지연 로딩이 호출 시점에 터진다. 어느 쪽이든 조용히 깨지므로
     * 선언 자체를 테스트로 고정한다.
     */
    @Test
    void declaresItsOwnReadOnlyTransactionSoTheBrokerCallCanHappenOutsideOfIt() throws NoSuchMethodException {
        Transactional transactional = BrokerHoldingContextLoader.class
                .getMethod("load", Long.class, Long.class)
                .getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isTrue();
    }

    @Test
    void loadsEverythingNeededForTheBrokerCallInOneRead() {
        givenVerifiedLink();

        BrokerHoldingContextLoader.HoldingContext context = brokerHoldingContextLoader.load(10L, 20L);

        assertThat(context.brokerConnectionId()).isEqualTo(1L);
        assertThat(context.brokerAccountId()).isEqualTo(100L);
        assertThat(context.provider()).isEqualTo(BrokerProvider.TOSS_SECURITIES);
        assertThat(context.maskedAccountNumber()).isEqualTo("*****1234");
        assertThat(context.holdingsProvider()).isSameAs(holdingsProvider);
        assertThat(context.credentials().keys()).containsExactlyInAnyOrder("clientId", "clientSecret");
        assertThat(context.accountSequence()).isEqualTo("plain-account-sequence");
    }

    /** 컨텍스트가 어댑터 호출까지 책임지므로, 호출하는 서비스는 평문을 손에 쥘 필요가 없다. */
    @Test
    void fetchesHoldingsThroughTheContextWithTheDecryptedValues() {
        givenVerifiedLink();

        BrokerHoldingSnapshot snapshot = brokerHoldingContextLoader.load(10L, 20L).fetchHoldings();

        assertThat(snapshot).isSameAs(holdingsProvider.snapshot);
        assertThat(holdingsProvider.calls).containsExactly(
                List.of("plain-client-id", "plain-client-secret", "plain-account-sequence"));
    }

    /**
     * 값이 새는 가장 흔한 경로가 로깅과 예외 메시지다. 계좌 일련번호와 자격 증명 평문은
     * {@code toString}에 나타나지 않아야 한다.
     */
    @Test
    void neverExposesPlaintextSecretsInItsStringForm() {
        givenVerifiedLink();

        String rendered = brokerHoldingContextLoader.load(10L, 20L).toString();

        assertThat(rendered)
                .doesNotContain("plain-account-sequence")
                .doesNotContain("plain-client-id")
                .doesNotContain("plain-client-secret");
        assertThat(rendered).contains("TOSS_SECURITIES");
    }

    /**
     * 기능 관문은 복호화보다 앞이다. 아직 열려 있지 않은 기능 때문에 평문이 만들어지면 안 된다.
     */
    @Test
    void refusesBeforeDecryptingWhenTheHoldingSnapshotCapabilityIsNotAvailable() {
        BrokerHoldingContextLoader loaderWithoutProviders =
                loaderWith(new BrokerProviderRegistry(List.of(), List.of(), List.of(), List.of()));
        BrokerConnection connection = verifiedConnection();
        when(portfolioRepository.findByMember_IdAndId(10L, 20L)).thenReturn(Optional.of(portfolio));
        when(portfolioBrokerLinkRepository.findByPortfolio_Id(20L))
                .thenReturn(Optional.of(linkedTo(connection)));

        assertThatThrownBy(() -> loaderWithoutProviders.load(10L, 20L))
                .isInstanceOf(BrokerConnectionUnavailableException.class)
                .hasMessage("해당 증권사의 보유 종목 조회를 아직 지원하지 않습니다.");

        verifyNoInteractions(brokerCredentialCipher);
    }

    @Test
    void refusesWhenTheConnectionIsNoLongerVerified() {
        BrokerConnection connection =
                new BrokerConnection(member, BrokerProvider.TOSS_SECURITIES, "개인 토스증권");
        ReflectionTestUtils.setField(connection, "id", 1L);
        BrokerAccount account = new BrokerAccount("encrypted-sequence", "sequence-iv", "*****1234", "위탁", 1);
        ReflectionTestUtils.setField(account, "id", 100L);
        connection.reconcileVerifiedAccounts(List.of(account));

        when(portfolioRepository.findByMember_IdAndId(10L, 20L)).thenReturn(Optional.of(portfolio));
        when(portfolioBrokerLinkRepository.findByPortfolio_Id(20L))
                .thenReturn(Optional.of(linkedTo(connection)));

        assertThatThrownBy(() -> brokerHoldingContextLoader.load(10L, 20L))
                .isInstanceOf(BrokerConnectionReverificationRequiredException.class)
                .hasMessage("증권사 연결을 다시 검증해야 합니다.")
                .extracting(exception -> ((BrokerConnectionReverificationRequiredException) exception).getCode())
                .isEqualTo(ApiErrorCode.BROKER_CONNECTION_REVERIFICATION_REQUIRED);

        verifyNoInteractions(brokerCredentialCipher);
    }

    @Test
    void refusesWithNotFoundWhenThePortfolioDoesNotExist() {
        when(portfolioRepository.findByMember_IdAndId(10L, 20L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> brokerHoldingContextLoader.load(10L, 20L))
                .isInstanceOf(PortfolioNotFoundException.class)
                .hasMessage("포트폴리오를 찾을 수 없습니다.")
                .extracting(exception -> ((PortfolioNotFoundException) exception).getCode())
                .isEqualTo(ApiErrorCode.PORTFOLIO_NOT_FOUND);

        verifyNoInteractions(brokerCredentialCipher);
    }

    @Test
    void refusesWithNotFoundWhenThePortfolioHasNoBrokerLink() {
        when(portfolioRepository.findByMember_IdAndId(10L, 20L)).thenReturn(Optional.of(portfolio));
        when(portfolioBrokerLinkRepository.findByPortfolio_Id(20L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> brokerHoldingContextLoader.load(10L, 20L))
                .isInstanceOf(PortfolioBrokerLinkNotFoundException.class)
                .hasMessage("포트폴리오에 연결된 증권사 계좌가 없습니다.")
                .extracting(exception -> ((PortfolioBrokerLinkNotFoundException) exception).getCode())
                .isEqualTo(ApiErrorCode.PORTFOLIO_BROKER_LINK_NOT_FOUND);

        verifyNoInteractions(brokerCredentialCipher);
    }

    private BrokerHoldingContextLoader loaderWith(BrokerProviderRegistry registry) {
        return new BrokerHoldingContextLoader(
                portfolioRepository,
                portfolioBrokerLinkRepository,
                new BrokerCredentialLoader(brokerCredentialCipher),
                registry
        );
    }

    private void givenVerifiedLink() {
        when(portfolioRepository.findByMember_IdAndId(10L, 20L)).thenReturn(Optional.of(portfolio));
        when(portfolioBrokerLinkRepository.findByPortfolio_Id(20L))
                .thenReturn(Optional.of(linkedTo(verifiedConnection())));
        when(brokerCredentialCipher.decrypt(new EncryptedBrokerCredential("encrypted-client-id", "client-id-iv", 1)))
                .thenReturn("plain-client-id");
        when(brokerCredentialCipher.decrypt(
                new EncryptedBrokerCredential("encrypted-client-secret", "client-secret-iv", 1)))
                .thenReturn("plain-client-secret");
        when(brokerCredentialCipher.decrypt(new EncryptedBrokerCredential("encrypted-sequence", "sequence-iv", 1)))
                .thenReturn("plain-account-sequence");
    }

    private PortfolioBrokerLink linkedTo(BrokerConnection connection) {
        return new PortfolioBrokerLink(
                portfolio, connection, connection.getAccounts().getFirst(), LocalDateTime.of(2026, 9, 1, 0, 0));
    }

    private BrokerConnection verifiedConnection() {
        BrokerConnection connection =
                new BrokerConnection(member, BrokerProvider.TOSS_SECURITIES, "개인 토스증권");
        ReflectionTestUtils.setField(connection, "id", 1L);
        connection.replaceSecretValues(List.of(
                new BrokerConnectionSecretValue("clientId", "encrypted-client-id", "client-id-iv", 1),
                new BrokerConnectionSecretValue("clientSecret", "encrypted-client-secret", "client-secret-iv", 1)
        ));

        BrokerAccount account = new BrokerAccount("encrypted-sequence", "sequence-iv", "*****1234", "위탁", 1);
        ReflectionTestUtils.setField(account, "id", 100L);
        connection.reconcileVerifiedAccounts(List.of(account));
        connection.markConnected("*****1234");
        return connection;
    }

    /** 테스트용 가짜 제공자다. 실제 토스증권 API를 호출하지 않는다. */
    private static final class StubHoldingsProvider implements BrokerHoldingsProvider {

        private final List<List<String>> calls = new java.util.ArrayList<>();
        private final BrokerHoldingSnapshot snapshot = new BrokerHoldingSnapshot(List.of(), 0);

        @Override
        public BrokerProvider getProvider() {
            return BrokerProvider.TOSS_SECURITIES;
        }

        @Override
        public BrokerHoldingSnapshot fetchHoldings(BrokerCredentials credentials, String accountSequence) {
            calls.add(List.of(
                    credentials.require("clientId"),
                    credentials.require("clientSecret"),
                    accountSequence
            ));
            return snapshot;
        }
    }
}
