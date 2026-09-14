package com.tradeguide.domain.broker;

import com.tradeguide.domain.trade.Market;

import java.util.List;
import java.util.Set;

/**
 * Trade Guide가 아는 증권사 제공자 카탈로그다.
 * 각 항목은 표시 이름과, 현재 이 서비스가 실제로 구현한 기능 집합을 선언한다.
 * 목록에 없는 제공자는 선택할 수 없다({@link com.tradeguide.service.broker.BrokerProviderRegistry} 참고).
 *
 * <p>{@link #getCredentialFields()}는 연결 화면이 제공자별 분기 없이 입력 폼을 그릴 수 있도록
 * "어떤 값을 입력받아야 하는지"만 선언한다. 저장된 자격 증명 값은 어떤 형태로도 담지 않는다.
 */
public enum BrokerProvider {
    TOSS_SECURITIES(
            "토스증권",
            // 주문 이력 조회를 여는 조건은 "앱에서 낸 주문이 응답에 포함되는가"였고, 사용자가 본인
            // 계좌로 직접 확인해 포함됨을 확인했다. 같은 요청을 두 번 실행해 결과가 동일한 것도 함께
            // 확인했다. 여기 값을 되돌리면 어댑터가 등록돼 있어도 기능은 다시 닫힌다.
            Set.of(
                    BrokerProviderCapability.CONNECTION_VERIFICATION,
                    BrokerProviderCapability.HOLDING_SNAPSHOT,
                    BrokerProviderCapability.TRANSACTION_HISTORY_IMPORT
            ),
            Set.of(Market.US, Market.KR),
            // 원장에 쓸 수 있는 시장은 US 하나다. Trade Guide의 매매 원장에는 통화 필드가 없고
            // 모든 금액을 단일 통화로 읽는다. KR 보유 종목은 스냅샷·비교로 보여 줄 수는 있지만
            // 개시 잔고로 반영하는 순간 원화 금액이 달러 원장에 섞이고, 평가 조회까지 함께
            // 무너진다. 다중 통화 원장이 도입되기 전에는 이 집합을 넓히지 않는다.
            Set.of(Market.US),
            List.of(
                    new BrokerCredentialField(
                            "clientId",
                            "Client ID",
                            BrokerCredentialFieldType.TEXT,
                            true,
                            "발급받은 Client ID를 입력하세요.",
                            "토스증권 오픈API에서 발급한 client id입니다. 계좌번호나 로그인 아이디가 아닙니다."
                    ),
                    new BrokerCredentialField(
                            "clientSecret",
                            "Client Secret",
                            BrokerCredentialFieldType.SECRET,
                            true,
                            "발급받은 Client Secret을 입력하세요.",
                            "저장 후에는 다시 조회할 수 없습니다. 값이 바뀌면 연결을 다시 등록하세요."
                    )
            )
    );

    private final String displayName;
    private final Set<BrokerProviderCapability> supportedCapabilities;
    private final Set<Market> supportedMarkets;
    private final Set<Market> ledgerWritableMarkets;
    private final List<BrokerCredentialField> credentialFields;

    BrokerProvider(
            String displayName,
            Set<BrokerProviderCapability> supportedCapabilities,
            Set<Market> supportedMarkets,
            Set<Market> ledgerWritableMarkets,
            List<BrokerCredentialField> credentialFields
    ) {
        this.displayName = displayName;
        this.supportedCapabilities = supportedCapabilities;
        this.supportedMarkets = supportedMarkets;
        this.ledgerWritableMarkets = ledgerWritableMarkets;
        this.credentialFields = credentialFields;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** 이 서비스가 현재 실제로 구현해 제공하는 기능 집합이다. */
    public Set<BrokerProviderCapability> getSupportedCapabilities() {
        return supportedCapabilities;
    }

    /**
     * 이 서비스가 해당 제공자로부터 현재 실제로 처리할 수 있는 시장이다.
     * 증권사가 취급하는 전체 상품 범위에 대한 주장이 아니라,
     * 보유 종목 어댑터가 {@link Market}으로 변환할 수 있는 시장을 뜻한다
     * ({@link com.tradeguide.service.broker.TossSecuritiesHoldingsProvider} 참고).
     * 그 외 시장은 스냅샷에서 제외되고 건수만 보고된다.
     */
    public Set<Market> getSupportedMarkets() {
        return supportedMarkets;
    }

    /**
     * 이 제공자에서 가져온 데이터를 매매 원장에 쓸 수 있는 시장이다.
     * 항상 {@link #getSupportedMarkets()}의 부분집합이며, 보통 그보다 좁다.
     *
     * <p>"조회할 수 있는 시장"과 "원장에 쓸 수 있는 시장"은 다른 개념이다. 전자는 어댑터가
     * 증권사 응답을 {@link Market}으로 변환할 수 있는지에 대한 사실이고, 후자는 Trade Guide의
     * 원장 모델이 그 시장의 거래를 정확히 표현할 수 있는지에 대한 사실이다. 둘을 같은 값으로
     * 쓰면 조회만 되면 원장에도 쓸 수 있다는 잘못된 결론이 나온다.
     *
     * <p>판정은 서비스마다 흩어 놓지 않고
     * {@link com.tradeguide.service.broker.BrokerProviderRegistry#requireLedgerWritableMarket}과
     * {@link com.tradeguide.service.broker.BrokerProviderRegistry#isLedgerWritableMarket}이
     * 한곳에서 담당한다. 원장에 쓰는 경로는 예외 없이 그 관문을 지난다.
     */
    public Set<Market> getLedgerWritableMarkets() {
        return ledgerWritableMarkets;
    }

    /**
     * 연결 생성 시 이 제공자가 요구하는 자격 증명 입력 칸 목록이다.
     * 각 {@link BrokerCredentialField#key()}는 연결 생성 요청 본문의 속성 이름과 같다.
     * 연결 이름({@code displayName})처럼 제공자와 무관한 공통 입력은 포함하지 않는다.
     */
    public List<BrokerCredentialField> getCredentialFields() {
        return credentialFields;
    }
}
