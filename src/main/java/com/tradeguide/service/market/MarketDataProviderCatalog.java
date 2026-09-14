package com.tradeguide.service.market;

import com.tradeguide.domain.market.MarketDataProvider;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class MarketDataProviderCatalog {

    private final MarketDataProviderConfigurationStatus configurationStatus;

    public MarketDataProviderCatalog(
            MarketDataProviderConfigurationStatus configurationStatus
    ) {
        this.configurationStatus = configurationStatus;
    }

    public List<MarketDataProvider> getProviders() {
        return Arrays.asList(MarketDataProvider.values());
    }

    /**
     * 제공자를 사용하기 위한 서버 설정이 준비됐는지 반환한다. 비밀값은 노출하지 않는다.
     */
    public boolean isConfigured(MarketDataProvider provider) {
        return configurationStatus.isConfigured(provider);
    }

    public void requireSelectable(MarketDataProvider provider) {
        if (provider == null || !provider.isSelectable()) {
            throw new IllegalArgumentException("현재 선택할 수 없는 시장 데이터 제공자입니다.");
        }
    }
}
