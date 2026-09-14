package com.tradeguide.dto.backtest;

import com.tradeguide.domain.backtest.PortfolioAssetBacktest;
import com.tradeguide.domain.trade.Market;

import java.time.LocalDate;

public class PortfolioAssetBacktestResponse {

    private final Market market;
    private final String ticker;
    private final LocalDate dataAsOfDate;
    private final BacktestResultResponse result;

    public PortfolioAssetBacktestResponse(
            Market market,
            String ticker,
            LocalDate dataAsOfDate,
            BacktestResultResponse result
    ) {
        this.market = market;
        this.ticker = ticker;
        this.dataAsOfDate = dataAsOfDate;
        this.result = result;
    }

    public static PortfolioAssetBacktestResponse from(PortfolioAssetBacktest backtest) {
        return new PortfolioAssetBacktestResponse(
                backtest.getMarket(),
                backtest.getTicker(),
                backtest.getDataAsOfDate(),
                BacktestResultResponse.from(backtest.getResult())
        );
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

    public BacktestResultResponse getResult() {
        return result;
    }
}
