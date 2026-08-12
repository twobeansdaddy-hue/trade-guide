package com.tradeguide.dto.market;

import com.tradeguide.domain.market.MarketCandle;
import com.tradeguide.domain.trade.Market;

import java.math.BigDecimal;
import java.time.LocalDate;

public class MarketCandleResponse {

    private final Market market;
    private final String ticker;
    private final LocalDate tradingDate;
    private final BigDecimal open;
    private final BigDecimal high;
    private final BigDecimal low;
    private final BigDecimal close;
    private final long volume;

    public MarketCandleResponse(
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

    public static MarketCandleResponse from(MarketCandle candle) {
        return new MarketCandleResponse(
                candle.getMarket(),
                candle.getTicker(),
                candle.getTradingDate(),
                candle.getOpen(),
                candle.getHigh(),
                candle.getLow(),
                candle.getClose(),
                candle.getVolume()
        );
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
