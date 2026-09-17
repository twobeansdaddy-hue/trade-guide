package com.tradeguide.service.broker;

import com.tradeguide.domain.broker.BrokerCredentials;
import com.tradeguide.domain.broker.BrokerAccount;
import com.tradeguide.domain.broker.BrokerConnection;
import com.tradeguide.domain.broker.BrokerConnectionSecretValue;
import com.tradeguide.domain.broker.BrokerHolding;
import com.tradeguide.domain.broker.BrokerHoldingComparison;
import com.tradeguide.domain.broker.BrokerHoldingPreview;
import com.tradeguide.domain.broker.BrokerHoldingSnapshot;
import com.tradeguide.domain.broker.BrokerProvider;
import com.tradeguide.domain.holding.Holding;
import com.tradeguide.domain.member.Member;
import com.tradeguide.domain.portfolio.Portfolio;
import com.tradeguide.domain.portfolio.PortfolioBrokerLink;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.exception.BrokerCallCooldownException;
import com.tradeguide.exception.BrokerCallInProgressException;
import com.tradeguide.exception.BrokerConnectionUnavailableException;
import com.tradeguide.exception.PortfolioBrokerLinkNotFoundException;
import com.tradeguide.exception.PortfolioNotFoundException;
import com.tradeguide.repository.broker.PortfolioBrokerLinkRepository;
import com.tradeguide.repository.portfolio.PortfolioRepository;
import com.tradeguide.service.holding.HoldingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BrokerHoldingPreviewServiceTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-09-04T09:30:00Z"), ZoneOffset.UTC);

    @Mock
    private PortfolioRepository portfolioRepository;

    @Mock
    private PortfolioBrokerLinkRepository portfolioBrokerLinkRepository;

    @Mock
    private BrokerCredentialCipher brokerCredentialCipher;

    @Mock
    private HoldingService holdingService;

    private RecordingHoldingsProvider holdingsProvider;
    private BrokerDuplicateCallGuard brokerDuplicateCallGuard;
    private BrokerHoldingPreviewCallTracker brokerHoldingPreviewCallTracker;
    private BrokerHoldingPreviewService brokerHoldingPreviewService;

    private Member member;
    private Portfolio portfolio;

    @BeforeEach
    void setUp() {
        holdingsProvider = new RecordingHoldingsProvider(new BrokerHoldingSnapshot(List.of(), 0));
        brokerDuplicateCallGuard = new BrokerDuplicateCallGuard();
        brokerHoldingPreviewCallTracker = new BrokerHoldingPreviewCallTracker();
        brokerHoldingPreviewService =
                serviceWith(new BrokerProviderRegistry(List.of(), List.of(holdingsProvider), List.of(), List.of()));

        member = new Member("broker@example.com", "broker-user");
        ReflectionTestUtils.setField(member, "id", 10L);
        portfolio = new Portfolio(member, "성장 포트폴리오");
        ReflectionTestUtils.setField(portfolio, "id", 20L);
    }

    @Test
    void previewsBrokerHoldingsWithDecryptedCredentialsAndAccountSequence() {
        holdingsProvider.snapshot = new BrokerHoldingSnapshot(List.of(
                new BrokerHolding(Market.US, "SOXL", new BigDecimal("30"), new BigDecimal("20.00")),
                new BrokerHolding(Market.US, "AAPL", new BigDecimal("10"), new BigDecimal("150.00"))
        ), 2);

        givenVerifiedLink();
        when(holdingService.getHoldings(10L, 20L)).thenReturn(List.of(
                new Holding(Market.US, "AAPL", new BigDecimal("10"), new BigDecimal("140.00"))
        ));

        BrokerHoldingPreview preview = brokerHoldingPreviewService.getHoldingPreview(10L, 20L);

        assertThat(holdingsProvider.calls).containsExactly(
                List.of("plain-client-id", "plain-client-secret", "plain-account-sequence")
        );
        assertThat(preview.provider()).isEqualTo(BrokerProvider.TOSS_SECURITIES);
        assertThat(preview.brokerConnectionId()).isEqualTo(1L);
        assertThat(preview.maskedAccountNumber()).isEqualTo("*****1234");
        assertThat(preview.syncedAt()).isEqualTo(LocalDateTime.of(2026, 9, 4, 9, 30));
        assertThat(preview.unsupportedMarketCount()).isEqualTo(2);
        assertThat(preview.items()).extracting("ticker", "comparison")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("AAPL", BrokerHoldingComparison.MATCHED),
                        org.assertj.core.groups.Tuple.tuple("SOXL", BrokerHoldingComparison.ONLY_IN_BROKER)
                );
    }

    /** 같은 포트폴리오의 미리보기 조회가 이미 진행 중이면 409에 해당하는 예외로 거부한다. */
    @Test
    void rejectsAConcurrentPreviewCallForTheSamePortfolio() {
        brokerDuplicateCallGuard.acquire(20L, BrokerCallType.HOLDING_PREVIEW);

        assertThatThrownBy(() -> brokerHoldingPreviewService.getHoldingPreview(10L, 20L))
                .isInstanceOf(BrokerCallInProgressException.class);

        assertThat(holdingsProvider.calls).isEmpty();
    }

    /**
     * 미리보기는 저장하지 않으므로 쿨다운 기준 시각은 메모리에서만 온다. 직전 성공 호출이
     * 쿨다운 이내면 429에 해당하는 예외로 거부한다.
     */
    @Test
    void rejectsARepeatedPreviewCallWithinTheCooldownWindow() {
        givenVerifiedLink();
        when(holdingService.getHoldings(10L, 20L)).thenReturn(List.of());
        brokerHoldingPreviewService.getHoldingPreview(10L, 20L);
        holdingsProvider.calls.clear();

        assertThatThrownBy(() -> brokerHoldingPreviewService.getHoldingPreview(10L, 20L))
                .isInstanceOf(BrokerCallCooldownException.class);

        assertThat(holdingsProvider.calls).isEmpty();
    }

    /** 잠금은 성공이든 실패든 항상 풀려서 다음 호출이 409로 영구히 막히지 않는다. */
    @Test
    void releasesTheLockEvenWhenTheProviderCallFails() {
        givenVerifiedLink();
        holdingsProvider.failure = new BrokerConnectionUnavailableException("증권사 응답을 받지 못했습니다.");

        assertThatThrownBy(() -> brokerHoldingPreviewService.getHoldingPreview(10L, 20L))
                .isInstanceOf(BrokerConnectionUnavailableException.class);

        assertThatCode(() ->
                brokerDuplicateCallGuard.acquire(20L, BrokerCallType.HOLDING_PREVIEW))
                .doesNotThrowAnyException();
    }

    @Test
    void failsWhenPortfolioHasNoLinkedBrokerAccount() {
        when(portfolioRepository.findByMember_IdAndId(10L, 20L)).thenReturn(Optional.of(portfolio));
        when(portfolioBrokerLinkRepository.findByPortfolio_Id(20L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> brokerHoldingPreviewService.getHoldingPreview(10L, 20L))
                .isInstanceOf(PortfolioBrokerLinkNotFoundException.class)
                .hasMessage("포트폴리오에 연결된 증권사 계좌가 없습니다.");

        assertThat(holdingsProvider.calls).isEmpty();
    }

    @Test
    void failsWhenPortfolioBelongsToAnotherMember() {
        when(portfolioRepository.findByMember_IdAndId(99L, 20L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> brokerHoldingPreviewService.getHoldingPreview(99L, 20L))
                .isInstanceOf(PortfolioNotFoundException.class)
                .hasMessage("포트폴리오를 찾을 수 없습니다.");

        assertThat(holdingsProvider.calls).isEmpty();
    }

    @Test
    void failsWithoutCallingProviderWhenEncryptionKeyIsUnavailable() {
        BrokerConnection connection = verifiedConnection();
        PortfolioBrokerLink link = new PortfolioBrokerLink(
                portfolio, connection, connection.getAccounts().getFirst(), LocalDateTime.of(2026, 9, 1, 0, 0)
        );
        when(portfolioRepository.findByMember_IdAndId(10L, 20L)).thenReturn(Optional.of(portfolio));
        when(portfolioBrokerLinkRepository.findByPortfolio_Id(20L)).thenReturn(Optional.of(link));
        when(brokerCredentialCipher.decrypt(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new BrokerConnectionUnavailableException("증권사 연결 암호화 키가 설정되지 않았습니다."));

        assertThatThrownBy(() -> brokerHoldingPreviewService.getHoldingPreview(10L, 20L))
                .isInstanceOf(BrokerConnectionUnavailableException.class);

        assertThat(holdingsProvider.calls).isEmpty();
    }

    @Test
    void failsWhenNoHoldingsProviderSupportsTheBroker() {
        BrokerHoldingPreviewService serviceWithoutProviders =
                serviceWith(new BrokerProviderRegistry(List.of(), List.of(), List.of(), List.of()));

        BrokerConnection connection = verifiedConnection();
        PortfolioBrokerLink link = new PortfolioBrokerLink(
                portfolio, connection, connection.getAccounts().getFirst(), LocalDateTime.of(2026, 9, 1, 0, 0)
        );
        when(portfolioRepository.findByMember_IdAndId(10L, 20L)).thenReturn(Optional.of(portfolio));
        when(portfolioBrokerLinkRepository.findByPortfolio_Id(20L)).thenReturn(Optional.of(link));

        assertThatThrownBy(() -> serviceWithoutProviders.getHoldingPreview(10L, 20L))
                .isInstanceOf(BrokerConnectionUnavailableException.class)
                .hasMessage("해당 증권사의 보유 종목 조회를 아직 지원하지 않습니다.");
    }

    /**
     * 미리보기는 아무것도 저장하지 않으므로, 증권사 호출이 실패했을 때 남는 상태도 없다.
     * 원장 조회는 호출이 성공한 뒤에만 일어난다.
     */
    @Test
    void doesNotReadTheLedgerWhenTheBrokerCallFails() {
        givenVerifiedLink();
        holdingsProvider.failure = new BrokerConnectionUnavailableException("증권사 응답을 받지 못했습니다.");

        assertThatThrownBy(() -> brokerHoldingPreviewService.getHoldingPreview(10L, 20L))
                .isInstanceOf(BrokerConnectionUnavailableException.class);

        verify(holdingService, never()).getHoldings(anyLong(), anyLong());
    }

    /**
     * 미리보기는 로더가 연 짧은 읽기 트랜잭션에서 꺼낸 값만으로 응답을 만든다. 증권사 호출은
     * 그 트랜잭션이 닫힌 뒤에 일어나므로, 응답에 들어가는 제공자·연결 id·마스킹 계좌번호는
     * 지연 로딩이 아니라 컨텍스트에서 온다.
     */
    @Test
    void buildsTheResponseFromTheLoadedContextWithoutTouchingEntitiesAfterTheBrokerCall() {
        givenVerifiedLink();
        when(holdingService.getHoldings(10L, 20L)).thenReturn(List.of());

        BrokerHoldingPreview preview = brokerHoldingPreviewService.getHoldingPreview(10L, 20L);

        assertThat(preview.provider()).isEqualTo(BrokerProvider.TOSS_SECURITIES);
        assertThat(preview.brokerConnectionId()).isEqualTo(1L);
        assertThat(preview.maskedAccountNumber()).isEqualTo("*****1234");
        assertThat(preview.items()).isEmpty();
    }

    /**
     * 서비스는 로더가 자기 트랜잭션을 여는 것을 전제로 조립한다. 증권사 호출은 그 트랜잭션
     * 밖에서 일어난다.
     */
    private BrokerHoldingPreviewService serviceWith(BrokerProviderRegistry registry) {
        return new BrokerHoldingPreviewService(
                new BrokerHoldingContextLoader(
                        portfolioRepository,
                        portfolioBrokerLinkRepository,
                        new BrokerCredentialLoader(brokerCredentialCipher),
                        registry
                ),
                holdingService,
                new BrokerHoldingPreviewCalculator(),
                brokerDuplicateCallGuard,
                brokerHoldingPreviewCallTracker,
                FIXED_CLOCK
        );
    }

    private void givenVerifiedLink() {
        BrokerConnection connection = verifiedConnection();
        PortfolioBrokerLink link = new PortfolioBrokerLink(
                portfolio, connection, connection.getAccounts().getFirst(), LocalDateTime.of(2026, 9, 1, 0, 0)
        );

        when(portfolioRepository.findByMember_IdAndId(10L, 20L)).thenReturn(Optional.of(portfolio));
        when(portfolioBrokerLinkRepository.findByPortfolio_Id(20L)).thenReturn(Optional.of(link));
        when(brokerCredentialCipher.decrypt(new EncryptedBrokerCredential("encrypted-client-id", "client-id-iv", 1)))
                .thenReturn("plain-client-id");
        when(brokerCredentialCipher.decrypt(new EncryptedBrokerCredential("encrypted-client-secret", "client-secret-iv", 1)))
                .thenReturn("plain-client-secret");
        when(brokerCredentialCipher.decrypt(new EncryptedBrokerCredential("encrypted-sequence", "sequence-iv", 1)))
                .thenReturn("plain-account-sequence");
    }

    private BrokerConnection verifiedConnection() {
        BrokerConnection connection = new BrokerConnection(member, BrokerProvider.TOSS_SECURITIES, "개인 토스증권");
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
    private static final class RecordingHoldingsProvider implements BrokerHoldingsProvider {

        private final List<List<String>> calls = new ArrayList<>();
        private BrokerHoldingSnapshot snapshot;
        private RuntimeException failure;

        private RecordingHoldingsProvider(BrokerHoldingSnapshot snapshot) {
            this.snapshot = snapshot;
        }

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
            if (failure != null) {
                throw failure;
            }
            return snapshot;
        }
    }
}
