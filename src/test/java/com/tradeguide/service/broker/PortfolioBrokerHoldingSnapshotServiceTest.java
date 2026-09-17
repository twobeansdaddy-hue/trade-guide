package com.tradeguide.service.broker;

import com.tradeguide.domain.broker.BrokerCredentials;
import com.tradeguide.domain.broker.BrokerAccount;
import com.tradeguide.domain.broker.BrokerConnection;
import com.tradeguide.domain.broker.BrokerConnectionSecretValue;
import com.tradeguide.domain.broker.BrokerHolding;
import com.tradeguide.domain.broker.BrokerHoldingSnapshot;
import com.tradeguide.domain.broker.BrokerProvider;
import com.tradeguide.domain.broker.BrokerHoldingComparison;
import com.tradeguide.domain.broker.BrokerHoldingPreview;
import com.tradeguide.domain.holding.Holding;
import com.tradeguide.domain.broker.PortfolioBrokerHoldingSnapshot;
import com.tradeguide.domain.member.Member;
import com.tradeguide.domain.portfolio.Portfolio;
import com.tradeguide.domain.portfolio.PortfolioBrokerLink;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.exception.BrokerCallCooldownException;
import com.tradeguide.exception.BrokerCallInProgressException;
import com.tradeguide.exception.BrokerConnectionUnavailableException;
import com.tradeguide.exception.BrokerHoldingSnapshotNotFoundException;
import com.tradeguide.exception.PortfolioBrokerLinkNotFoundException;
import com.tradeguide.exception.PortfolioNotFoundException;
import com.tradeguide.repository.broker.PortfolioBrokerHoldingSnapshotRepository;
import com.tradeguide.repository.broker.PortfolioBrokerLinkRepository;
import com.tradeguide.repository.portfolio.PortfolioRepository;
import com.tradeguide.service.holding.HoldingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioBrokerHoldingSnapshotServiceTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-09-04T09:30:00Z"), ZoneOffset.UTC);

    @Mock
    private PortfolioRepository portfolioRepository;

    @Mock
    private PortfolioBrokerLinkRepository portfolioBrokerLinkRepository;

    @Mock
    private PortfolioBrokerHoldingSnapshotRepository portfolioBrokerHoldingSnapshotRepository;

    @Mock
    private BrokerCredentialCipher brokerCredentialCipher;

    @Mock
    private HoldingService holdingService;

    private RecordingHoldingsProvider holdingsProvider;
    private BrokerDuplicateCallGuard brokerDuplicateCallGuard;
    private PortfolioBrokerHoldingSnapshotService portfolioBrokerHoldingSnapshotService;

    private Member member;
    private Portfolio portfolio;

    @BeforeEach
    void setUp() {
        holdingsProvider = new RecordingHoldingsProvider(new BrokerHoldingSnapshot(List.of(), 0));
        brokerDuplicateCallGuard = new BrokerDuplicateCallGuard();
        portfolioBrokerHoldingSnapshotService =
                serviceWith(new BrokerProviderRegistry(List.of(), List.of(holdingsProvider), List.of(), List.of()));

        member = new Member("broker@example.com", "broker-user");
        ReflectionTestUtils.setField(member, "id", 10L);
        portfolio = new Portfolio(member, "성장 포트폴리오");
        ReflectionTestUtils.setField(portfolio, "id", 20L);
    }

    @Test
    void refreshesAndPersistsSnapshotThroughBrokerAdapter() {
        holdingsProvider.snapshot = new BrokerHoldingSnapshot(List.of(
                new BrokerHolding(Market.US, "SOXL", new BigDecimal("30"), new BigDecimal("20.00")),
                new BrokerHolding(Market.US, "AAPL", new BigDecimal("10"), new BigDecimal("150.00"))
        ), 2);
        givenVerifiedLink();
        when(portfolioBrokerHoldingSnapshotRepository.save(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PortfolioBrokerHoldingSnapshot saved = portfolioBrokerHoldingSnapshotService.refreshSnapshot(10L, 20L);

        assertThat(holdingsProvider.calls).containsExactly(
                List.of("plain-client-id", "plain-client-secret", "plain-account-sequence")
        );
        assertThat(saved.getPortfolio()).isEqualTo(portfolio);
        assertThat(saved.getSyncedAt()).isEqualTo(LocalDateTime.of(2026, 9, 4, 9, 30));
        assertThat(saved.getUnsupportedMarketCount()).isEqualTo(2);
        assertThat(saved.getItems()).extracting("ticker").containsExactly("SOXL", "AAPL");

        ArgumentCaptor<PortfolioBrokerHoldingSnapshot> captor =
                ArgumentCaptor.forClass(PortfolioBrokerHoldingSnapshot.class);
        verify(portfolioBrokerHoldingSnapshotRepository).save(captor.capture());
        assertThat(captor.getValue()).isSameAs(saved);
    }

    /** 같은 포트폴리오의 스냅샷 갱신이 이미 진행 중이면 409에 해당하는 예외로 거부한다. */
    @Test
    void rejectsAConcurrentRefreshCallForTheSamePortfolio() {
        brokerDuplicateCallGuard.acquire(20L, BrokerCallType.HOLDING_SNAPSHOT);

        assertThatThrownBy(() -> portfolioBrokerHoldingSnapshotService.refreshSnapshot(10L, 20L))
                .isInstanceOf(BrokerCallInProgressException.class);

        assertThat(holdingsProvider.calls).isEmpty();
    }

    /**
     * 스냅샷 갱신의 쿨다운 기준 시각은 이미 존재하는 {@code findFirstByPortfolio_IdOrderBySyncedAtDesc}
     * 조회의 {@code syncedAt}을 그대로 쓴다. 직전 저장이 쿨다운 이내면 429에 해당하는 예외로 거부한다.
     */
    @Test
    void rejectsARepeatedRefreshCallWithinTheCooldownWindow() {
        PortfolioBrokerHoldingSnapshot latest = new PortfolioBrokerHoldingSnapshot(
                portfolio,
                verifiedConnection(),
                verifiedConnection().getAccounts().getFirst(),
                LocalDateTime.now(FIXED_CLOCK).minusSeconds(2),
                0,
                List.of()
        );
        when(portfolioBrokerHoldingSnapshotRepository.findFirstByPortfolio_IdOrderBySyncedAtDesc(20L))
                .thenReturn(Optional.of(latest));

        assertThatThrownBy(() -> portfolioBrokerHoldingSnapshotService.refreshSnapshot(10L, 20L))
                .isInstanceOf(BrokerCallCooldownException.class);

        assertThat(holdingsProvider.calls).isEmpty();
    }

    /** 쿨다운 시간이 지났으면 직전 스냅샷이 있어도 정상적으로 갱신한다. */
    @Test
    void allowsARefreshCallAfterTheCooldownWindowHasPassed() {
        givenVerifiedLink();
        when(portfolioBrokerHoldingSnapshotRepository.save(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        PortfolioBrokerHoldingSnapshot latest = new PortfolioBrokerHoldingSnapshot(
                portfolio,
                verifiedConnection(),
                verifiedConnection().getAccounts().getFirst(),
                LocalDateTime.now(FIXED_CLOCK).minusSeconds(5),
                0,
                List.of()
        );
        when(portfolioBrokerHoldingSnapshotRepository.findFirstByPortfolio_IdOrderBySyncedAtDesc(20L))
                .thenReturn(Optional.of(latest));

        PortfolioBrokerHoldingSnapshot saved = portfolioBrokerHoldingSnapshotService.refreshSnapshot(10L, 20L);

        assertThat(saved.getSyncedAt()).isEqualTo(LocalDateTime.now(FIXED_CLOCK));
        assertThat(holdingsProvider.calls).hasSize(1);
    }

    /** 잠금은 성공이든 실패든 항상 풀려서 다음 호출이 409로 영구히 막히지 않는다. */
    @Test
    void releasesTheLockEvenWhenTheProviderCallFails() {
        givenVerifiedLink();
        holdingsProvider.failure = new BrokerConnectionUnavailableException("증권사 응답을 받지 못했습니다.");

        assertThatThrownBy(() -> portfolioBrokerHoldingSnapshotService.refreshSnapshot(10L, 20L))
                .isInstanceOf(BrokerConnectionUnavailableException.class);

        assertThatCode(() ->
                brokerDuplicateCallGuard.acquire(20L, BrokerCallType.HOLDING_SNAPSHOT))
                .doesNotThrowAnyException();
    }

    @Test
    void failsRefreshWhenPortfolioBelongsToAnotherMember() {
        when(portfolioRepository.findByMember_IdAndId(99L, 20L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> portfolioBrokerHoldingSnapshotService.refreshSnapshot(99L, 20L))
                .isInstanceOf(PortfolioNotFoundException.class)
                .hasMessage("포트폴리오를 찾을 수 없습니다.");

        assertThat(holdingsProvider.calls).isEmpty();
    }

    @Test
    void failsRefreshWhenPortfolioHasNoLinkedBrokerAccount() {
        when(portfolioRepository.findByMember_IdAndId(10L, 20L)).thenReturn(Optional.of(portfolio));
        when(portfolioBrokerLinkRepository.findByPortfolio_Id(20L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> portfolioBrokerHoldingSnapshotService.refreshSnapshot(10L, 20L))
                .isInstanceOf(PortfolioBrokerLinkNotFoundException.class)
                .hasMessage("포트폴리오에 연결된 증권사 계좌가 없습니다.");

        assertThat(holdingsProvider.calls).isEmpty();
    }

    @Test
    void failsRefreshWhenNoHoldingsProviderSupportsTheBroker() {
        PortfolioBrokerHoldingSnapshotService serviceWithoutProviders =
                serviceWith(new BrokerProviderRegistry(List.of(), List.of(), List.of(), List.of()));

        BrokerConnection connection = verifiedConnection();
        PortfolioBrokerLink link = new PortfolioBrokerLink(
                portfolio, connection, connection.getAccounts().getFirst(), LocalDateTime.of(2026, 9, 1, 0, 0)
        );
        when(portfolioRepository.findByMember_IdAndId(10L, 20L)).thenReturn(Optional.of(portfolio));
        when(portfolioBrokerLinkRepository.findByPortfolio_Id(20L)).thenReturn(Optional.of(link));

        assertThatThrownBy(() -> serviceWithoutProviders.refreshSnapshot(10L, 20L))
                .isInstanceOf(BrokerConnectionUnavailableException.class)
                .hasMessage("해당 증권사의 보유 종목 조회를 아직 지원하지 않습니다.");
    }

    @Test
    void returnsLatestSnapshotWithoutCallingProvider() {
        PortfolioBrokerHoldingSnapshot latest = new PortfolioBrokerHoldingSnapshot(
                portfolio,
                verifiedConnection(),
                verifiedConnection().getAccounts().getFirst(),
                LocalDateTime.of(2026, 9, 4, 9, 30),
                0,
                List.of()
        );
        when(portfolioRepository.findByMember_IdAndId(10L, 20L)).thenReturn(Optional.of(portfolio));
        when(portfolioBrokerHoldingSnapshotRepository.findFirstByPortfolio_IdOrderBySyncedAtDesc(20L))
                .thenReturn(Optional.of(latest));

        PortfolioBrokerHoldingSnapshot result = portfolioBrokerHoldingSnapshotService.getLatestSnapshot(10L, 20L);

        assertThat(result).isSameAs(latest);
        assertThat(holdingsProvider.calls).isEmpty();
    }

    @Test
    void failsLatestSnapshotWhenNoneSaved() {
        when(portfolioRepository.findByMember_IdAndId(10L, 20L)).thenReturn(Optional.of(portfolio));
        when(portfolioBrokerHoldingSnapshotRepository.findFirstByPortfolio_IdOrderBySyncedAtDesc(20L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> portfolioBrokerHoldingSnapshotService.getLatestSnapshot(10L, 20L))
                .isInstanceOf(BrokerHoldingSnapshotNotFoundException.class)
                .hasMessage("저장된 증권사 보유 종목 스냅샷이 없습니다.");
    }

    @Test
    void failsLatestSnapshotWhenPortfolioBelongsToAnotherMember() {
        when(portfolioRepository.findByMember_IdAndId(99L, 20L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> portfolioBrokerHoldingSnapshotService.getLatestSnapshot(99L, 20L))
                .isInstanceOf(PortfolioNotFoundException.class)
                .hasMessage("포트폴리오를 찾을 수 없습니다.");
    }

    @Test
    void comparesLatestSnapshotAgainstTradeGuideHoldingsWithoutCallingProvider() {
        PortfolioBrokerHoldingSnapshot latest = new PortfolioBrokerHoldingSnapshot(
                portfolio,
                verifiedConnection(),
                verifiedConnection().getAccounts().getFirst(),
                LocalDateTime.of(2026, 9, 4, 9, 30),
                2,
                List.of(
                        new BrokerHolding(Market.US, "SOXL", new BigDecimal("30"), new BigDecimal("20.00")),
                        new BrokerHolding(Market.US, "AAPL", new BigDecimal("10"), new BigDecimal("150.00"))
                )
        );
        when(portfolioRepository.findByMember_IdAndId(10L, 20L)).thenReturn(Optional.of(portfolio));
        when(portfolioBrokerHoldingSnapshotRepository.findFirstByPortfolio_IdOrderBySyncedAtDesc(20L))
                .thenReturn(Optional.of(latest));
        when(holdingService.getHoldings(10L, 20L)).thenReturn(List.of(
                new Holding(Market.US, "AAPL", new BigDecimal("10"), new BigDecimal("140.00"))
        ));

        BrokerHoldingPreview comparison = portfolioBrokerHoldingSnapshotService.getLatestSnapshotComparison(10L, 20L);

        assertThat(comparison.provider()).isEqualTo(BrokerProvider.TOSS_SECURITIES);
        assertThat(comparison.brokerConnectionId()).isEqualTo(1L);
        assertThat(comparison.maskedAccountNumber()).isEqualTo("*****1234");
        assertThat(comparison.syncedAt()).isEqualTo(LocalDateTime.of(2026, 9, 4, 9, 30));
        assertThat(comparison.unsupportedMarketCount()).isEqualTo(2);
        assertThat(comparison.items()).extracting("ticker", "comparison")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("AAPL", BrokerHoldingComparison.MATCHED),
                        org.assertj.core.groups.Tuple.tuple("SOXL", BrokerHoldingComparison.ONLY_IN_BROKER)
                );
        assertThat(holdingsProvider.calls).isEmpty();
    }

    @Test
    void failsComparisonWhenNoSnapshotSaved() {
        when(portfolioRepository.findByMember_IdAndId(10L, 20L)).thenReturn(Optional.of(portfolio));
        when(portfolioBrokerHoldingSnapshotRepository.findFirstByPortfolio_IdOrderBySyncedAtDesc(20L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> portfolioBrokerHoldingSnapshotService.getLatestSnapshotComparison(10L, 20L))
                .isInstanceOf(BrokerHoldingSnapshotNotFoundException.class)
                .hasMessage("저장된 증권사 보유 종목 스냅샷이 없습니다.");

        assertThat(holdingsProvider.calls).isEmpty();
    }

    @Test
    void failsComparisonWhenPortfolioBelongsToAnotherMember() {
        when(portfolioRepository.findByMember_IdAndId(99L, 20L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> portfolioBrokerHoldingSnapshotService.getLatestSnapshotComparison(99L, 20L))
                .isInstanceOf(PortfolioNotFoundException.class)
                .hasMessage("포트폴리오를 찾을 수 없습니다.");
    }

    /**
     * 증권사 호출이 실패하면 쓰기 트랜잭션이 시작조차 하지 않는다.
     *
     * <p>이 경로의 부분 영속화 방지는 롤백이 아니라 <b>경계</b>로 보장한다. 저장은 조회가
     * 성공한 뒤에만 열리므로, 조회 실패 시 저장할 대상 자체가 없다.
     */
    @Test
    void persistsNothingWhenTheBrokerCallFails() {
        givenVerifiedLink();
        holdingsProvider.failure = new BrokerConnectionUnavailableException("증권사 응답을 받지 못했습니다.");

        assertThatThrownBy(() -> portfolioBrokerHoldingSnapshotService.refreshSnapshot(10L, 20L))
                .isInstanceOf(BrokerConnectionUnavailableException.class);

        verify(portfolioBrokerHoldingSnapshotRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    /**
     * 조회 시각은 저장 시각이 아니라 증권사가 값을 보고한 시점을 찍는다. 조회를 트랜잭션 밖으로
     * 빼면서 조회와 저장 사이가 벌어졌으므로, 그 사이의 시각을 쓰지 않는다는 사실을 고정한다.
     */
    @Test
    void stampsSyncedAtRightAfterTheBrokerCallInsteadOfAtPersistTime() {
        givenVerifiedLink();
        when(portfolioBrokerHoldingSnapshotRepository.save(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PortfolioBrokerHoldingSnapshot saved = portfolioBrokerHoldingSnapshotService.refreshSnapshot(10L, 20L);

        assertThat(saved.getSyncedAt()).isEqualTo(LocalDateTime.of(2026, 9, 4, 9, 30));
        assertThat(saved.getCreatedAt()).isEqualTo(saved.getSyncedAt());
    }

    /**
     * 조회하는 동안 사용자가 링크를 다른 계좌로 바꾸면 저장하지 않는다.
     * 확인 없이 저장하면 A 계좌에서 가져온 보유 종목이 B 계좌의 스냅샷으로 남는다.
     */
    @Test
    void persistsNothingWhenTheLinkedAccountChangesWhileTheBrokerCallIsInFlight() {
        givenVerifiedLink();

        BrokerConnection otherConnection = verifiedConnection();
        ReflectionTestUtils.setField(otherConnection, "id", 2L);
        ReflectionTestUtils.setField(otherConnection.getAccounts().getFirst(), "id", 200L);
        PortfolioBrokerLink relinked = new PortfolioBrokerLink(
                portfolio, otherConnection, otherConnection.getAccounts().getFirst(),
                LocalDateTime.of(2026, 9, 1, 0, 0)
        );
        // 조회 전에는 원래 링크를, 저장 시점에는 바뀐 링크를 돌려준다.
        when(portfolioBrokerLinkRepository.findByPortfolio_Id(20L))
                .thenReturn(Optional.of(linkedTo(verifiedConnection())), Optional.of(relinked));

        assertThatThrownBy(() -> portfolioBrokerHoldingSnapshotService.refreshSnapshot(10L, 20L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("증권사 계좌 연결이 바뀌었습니다");

        assertThat(holdingsProvider.calls).hasSize(1);
        verify(portfolioBrokerHoldingSnapshotRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    /**
     * 서비스는 로더와 라이터가 각자의 트랜잭션을 여는 것을 전제로 조립한다. 증권사 호출은
     * 그 두 트랜잭션 사이, 즉 트랜잭션 밖에서 일어난다.
     */
    private PortfolioBrokerHoldingSnapshotService serviceWith(BrokerProviderRegistry registry) {
        return new PortfolioBrokerHoldingSnapshotService(
                portfolioRepository,
                portfolioBrokerHoldingSnapshotRepository,
                new BrokerHoldingContextLoader(
                        portfolioRepository,
                        portfolioBrokerLinkRepository,
                        new BrokerCredentialLoader(brokerCredentialCipher),
                        registry
                ),
                new PortfolioBrokerHoldingSnapshotWriter(
                        portfolioRepository,
                        portfolioBrokerLinkRepository,
                        portfolioBrokerHoldingSnapshotRepository
                ),
                holdingService,
                new BrokerHoldingPreviewCalculator(),
                brokerDuplicateCallGuard,
                FIXED_CLOCK
        );
    }

    private PortfolioBrokerLink linkedTo(BrokerConnection connection) {
        return new PortfolioBrokerLink(
                portfolio, connection, connection.getAccounts().getFirst(), LocalDateTime.of(2026, 9, 1, 0, 0)
        );
    }

    private void givenVerifiedLink() {
        when(portfolioRepository.findByMember_IdAndId(10L, 20L)).thenReturn(Optional.of(portfolio));
        when(portfolioBrokerLinkRepository.findByPortfolio_Id(20L))
                .thenReturn(Optional.of(linkedTo(verifiedConnection())));
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
