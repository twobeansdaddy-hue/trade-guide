export type MarketDataProvider =
    | "TWELVE_DATA"
    | "TOSS_SECURITIES"
    | "YAHOO_FINANCE"

export type MarketDataProviderCapability = {
    provider: MarketDataProvider
    displayName: string
    requiresBrokerConnection: boolean
    selectable: boolean
    supportedMarkets: string[]
}

export type PortfolioMarketDataPreference = {
    priceProvider: MarketDataProvider
    candleProvider: MarketDataProvider
    assetReferenceProvider: MarketDataProvider
}
