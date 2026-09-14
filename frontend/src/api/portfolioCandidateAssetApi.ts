import {getJsonResponse, parseApiError} from "./apiError";
import type {
    PortfolioCandidateAsset,
    PortfolioCandidateAssetCreateRequest,
} from "../types/portfolioCandidateAsset";
import type {Market} from "../types/tradeTransaction";

export async function getPortfolioCandidateAssets(
    memberId: number,
    portfolioId: number,
): Promise<PortfolioCandidateAsset[]> {
    const response = await fetch(
        `/api/members/${memberId}/portfolios/${portfolioId}/candidate-assets`,
    );

    return getJsonResponse<PortfolioCandidateAsset[]>(
        response,
        "후보 종목 목록을 불러오지 못했습니다.",
    );
}

export async function createPortfolioCandidateAsset(
    memberId: number,
    portfolioId: number,
    request: PortfolioCandidateAssetCreateRequest,
): Promise<PortfolioCandidateAsset> {
    const response = await fetch(
        `/api/members/${memberId}/portfolios/${portfolioId}/candidate-assets`,
        {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
            },
            body: JSON.stringify(request),
        },
    );

    return getJsonResponse<PortfolioCandidateAsset>(
        response,
        "후보 종목을 등록하지 못했습니다.",
    );
}

export async function deletePortfolioCandidateAsset(
    memberId: number,
    portfolioId: number,
    market: Market,
    ticker: string,
): Promise<void> {
    const response = await fetch(
        `/api/members/${memberId}/portfolios/${portfolioId}/candidate-assets/${market}/${ticker}`,
        {
            method: "DELETE",
        },
    );

    if (!response.ok) {
        throw await parseApiError(
            response,
            "후보 종목을 삭제하지 못했습니다.",
        );
    }
}
