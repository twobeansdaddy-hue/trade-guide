package com.tradeguide.service.market;

import com.tradeguide.domain.market.MarketDataProvider;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class MarketDataProviderCatalog {

    public List<MarketDataProvider> getProviders() {
        return Arrays.asList(MarketDataProvider.values());
    }

    public void requireSelectable(MarketDataProvider provider) {
        if (provider == null || !provider.isSelectable()) {
            throw new IllegalArgumentException("현재 선택할 수 없는 시장 데이터 제공자입니다.");
        }
    }
}
