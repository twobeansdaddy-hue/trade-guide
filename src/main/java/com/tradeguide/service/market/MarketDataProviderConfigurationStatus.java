package com.tradeguide.service.market;

import com.tradeguide.domain.market.MarketDataProvider;
import com.tradeguide.exception.MarketDataProviderNotConfiguredException;
import com.tradeguide.service.broker.BrokerCredentialCipher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 각 시장 데이터 제공자의 서버 측 설정 전제 조건이 준비됐는지 판단한다.
 *
 * <p>여기서 판단하는 것은 서버 설정의 존재 여부뿐이다. 비밀값 자체를 노출하거나
 * 반환하지 않으며, 외부 제공자에 연결이 가능한지 확인하지도 않는다.
 * 회원별 증권사 연결 필요 여부는 {@link MarketDataProvider#requiresBrokerConnection()}이
 * 별도로 표현한다.
 *
 * <p>Toss Securities의 전제 조건은 {@link BrokerCredentialCipher#isConfigured()}에
 * 위임한다. 이는 레거시 단일 키({@code BROKER_CREDENTIAL_ENCRYPTION_KEY})와
 * 키링({@code BROKER_CREDENTIAL_ENCRYPTION_KEY_V1}, {@code _V2}, ...) 및 현재 버전
 * 규칙을 실제로 적용하는 유일한 지점이므로, 이 클래스가 그 규칙을 별도로
 * 재구현하면 두 판단이 어긋날 수 있다.
 */
@Component
public class MarketDataProviderConfigurationStatus {

    private final boolean twelveDataApiKeyConfigured;
    private final BrokerCredentialCipher brokerCredentialCipher;

    public MarketDataProviderConfigurationStatus(
            @Value("${twelve-data.api-key:}") String twelveDataApiKey,
            BrokerCredentialCipher brokerCredentialCipher
    ) {
        this.twelveDataApiKeyConfigured = isPresent(twelveDataApiKey);
        this.brokerCredentialCipher = brokerCredentialCipher;
    }

    /**
     * 제공자를 사용하기 위한 서버 설정이 준비됐는지 반환한다.
     *
     * <p>{@code YAHOO_FINANCE}는 이 서비스에서 사용하는 서버 측 자격 증명이 없으므로
     * 항상 준비된 상태로 본다. 사용 가능 여부는 {@link MarketDataProvider#isSelectable()}이
     * 별도로 판단한다.
     */
    public boolean isConfigured(MarketDataProvider provider) {
        if (provider == null) {
            return false;
        }

        return switch (provider) {
            case TWELVE_DATA -> twelveDataApiKeyConfigured;
            case TOSS_SECURITIES -> brokerCredentialCipher.isConfigured();
            case YAHOO_FINANCE -> true;
        };
    }

    /**
     * 설정이 준비되지 않았으면 외부 호출 전에 차단한다.
     *
     * @throws MarketDataProviderNotConfiguredException 서버 설정 전제 조건이 없을 때
     */
    public void requireConfigured(MarketDataProvider provider) {
        if (isConfigured(provider)) {
            return;
        }

        throw new MarketDataProviderNotConfiguredException(
                provider,
                missingPrerequisiteMessage(provider)
        );
    }

    private String missingPrerequisiteMessage(MarketDataProvider provider) {
        if (provider == null) {
            return "시장 데이터 제공자가 지정되지 않았습니다.";
        }

        return switch (provider) {
            case TWELVE_DATA -> provider.getDisplayName()
                    + " 시장 데이터 API 키가 서버에 설정되지 않았습니다."
                    + " 서버 환경 변수 TWELVE_DATA_API_KEY를 설정한 뒤 다시 시도해 주세요.";
            case TOSS_SECURITIES -> provider.getDisplayName()
                    + " 시장 데이터를 사용하려면 서버 환경 변수 BROKER_CREDENTIAL_ENCRYPTION_KEY"
                    + " (또는 BROKER_CREDENTIAL_ENCRYPTION_KEY_V1/_V2 키링과"
                    + " BROKER_CREDENTIAL_ENCRYPTION_CURRENT_VERSION) 설정이 필요합니다.";
            case YAHOO_FINANCE -> provider.getDisplayName()
                    + " 시장 데이터는 현재 사용할 수 없습니다.";
        };
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }
}
