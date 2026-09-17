export type CurrencyValuationTotals = {
    totalPurchaseAmount: number
    totalMarketValue: number
    totalUnrealizedProfitLoss: number
    totalReturnRate: number
}

export type PortfolioValuation = {
    holdingValuations: HoldingValuation[]
    totalsByCurrency: Partial<Record<"USD" | "KRW", CurrencyValuationTotals>>
}

export type HoldingValuation = {
    market: string
    ticker: string
    quantity: number
    averagePurchasePrice: number
    currentPrice: number
    purchaseAmount: number
    marketValue: number
    unrealizedProfitLoss: number
    returnRate: number

}