export type BacktestTradeType = "BUY" | "SELL";

export type BacktestTradeEvent = {
    tradingDate: string;
    type: BacktestTradeType;
    price: number;
    quantity: number;
    cashAfter: number;
    sharesAfter: number;
    portfolioValueAfter: number;
};

export type BacktestAssumptions = {
    feeRate: number;
    slippageRate: number;
};

export type BacktestResult = {
    periodStart: string;
    periodEnd: string;
    startingPortfolioValue: number;
    endingPortfolioValue: number;
    cumulativeReturnRate: number;
    maxDrawdownRate: number;
    tradeCount: number;
    trades: BacktestTradeEvent[];
    assumptions: BacktestAssumptions;
};

export type PortfolioAssetBacktest = {
    market: string;
    ticker: string;
    dataAsOfDate: string;
    result: BacktestResult;
};
