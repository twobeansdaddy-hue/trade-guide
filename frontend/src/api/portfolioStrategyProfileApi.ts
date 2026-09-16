import {getJsonResponse, parseApiError} from "./apiError";
import type {
    InvestmentTrack,
    PortfolioAssetStrategyProfile,
    PortfolioAssetStrategyProfileUpsertRequest,
} from "../types/portfolioStrategyProfile";

export async function getHeldAssetStrategyProfiles(
    memberId: number,
    portfolioId: number,
): Promise<PortfolioAssetStrategyProfile[]> {
    const response = await fetch(
        `/api/members/${memberId}/portfolios/${portfolioId}/strategy-profiles`,
    );

    return getJsonResponse<PortfolioAssetStrategyProfile[]>(
        response,
        "보유 종목 투자 트랙 및 손절 기준 설정을 불러오지 못했습니다.",
    );
}

export async function upsertHeldAssetStrategyProfile(
    memberId: number,
    portfolioId: number,
    market: string,
    ticker: string,
    investmentTrack: InvestmentTrack,
): Promise<PortfolioAssetStrategyProfile> {
    const request: PortfolioAssetStrategyProfileUpsertRequest = {
        investmentTrack,
    };

    const response = await fetch(
        `/api/members/${memberId}/portfolios/${portfolioId}/strategy-profiles/${market}/${ticker}`,
        {
            method: "PUT",
            headers: {
                "Content-Type": "application/json",
            },
            body: JSON.stringify(request),
        },
    );

    return getJsonResponse<PortfolioAssetStrategyProfile>(
        response,
        "투자 트랙 재정의를 저장하지 못했습니다.",
    );
}

export async function deleteHeldAssetStrategyProfile(
    memberId: number,
    portfolioId: number,
    market: string,
    ticker: string,
): Promise<void> {
    const response = await fetch(
        `/api/members/${memberId}/portfolios/${portfolioId}/strategy-profiles/${market}/${ticker}`,
        {
            method: "DELETE",
        },
    );

    if (!response.ok) {
        throw await parseApiError(
            response,
            "투자 트랙 재정의를 삭제하지 못했습니다.",
        );
    }
}
