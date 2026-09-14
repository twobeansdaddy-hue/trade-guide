export interface PortfolioRiskPolicy {
    maxLossPerTradeRatio: number
    maxSingleAssetExposureRatio: number
    stopLossRatio: number | null
}
