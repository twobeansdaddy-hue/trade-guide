package com.tradeguide.dto.broker;

import com.tradeguide.domain.broker.BrokerProvider;
import com.tradeguide.domain.broker.BrokerProviderCapability;
import com.tradeguide.domain.trade.Market;

import java.util.List;
import java.util.Set;

/**
 * 안전한(자격 증명이나 내부 구현을 노출하지 않는) 증권사 제공자 카탈로그 응답이다.
 * 연결 화면은 이 응답만으로 제공자 선택지, 지원 기능, 입력 폼을 구성할 수 있어야 하며
 * 제공자별 조건 분기를 코드에 두지 않는다.
 *
 * <p>이 응답은 저장된 자격 증명을 노출하지 않고, 저장된 값을 되돌려 달라고 요청하지도 않는다.
 * {@link #getCredentialFields()}는 입력받을 항목의 명세일 뿐이다.
 */
public class BrokerProviderCapabilityResponse {

    private final BrokerProvider provider;
    private final String displayName;
    private final boolean connectable;
    private final List<BrokerProviderCapability> supportedCapabilities;
    private final List<BrokerProviderCapability> availableCapabilities;
    private final List<Market> supportedMarkets;
    private final List<Market> ledgerWritableMarkets;
    private final List<BrokerCredentialFieldResponse> credentialFields;

    public BrokerProviderCapabilityResponse(
            BrokerProvider provider,
            boolean connectable,
            Set<BrokerProviderCapability> availableCapabilities
    ) {
        this.provider = provider;
        this.displayName = provider.getDisplayName();
        this.connectable = connectable;
        this.supportedCapabilities = provider.getSupportedCapabilities().stream().sorted().toList();
        this.availableCapabilities = availableCapabilities.stream().sorted().toList();
        this.supportedMarkets = provider.getSupportedMarkets().stream().sorted().toList();
        this.ledgerWritableMarkets = provider.getLedgerWritableMarkets().stream().sorted().toList();
        this.credentialFields = provider.getCredentialFields().stream()
                .map(BrokerCredentialFieldResponse::new)
                .toList();
    }

    public BrokerProvider getProvider() {
        return provider;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** 이 서비스 인스턴스에 실제 연결 검증 구현체가 연결되어 있는지 여부다. */
    public boolean isConnectable() {
        return connectable;
    }

    /** 이 제공자에 대해 서비스가 구현했다고 선언한 기능이다. 배포 구성과 무관한 선언 값이다. */
    public List<BrokerProviderCapability> getSupportedCapabilities() {
        return supportedCapabilities;
    }

    /**
     * 이 서비스 인스턴스에서 실제로 사용할 수 있는 기능이다.
     * {@link #getSupportedCapabilities()}와 어댑터 등록의 교집합이며 항상 그 부분집합이다.
     * 화면은 버튼 활성화를 이 값으로 판단한다. 선언만 보고 판단하면 어댑터가 없는 배포에서
     * 눌러야 503을 받는 버튼이 열린다.
     */
    public List<BrokerProviderCapability> getAvailableCapabilities() {
        return availableCapabilities;
    }

    /** 이 서비스가 해당 제공자로부터 현재 처리할 수 있는 시장이다. */
    public List<Market> getSupportedMarkets() {
        return supportedMarkets;
    }

    /**
     * 이 제공자에서 가져온 데이터를 매매 원장에 쓸 수 있는 시장이다.
     * 항상 {@link #getSupportedMarkets()}의 부분집합이며 보통 그보다 좁다.
     * 조회할 수 있다는 사실과 원장에 쓸 수 있다는 사실은 다른 이야기다.
     */
    public List<Market> getLedgerWritableMarkets() {
        return ledgerWritableMarkets;
    }

    /**
     * 연결 생성 시 입력받아야 하는 자격 증명 항목의 명세다.
     * 각 항목의 {@code key}는 연결 생성 요청 본문의 속성 이름과 같다.
     * 연결 이름({@code displayName})처럼 제공자와 무관한 공통 입력은 포함하지 않는다.
     */
    public List<BrokerCredentialFieldResponse> getCredentialFields() {
        return credentialFields;
    }
}
