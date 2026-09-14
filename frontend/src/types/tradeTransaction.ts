export type Market = "US" | "KR";

export type TradeType = "BUY" | "SELL";

export type TradeTransactionSource = "MANUAL" | "BROKER_OPENING_BALANCE" | "BROKER_ORDER_HISTORY";

export type TradeTransactionCreateRequest = {
    market: Market
    ticker: string
    tradeType: TradeType
    quantity: number
    executedPrice: number
    fee: number
    tradedAt: string
}

export type TradeTransaction = TradeTransactionCreateRequest & {
    id: number
    source: TradeTransactionSource
}
