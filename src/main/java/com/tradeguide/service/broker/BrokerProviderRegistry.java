package com.tradeguide.service.broker;

import com.tradeguide.domain.broker.BrokerProvider;
import com.tradeguide.domain.broker.BrokerProviderCapability;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.dto.ApiErrorCode;
import com.tradeguide.exception.BrokerConnectionUnavailableException;
import com.tradeguide.exception.BrokerHoldingImportUnprocessableException;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 증권사 제공자별 어댑터 구현체를 찾아 연결한다.
 * 서비스 계층은 특정 증권사를 직접 분기하지 않고 이 레지스트리를 거친다.
 * 새 증권사를 지원하려면 어댑터 구현체를 추가하고 {@link BrokerProvider}에 항목과 기능을 선언하면 된다.
 *
 * <p>기능별 조회 경로를 여기 한곳에 모은다. 서비스마다 구현체 목록을 따로 들고 맵을 만들면
 * 지원 여부 판정이 갈라지고, 어떤 기능이 어디서 켜지는지 코드에서 읽기 어려워진다.
 *
 * <p>기능 관문뿐 아니라 <b>원장 반영 시장 관문</b>도 여기 모은다. 제공자가 조회할 수 있는
 * 시장({@link BrokerProvider#getSupportedMarkets()})과 Trade Guide 원장이 표현할 수 있는
 * 시장({@link BrokerProvider#getLedgerWritableMarkets()})은 다른 집합이며, 원장에 쓰는 경로는
 * 예외 없이 {@link #requireLedgerWritableMarket} 또는 {@link #isLedgerWritableMarket}을 지난다.
 *
 * <p>구현체가 빈으로 등록돼 있다는 사실만으로 기능이 열리지는 않는다.
 * {@link BrokerProvider#getSupportedCapabilities()}가 함께 선언돼야 한다.
 * 검증되지 않은 어댑터가 등록되는 것만으로 사용자에게 노출되는 것을 막기 위한 이중 관문이다.
 */
@Component
public class BrokerProviderRegistry {

    private final Map<BrokerProvider, BrokerConnectionVerifier> connectionVerifiers;
    private final Map<BrokerProvider, BrokerHoldingsProvider> holdingsProviders;
    private final Map<BrokerProvider, BrokerOrderHistoryProvider> orderHistoryProviders;

    public BrokerProviderRegistry(
            List<BrokerConnectionVerifier> connectionVerifiers,
            List<BrokerHoldingsProvider> holdingsProviders,
            List<BrokerOrderHistoryProvider> orderHistoryProviders
    ) {
        this.connectionVerifiers = new EnumMap<>(BrokerProvider.class);
        connectionVerifiers.forEach(verifier -> this.connectionVerifiers.put(verifier.getProvider(), verifier));
        this.holdingsProviders = new EnumMap<>(BrokerProvider.class);
        holdingsProviders.forEach(provider -> this.holdingsProviders.put(provider.getProvider(), provider));
        this.orderHistoryProviders = new EnumMap<>(BrokerProvider.class);
        orderHistoryProviders.forEach(provider -> this.orderHistoryProviders.put(provider.getProvider(), provider));
    }

    /**
     * 요청된 제공자의 연결 검증 구현체를 반환한다.
     * 지원하지 않는 제공자(카탈로그에 없거나 구현체가 아직 없는 경우)는 예외로 거부한다.
     */
    public BrokerConnectionVerifier requireConnectionVerifier(BrokerProvider provider) {
        // 연결 생성·재검증의 거부는 예전부터 400이었고 그 계약을 바꾸지 않는다. 관문만
        // 어댑터 등록 단독에서 "어댑터 등록 + 기능 선언"으로 통일한다.
        if (!isCapabilityAvailable(provider, BrokerProviderCapability.CONNECTION_VERIFICATION)) {
            throw new IllegalArgumentException("지원하지 않는 증권사 제공자입니다.");
        }
        return connectionVerifiers.get(provider);
    }

    /**
     * 연결 검증을 실제로 사용할 수 있는지 여부. 제공자 카탈로그 응답의 {@code connectable}이다.
     * {@link #requireConnectionVerifier}와 같은 판정을 쓴다. 둘이 갈라지면 카탈로그가
     * "연결할 수 있다"고 말한 제공자가 연결 생성에서 거부된다.
     */
    public boolean isConnectable(BrokerProvider provider) {
        return isCapabilityAvailable(provider, BrokerProviderCapability.CONNECTION_VERIFICATION);
    }

    /**
     * 요청된 제공자의 보유 종목 조회 구현체를 반환한다.
     * 어댑터가 등록돼 있어도 제공자가 {@link BrokerProviderCapability#HOLDING_SNAPSHOT}을
     * 선언하지 않았다면 아직 열린 기능이 아니므로 거부한다.
     */
    public BrokerHoldingsProvider requireHoldingsProvider(BrokerProvider provider) {
        requireCapability(
                provider,
                BrokerProviderCapability.HOLDING_SNAPSHOT,
                "해당 증권사의 보유 종목 조회를 아직 지원하지 않습니다."
        );
        return holdingsProviders.get(provider);
    }

    /**
     * 요청된 제공자의 주문 이력 조회 구현체를 반환한다.
     * 어댑터가 등록돼 있어도 제공자가 {@link BrokerProviderCapability#TRANSACTION_HISTORY_IMPORT}를
     * 선언하지 않았다면 아직 열린 기능이 아니므로 거부한다.
     */
    public BrokerOrderHistoryProvider requireOrderHistoryProvider(BrokerProvider provider) {
        requireCapability(
                provider,
                BrokerProviderCapability.TRANSACTION_HISTORY_IMPORT,
                "해당 증권사의 주문 이력 조회를 아직 지원하지 않습니다."
        );
        return orderHistoryProviders.get(provider);
    }

    /** 주문 이력 조회가 실제로 열려 있는지 여부. 어댑터 등록과 기능 선언이 모두 필요하다. */
    public boolean isOrderHistoryImportable(BrokerProvider provider) {
        return isCapabilityAvailable(provider, BrokerProviderCapability.TRANSACTION_HISTORY_IMPORT);
    }

    /**
     * 이 서비스 인스턴스에서 해당 제공자로 <b>실제로 쓸 수 있는</b> 기능 집합이다.
     * 제공자가 선언한 기능({@link BrokerProvider#getSupportedCapabilities()})과 어댑터가
     * 등록된 기능의 교집합이며, 카탈로그 응답의 {@code availableCapabilities}가 이 값이다.
     *
     * <p>선언만으로 기능이 열리지 않는 이유는 배포마다 어댑터 구성이 다를 수 있기 때문이고,
     * 어댑터 등록만으로 열리지 않는 이유는 검증되지 않은 어댑터가 빈으로 올라온 것만으로
     * 사용자에게 노출되면 안 되기 때문이다. 화면은 이 값으로 버튼 활성화를 판단한다.
     */
    public Set<BrokerProviderCapability> availableCapabilities(BrokerProvider provider) {
        if (provider == null) {
            return Set.of();
        }

        return provider.getSupportedCapabilities().stream()
                .filter(capability -> hasAdapter(provider, capability))
                .collect(Collectors.toUnmodifiableSet());
    }

    /** 해당 기능이 실제로 열려 있는지 여부. 선언과 어댑터 등록이 모두 필요하다. */
    public boolean isCapabilityAvailable(BrokerProvider provider, BrokerProviderCapability capability) {
        return provider != null
                && capability != null
                && provider.getSupportedCapabilities().contains(capability)
                && hasAdapter(provider, capability);
    }

    /**
     * 해당 기능이 열려 있지 않으면 503으로 매핑되는 예외로 거부한다.
     * 클라이언트는 문구가 아니라 {@link ApiErrorCode#BROKER_CAPABILITY_UNAVAILABLE}로 분기한다.
     *
     * <p>메시지에는 기능 이름조차 넣지 않는다. 호출자가 사용자에게 보여 줄 문구를 직접 준다.
     */
    public void requireCapability(
            BrokerProvider provider,
            BrokerProviderCapability capability,
            String message
    ) {
        if (!isCapabilityAvailable(provider, capability)) {
            throw new BrokerConnectionUnavailableException(message, ApiErrorCode.BROKER_CAPABILITY_UNAVAILABLE);
        }
    }

    /**
     * 기능 하나에 대응하는 어댑터가 이 인스턴스에 등록돼 있는지 본다.
     *
     * <p>{@link BrokerProviderCapability#CASH_BALANCE}에는 아직 어댑터 계약 자체가 없다.
     * 여기서 {@code false}를 돌려주므로, 누군가 제공자에 그 기능을 먼저 선언하더라도
     * {@link #availableCapabilities}에는 나타나지 않는다.
     */
    private boolean hasAdapter(BrokerProvider provider, BrokerProviderCapability capability) {
        return switch (capability) {
            case CONNECTION_VERIFICATION -> connectionVerifiers.containsKey(provider);
            case HOLDING_SNAPSHOT -> holdingsProviders.containsKey(provider);
            case TRANSACTION_HISTORY_IMPORT -> orderHistoryProviders.containsKey(provider);
            case CASH_BALANCE -> false;
        };
    }

    /**
     * 이 제공자에서 가져온 해당 시장의 종목을 매매 원장에 쓸 수 있는지 여부다.
     * 판정만 필요하고 실패로 다루지 않는 경로(일괄 반영의 사유 있는 제외)가 쓴다.
     *
     * <p>제공자나 시장이 {@code null}이면 원장에 쓸 수 없다고 본다. 알 수 없는 입력을
     * 통과시키는 것보다 막는 쪽이 원장 오염을 되돌리는 비용보다 싸다.
     */
    public boolean isLedgerWritableMarket(BrokerProvider provider, Market market) {
        return provider != null
                && market != null
                && provider.getLedgerWritableMarkets().contains(market);
    }

    /**
     * 원장에 쓸 수 없는 시장이면 422로 매핑되는 예외로 거부한다.
     * 단건 승인처럼 결과가 "반영" 아니면 "거부" 둘뿐인 경로가 쓴다.
     *
     * <p>메시지에는 시장 값만 담는다. 계좌 식별 값이나 증권사 원문 오류는 담지 않는다.
     * 클라이언트는 문구가 아니라 {@link ApiErrorCode#BROKER_LEDGER_MARKET_UNSUPPORTED}로 분기한다.
     */
    public void requireLedgerWritableMarket(BrokerProvider provider, Market market) {
        if (!isLedgerWritableMarket(provider, market)) {
            throw new BrokerHoldingImportUnprocessableException(
                    "현재 매매 원장에 반영할 수 있는 시장이 아닙니다: " + market,
                    ApiErrorCode.BROKER_LEDGER_MARKET_UNSUPPORTED
            );
        }
    }
}
