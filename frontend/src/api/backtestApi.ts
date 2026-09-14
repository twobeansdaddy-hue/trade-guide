import {getJsonResponse} from "./apiError";
import type {PortfolioAssetBacktest} from "../types/backtest";

export async function getPortfolioAssetBacktest(
    memberId: number,
    portfolioId: number,
    market: string,
    ticker: string,
    initialCash: number,
): Promise<PortfolioAssetBacktest> {
    const params = new URLSearchParams({initialCash: initialCash.toString()});
    const path = `/api/members/${memberId}/portfolios/${portfolioId}/assets/${encodeURIComponent(market)}/${encodeURIComponent(ticker)}/backtest?${params.toString()}`;
    const response = await fetch(path);

    return getJsonResponse<PortfolioAssetBacktest>(
        response,
        `${ticker} 백테스트 결과를 불러오지 못했습니다.`,
    );
}
