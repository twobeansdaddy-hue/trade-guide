package com.tradeguide.dto.market;

import com.tradeguide.domain.market.MarketDataProvider;
import jakarta.validation.constraints.NotNull;

public class PortfolioMarketDataPreferenceUpdateRequest {

    @NotNull(message = "시장 데이터 제공자는 필수입니다.")
    private MarketDataProvider provider;

    public MarketDataProvider getProvider() {
        return provider;
    }
}
