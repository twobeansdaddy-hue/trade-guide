package com.tradeguide.service.broker;

import com.tradeguide.domain.broker.BrokerCredentials;
import com.tradeguide.domain.broker.BrokerConnectionCandidateAccount;
import com.tradeguide.domain.broker.BrokerHoldingSnapshot;
import com.tradeguide.domain.broker.BrokerOrderExclusionCounts;
import com.tradeguide.domain.broker.BrokerOrderHistoryPage;
import com.tradeguide.domain.broker.BrokerOrderHistoryQuery;
import com.tradeguide.domain.broker.BrokerProvider;
import com.tradeguide.domain.broker.BrokerProviderCapability;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.dto.ApiErrorCode;
import com.tradeguide.exception.BrokerConnectionUnavailableException;
import com.tradeguide.exception.BrokerHoldingImportUnprocessableException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class BrokerProviderRegistryTest {

    @Test
    void resolvesConnectionVerifierRegisteredForItsOwnProvider() {
        BrokerConnectionVerifier verifier = new StubConnectionVerifier();
        BrokerProviderRegistry registry = registryWith(List.of(verifier), List.of(), List.of());

        assertThat(registry.requireConnectionVerifier(BrokerProvider.TOSS_SECURITIES)).isSameAs(verifier);
        assertThat(registry.isConnectable(BrokerProvider.TOSS_SECURITIES)).isTrue();
    }

    @Test
    void rejectsNullProvider() {
        BrokerProviderRegistry registry = registryWith(List.of(new StubConnectionVerifier()), List.of(), List.of());

        assertThatThrownBy(() -> registry.requireConnectionVerifier(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("지원하지 않는 증권사 제공자입니다.");
    }

    @Test
    void reportsProviderAsNotConnectableWhenNoVerifierIsRegistered() {
        BrokerProviderRegistry registry = registryWith(List.of(), List.of(), List.of());

        assertThat(registry.isConnectable(BrokerProvider.TOSS_SECURITIES)).isFalse();
        assertThatThrownBy(() -> registry.requireConnectionVerifier(BrokerProvider.TOSS_SECURITIES))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("지원하지 않는 증권사 제공자입니다.");
    }

    @Test
    void resolvesHoldingsProviderRegisteredForItsOwnProvider() {
        BrokerHoldingsProvider holdingsProvider = new StubHoldingsProvider();
        BrokerProviderRegistry registry = registryWith(List.of(), List.of(holdingsProvider), List.of());

        assertThat(registry.requireHoldingsProvider(BrokerProvider.TOSS_SECURITIES)).isSameAs(holdingsProvider);
    }

    @Test
    void rejectsHoldingsLookupWhenNoAdapterIsRegistered() {
        BrokerProviderRegistry registry = registryWith(List.of(), List.of(), List.of());

        assertThatThrownBy(() -> registry.requireHoldingsProvider(BrokerProvider.TOSS_SECURITIES))
                .isInstanceOf(BrokerConnectionUnavailableException.class)
                .hasMessage("해당 증권사의 보유 종목 조회를 아직 지원하지 않습니다.");
    }

    @Test
    void rejectsOrderHistoryLookupWhenNoAdapterIsRegistered() {
        BrokerProviderRegistry registry = registryWith(List.of(), List.of(), List.of());

        assertThat(registry.isOrderHistoryImportable(BrokerProvider.TOSS_SECURITIES)).isFalse();
        assertThatThrownBy(() -> registry.requireOrderHistoryProvider(BrokerProvider.TOSS_SECURITIES))
                .isInstanceOf(BrokerConnectionUnavailableException.class)
                .hasMessage("해당 증권사의 주문 이력 조회를 아직 지원하지 않습니다.");
    }

    /**
     * 어댑터가 빈으로 등록돼 있어도 제공자가 기능을 선언하지 않았다면 열리지 않아야 한다.
     * 주문 이력은 앱 주문 가시성·약관·고정 IP 확인이 끝나기 전까지 닫혀 있어야 하고,
     * 그 관문은 {@link BrokerProvider#getSupportedCapabilities()} 선언 하나로 지킨다.
     */
    @Test
    void keepsOrderHistoryClosedWhileProviderDoesNotDeclareTheCapability() {
        assumeTrue(!BrokerProvider.TOSS_SECURITIES.getSupportedCapabilities()
                .contains(BrokerProviderCapability.TRANSACTION_HISTORY_IMPORT));

        BrokerProviderRegistry registry = registryWith(List.of(), List.of(), List.of(new StubOrderHistoryProvider()));

        assertThat(registry.isOrderHistoryImportable(BrokerProvider.TOSS_SECURITIES)).isFalse();
        assertThatThrownBy(() -> registry.requireOrderHistoryProvider(BrokerProvider.TOSS_SECURITIES))
                .isInstanceOf(BrokerConnectionUnavailableException.class)
                .hasMessage("해당 증권사의 주문 이력 조회를 아직 지원하지 않습니다.");
    }

    @Test
    void rejectsNullProviderForEveryLookup() {
        BrokerProviderRegistry registry = registryWith(
                List.of(), List.of(new StubHoldingsProvider()), List.of(new StubOrderHistoryProvider()));

        assertThat(registry.isOrderHistoryImportable(null)).isFalse();
        assertThatThrownBy(() -> registry.requireHoldingsProvider(null))
                .isInstanceOf(BrokerConnectionUnavailableException.class);
        assertThatThrownBy(() -> registry.requireOrderHistoryProvider(null))
                .isInstanceOf(BrokerConnectionUnavailableException.class);
    }

    /**
     * 선언만으로는 기능이 열리지 않는다. 어댑터가 하나도 등록되지 않은 배포에서는
     * 제공자가 세 기능을 선언했더라도 실제로 쓸 수 있는 기능이 없다.
     */
    @Test
    void reportsNoAvailableCapabilityWhenNoAdapterIsRegistered() {
        BrokerProviderRegistry registry = registryWith(List.of(), List.of(), List.of());

        assertThat(BrokerProvider.TOSS_SECURITIES.getSupportedCapabilities()).isNotEmpty();
        assertThat(registry.availableCapabilities(BrokerProvider.TOSS_SECURITIES)).isEmpty();
        assertThat(registry.isCapabilityAvailable(
                BrokerProvider.TOSS_SECURITIES, BrokerProviderCapability.HOLDING_SNAPSHOT)).isFalse();
    }

    /** 가용 기능은 선언과 어댑터 등록의 교집합이다. 등록된 어댑터의 기능만 나타난다. */
    @Test
    void reportsAvailableCapabilitiesAsIntersectionOfDeclarationAndRegisteredAdapters() {
        BrokerProviderRegistry registry =
                registryWith(List.of(), List.of(new StubHoldingsProvider()), List.of());

        assertThat(registry.availableCapabilities(BrokerProvider.TOSS_SECURITIES))
                .containsExactly(BrokerProviderCapability.HOLDING_SNAPSHOT);

        BrokerProviderRegistry fullRegistry = registryWith(
                List.of(new StubConnectionVerifier()),
                List.of(new StubHoldingsProvider()),
                List.of(new StubOrderHistoryProvider())
        );

        assertThat(fullRegistry.availableCapabilities(BrokerProvider.TOSS_SECURITIES))
                .containsExactlyInAnyOrderElementsOf(
                        BrokerProvider.TOSS_SECURITIES.getSupportedCapabilities());
    }

    /**
     * 어댑터 계약 자체가 없는 기능은 제공자가 선언하더라도 열리지 않는다.
     * 선언이 곧 구현이라고 읽히면, 구현 없는 기능이 화면에 켜진 채로 나간다.
     */
    @Test
    void keepsCashBalanceUnavailableBecauseNoAdapterContractExists() {
        BrokerProviderRegistry registry = registryWith(
                List.of(new StubConnectionVerifier()),
                List.of(new StubHoldingsProvider()),
                List.of(new StubOrderHistoryProvider())
        );

        assertThat(registry.isCapabilityAvailable(
                BrokerProvider.TOSS_SECURITIES, BrokerProviderCapability.CASH_BALANCE)).isFalse();
        assertThat(registry.availableCapabilities(BrokerProvider.TOSS_SECURITIES))
                .doesNotContain(BrokerProviderCapability.CASH_BALANCE);
    }

    /** 기능 관문의 거부는 503이며, 클라이언트는 문구가 아니라 코드로 분기한다. */
    @Test
    void rejectsUnavailableCapabilityWithServiceUnavailableCode() {
        BrokerProviderRegistry registry = registryWith(List.of(), List.of(), List.of());

        assertThatThrownBy(() -> registry.requireCapability(
                BrokerProvider.TOSS_SECURITIES,
                BrokerProviderCapability.HOLDING_SNAPSHOT,
                "해당 증권사의 보유 종목 조회를 아직 지원하지 않습니다."
        ))
                .isInstanceOf(BrokerConnectionUnavailableException.class)
                .hasMessage("해당 증권사의 보유 종목 조회를 아직 지원하지 않습니다.")
                .satisfies(exception -> assertThat(
                        ((BrokerConnectionUnavailableException) exception).getCode())
                        .isEqualTo(ApiErrorCode.BROKER_CAPABILITY_UNAVAILABLE));
    }

    /**
     * 보유 종목도 주문 이력과 같은 이중 관문을 지난다. 어댑터가 빈으로 등록돼 있어도
     * 제공자가 기능을 선언하지 않았다면 열리지 않는다.
     */
    @Test
    void keepsHoldingSnapshotClosedWhileProviderDoesNotDeclareTheCapability() {
        assumeTrue(!BrokerProvider.TOSS_SECURITIES.getSupportedCapabilities()
                .contains(BrokerProviderCapability.HOLDING_SNAPSHOT));

        BrokerProviderRegistry registry = registryWith(List.of(), List.of(new StubHoldingsProvider()), List.of());

        assertThatThrownBy(() -> registry.requireHoldingsProvider(BrokerProvider.TOSS_SECURITIES))
                .isInstanceOf(BrokerConnectionUnavailableException.class)
                .hasMessage("해당 증권사의 보유 종목 조회를 아직 지원하지 않습니다.");
    }

    /** 연결 검증도 같은 관문을 지난다. 카탈로그의 connectable 과 판정이 갈라지지 않아야 한다. */
    @Test
    void keepsConnectionVerificationClosedWhileProviderDoesNotDeclareTheCapability() {
        assumeTrue(!BrokerProvider.TOSS_SECURITIES.getSupportedCapabilities()
                .contains(BrokerProviderCapability.CONNECTION_VERIFICATION));

        BrokerProviderRegistry registry = registryWith(List.of(new StubConnectionVerifier()), List.of(), List.of());

        assertThat(registry.isConnectable(BrokerProvider.TOSS_SECURITIES)).isFalse();
        assertThatThrownBy(() -> registry.requireConnectionVerifier(BrokerProvider.TOSS_SECURITIES))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("지원하지 않는 증권사 제공자입니다.");
    }

    @Test
    void reportsNoAvailableCapabilityForUnknownProvider() {
        BrokerProviderRegistry registry = registryWith(
                List.of(new StubConnectionVerifier()), List.of(), List.of());

        assertThat(registry.availableCapabilities(null)).isEmpty();
        assertThat(registry.isCapabilityAvailable(null, BrokerProviderCapability.HOLDING_SNAPSHOT)).isFalse();
        assertThat(registry.isCapabilityAvailable(BrokerProvider.TOSS_SECURITIES, null)).isFalse();
    }

    /**
     * 원장 반영 시장 관문이다. 어댑터 등록과 무관하게 제공자 선언만으로 판정한다.
     * 어댑터가 없어도 이미 저장된 스냅샷으로 개시 잔고를 반영할 수 있기 때문이다.
     */
    @Test
    void allowsLedgerWritesForUsAndRejectsKoreanMarketWithUnprocessableCode() {
        BrokerProviderRegistry registry = registryWith(List.of(), List.of(), List.of());

        assertThat(registry.isLedgerWritableMarket(BrokerProvider.TOSS_SECURITIES, Market.US)).isTrue();
        registry.requireLedgerWritableMarket(BrokerProvider.TOSS_SECURITIES, Market.US);

        assertThat(registry.isLedgerWritableMarket(BrokerProvider.TOSS_SECURITIES, Market.KR)).isFalse();
        assertThatThrownBy(() ->
                registry.requireLedgerWritableMarket(BrokerProvider.TOSS_SECURITIES, Market.KR))
                .isInstanceOf(BrokerHoldingImportUnprocessableException.class)
                .satisfies(exception -> assertThat(
                        ((BrokerHoldingImportUnprocessableException) exception).getCode())
                        .isEqualTo(ApiErrorCode.BROKER_LEDGER_MARKET_UNSUPPORTED));
    }

    /** 알 수 없는 입력은 통과시키지 않는다. 원장 오염을 되돌리는 비용이 거부보다 크다. */
    @Test
    void rejectsLedgerWriteWhenProviderOrMarketIsUnknown() {
        BrokerProviderRegistry registry = registryWith(List.of(), List.of(), List.of());

        assertThat(registry.isLedgerWritableMarket(null, Market.US)).isFalse();
        assertThat(registry.isLedgerWritableMarket(BrokerProvider.TOSS_SECURITIES, null)).isFalse();
        assertThatThrownBy(() -> registry.requireLedgerWritableMarket(null, Market.US))
                .isInstanceOf(BrokerHoldingImportUnprocessableException.class);
        assertThatThrownBy(() -> registry.requireLedgerWritableMarket(BrokerProvider.TOSS_SECURITIES, null))
                .isInstanceOf(BrokerHoldingImportUnprocessableException.class);
    }

    private BrokerProviderRegistry registryWith(
            List<BrokerConnectionVerifier> verifiers,
            List<BrokerHoldingsProvider> holdingsProviders,
            List<BrokerOrderHistoryProvider> orderHistoryProviders
    ) {
        return new BrokerProviderRegistry(verifiers, holdingsProviders, orderHistoryProviders);
    }

    private static final class StubConnectionVerifier implements BrokerConnectionVerifier {
        @Override
        public BrokerProvider getProvider() {
            return BrokerProvider.TOSS_SECURITIES;
        }

        @Override
        public List<BrokerConnectionCandidateAccount> verify(BrokerCredentials credentials) {
            return List.of();
        }
    }

    private static final class StubHoldingsProvider implements BrokerHoldingsProvider {
        @Override
        public BrokerProvider getProvider() {
            return BrokerProvider.TOSS_SECURITIES;
        }

        @Override
        public BrokerHoldingSnapshot fetchHoldings(BrokerCredentials credentials, String accountSequence) {
            return new BrokerHoldingSnapshot(List.of(), 0);
        }
    }

    private static final class StubOrderHistoryProvider implements BrokerOrderHistoryProvider {
        @Override
        public BrokerProvider getProvider() {
            return BrokerProvider.TOSS_SECURITIES;
        }

        @Override
        public BrokerOrderHistoryPage fetchOrders(
                BrokerCredentials credentials,
                String accountSequence,
                BrokerOrderHistoryQuery query
        ) {
            return BrokerOrderHistoryPage.lastPage(List.of(), BrokerOrderExclusionCounts.none(0));
        }
    }
}
