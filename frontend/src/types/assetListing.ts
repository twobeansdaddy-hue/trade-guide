import type {Market} from "./tradeTransaction";

export type AssetListing = {
    market: Market
    ticker: string
    displayName: string
}
