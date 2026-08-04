package com.tradeguide.domain.market;

import com.tradeguide.domain.trade.Market;

import java.math.BigDecimal;
import java.time.LocalDate;

public class MarketCandle {

    private final Market market;
    private final String ticker;
    private final LocalDate tradingDate;
    private final BigDecimal open;
    private final BigDecimal high;
    private final BigDecimal low;
    private final BigDecimal close;
    private final long volume;

    public MarketCandle(
            Market market,
            String ticker,
            LocalDate tradingDate,
            BigDecimal open,
            BigDecimal high,
            BigDecimal low,
            BigDecimal close,
            long volume) {
        this.market = market;
        this.ticker = ticker;
        this.tradingDate = tradingDate;
        this.open = open;
        this.high = high;
        this.low = low;
        this.close = close;
        this.volume = volume;
    }

    public Market getMarket() {
        return market;
    }

    public String getTicker() {
        return ticker;
    }

    public LocalDate getTradingDate() {
        return tradingDate;
    }

    public BigDecimal getOpen() {
        return open;
    }

    public BigDecimal getHigh() {
        return high;
    }

    public BigDecimal getLow() {
        return low;
    }

    public BigDecimal getClose() {
        return close;
    }

    public long getVolume() {
        return volume;
    }
}
