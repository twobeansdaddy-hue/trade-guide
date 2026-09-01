import {getJsonResponse} from "./apiError";
import type {AssetListing} from "../types/assetListing";
import type {Market} from "../types/tradeTransaction";

export async function searchAssetListings(market: Market, query: string): Promise<AssetListing[]> {
    const parameters = new URLSearchParams({market, query});
    const response = await fetch(`/api/assets?${parameters}`);
    return getJsonResponse<AssetListing[]>(response, "종목 검색 결과를 불러오지 못했습니다.");
}
