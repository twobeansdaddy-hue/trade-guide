import {getJsonResponse} from "./apiError";
import type {
    MarketDataProvider,
    MarketDataProviderCapability,
    PortfolioMarketDataPreference,
} from "../types/marketDataProvider";

export async function getMarketDataProviders(
    memberId: number,
    portfolioId: number,
): Promise<MarketDataProviderCapability[]> {
    const response = await fetch(
        `/api/members/${memberId}/portfolios/${portfolioId}/market-data-providers`,
    );

    return getJsonResponse<MarketDataProviderCapability[]>(
        response,
        "시장 데이터 제공자 목록을 불러오지 못했습니다.",
    );
}

export async function getPortfolioMarketDataPreference(
    memberId: number,
    portfolioId: number,
): Promise<PortfolioMarketDataPreference> {
    const response = await fetch(
        `/api/members/${memberId}/portfolios/${portfolioId}/market-data-preference`,
    );

    return getJsonResponse<PortfolioMarketDataPreference>(
        response,
        "시장 데이터 제공자 설정을 불러오지 못했습니다.",
    );
}

export async function updatePortfolioMarketDataPreference(
    memberId: number,
    portfolioId: number,
    provider: MarketDataProvider,
): Promise<PortfolioMarketDataPreference> {
    const response = await fetch(
        `/api/members/${memberId}/portfolios/${portfolioId}/market-data-preference`,
        {
            method: "PUT",
            headers: {
                "Content-Type": "application/json",
            },
            body: JSON.stringify({provider}),
        },
    );

    return getJsonResponse<PortfolioMarketDataPreference>(
        response,
        "시장 데이터 제공자 설정을 저장하지 못했습니다.",
    );
}
