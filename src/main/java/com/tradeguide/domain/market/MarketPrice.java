package com.tradeguide.domain.market;

import com.tradeguide.domain.trade.Currency;
import com.tradeguide.domain.trade.Market;

import java.math.BigDecimal;
import java.time.Instant;

public class MarketPrice {

    private final Market market;
    private final String ticker;
    private final BigDecimal currentPrice;
    private final Currency currency;
    private final Instant capturedAt;

    /**
     * {@code currency}는 시세 제공자가 응답에서 실제로 밝힌 통화다. {@code market.getCurrency()}와
     * 일치해야 하며, 불일치하면 제공자 응답이 기대와 다르다는 뜻이므로 조용히 무시하지 않고
     * {@link IllegalArgumentException}을 던진다 - KRW 가격을 USD로 잘못 합산하는 사고를 막는다.
     */
    public MarketPrice(
            Market market,
            String ticker,
            BigDecimal currentPrice,
            Currency currency,
            Instant capturedAt
    ) {
        if (currency != market.getCurrency()) {
            throw new IllegalArgumentException(
                    "시세 통화(" + currency + ")가 시장(" + market + ")의 기대 통화(" + market.getCurrency() + ")와 다릅니다."
            );
        }
        this.market = market;
        this.ticker = ticker;
        this.currentPrice = currentPrice;
        this.currency = currency;
        this.capturedAt = capturedAt;
    }

    public Market getMarket() {
        return market;
    }

    public String getTicker() {
        return ticker;
    }

    public BigDecimal getCurrentPrice() {
        return currentPrice;
    }

    public Currency getCurrency() {
        return currency;
    }

    public Instant getCapturedAt() {
        return capturedAt;
    }
}