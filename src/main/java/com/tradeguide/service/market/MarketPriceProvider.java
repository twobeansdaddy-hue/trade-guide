package com.tradeguide.service.market;

import com.tradeguide.domain.market.MarketPrice;
import com.tradeguide.domain.trade.Market;

public interface MarketPriceProvider {
    MarketPrice getCurrentPrice(Market market, String ticker);

}
