import type {PortfolioValuation} from "../types/portfolioValuation";
import {getJsonResponse} from "./apiError";

export async function getPortfolioValuation(
    memberId: number,
    portfolioId: number,
): Promise<PortfolioValuation> {
    const response = await fetch(
        `/api/members/${memberId}/portfolios/${portfolioId}/valuation`,
    );

    return getJsonResponse<PortfolioValuation>(
        response,
        "포트폴리오 평가를 불러오지 못했습니다.",
    );
}
