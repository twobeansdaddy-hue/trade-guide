import type {Market} from "./tradeTransaction";

export type AssetListing = {
    id: number
    market: Market
    ticker: string
    displayName: string
}
