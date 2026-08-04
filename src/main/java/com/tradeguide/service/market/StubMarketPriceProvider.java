package com.tradeguide.service.market;

import com.tradeguide.domain.market.MarketPrice;
import com.tradeguide.domain.trade.Market;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;

@Service
public class StubMarketPriceProvider implements MarketPriceProvider {

    private static final Map<String, BigDecimal> PRICES = Map.of(
            "US:AAPL", new BigDecimal("210.50"),
            "US:MSFT", new BigDecimal("450.25")
    );

    @Override
    public MarketPrice getCurrentPrice(Market market, String ticker) {
        String normalizedTicker = ticker.toUpperCase(Locale.ROOT);
        BigDecimal currentPrice = PRICES.get(market.name() + ":" + normalizedTicker);

        if (currentPrice == null) {
            throw new IllegalArgumentException("현재가를 찾을 수 없습니다.");
        }

        return new MarketPrice(
                market,
                normalizedTicker,
                currentPrice,
                Instant.now()
        );
    }
}