import {getJsonResponse, parseApiError} from "./apiError";
import type {
    PortfolioAssetRiskOverride,
    PortfolioAssetRiskOverrideUpsertRequest,
} from "../types/portfolioAssetRiskOverride";

export async function getPortfolioAssetRiskOverrides(
    memberId: number,
    portfolioId: number,
): Promise<PortfolioAssetRiskOverride[]> {
    const response = await fetch(
        `/api/members/${memberId}/portfolios/${portfolioId}/asset-risk-overrides`,
    );

    return getJsonResponse<PortfolioAssetRiskOverride[]>(
        response,
        "보유 종목 손절 재정의 설정을 불러오지 못했습니다.",
    );
}

export async function upsertPortfolioAssetRiskOverride(
    memberId: number,
    portfolioId: number,
    market: string,
    ticker: string,
    stopLossRatio: number,
): Promise<PortfolioAssetRiskOverride> {
    const request: PortfolioAssetRiskOverrideUpsertRequest = {stopLossRatio};

    const response = await fetch(
        `/api/members/${memberId}/portfolios/${portfolioId}/assets/${market}/${ticker}/risk-override`,
        {
            method: "PUT",
            headers: {
                "Content-Type": "application/json",
            },
            body: JSON.stringify(request),
        },
    );

    return getJsonResponse<PortfolioAssetRiskOverride>(
        response,
        "종목별 손절 비율 재정의를 저장하지 못했습니다.",
    );
}

export async function deletePortfolioAssetRiskOverride(
    memberId: number,
    portfolioId: number,
    market: string,
    ticker: string,
): Promise<void> {
    const response = await fetch(
        `/api/members/${memberId}/portfolios/${portfolioId}/assets/${market}/${ticker}/risk-override`,
        {
            method: "DELETE",
        },
    );

    if (!response.ok) {
        throw await parseApiError(
            response,
            "종목별 손절 비율 재정의를 삭제하지 못했습니다.",
        );
    }
}
