export interface PortfolioRiskAlert {
    market: string
    ticker: string
    exposureRate: number
    maxExposureRate: number
    message: string
}