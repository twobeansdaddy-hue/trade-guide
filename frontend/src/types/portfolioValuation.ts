export type PortfolioValuation = {
    holdingValuations: HoldingValuation[]
    totalPurchaseAmount: number
    totalMarketValue: number
    totalUnrealizedProfitLoss: number
    totalReturnRate: number
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