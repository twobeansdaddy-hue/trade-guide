package com.tradeguide.domain.backtest;

import com.tradeguide.domain.trade.Market;

import java.time.LocalDate;

public class PortfolioAssetBacktest {

    private final Market market;
    private final String ticker;
    private final LocalDate dataAsOfDate;
    private final BacktestResult result;

    public PortfolioAssetBacktest(
            Market market,
            String ticker,
            LocalDate dataAsOfDate,
            BacktestResult result
    ) {
        this.market = market;
        this.ticker = ticker;
        this.dataAsOfDate = dataAsOfDate;
        this.result = result;
    }

    public Market getMarket() {
        return market;
    }

    public String getTicker() {
        return ticker;
    }

    public LocalDate getDataAsOfDate() {
        return dataAsOfDate;
    }

    public BacktestResult getResult() {
        return result;
    }
}
