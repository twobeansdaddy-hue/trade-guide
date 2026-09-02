import type {PortfolioRiskAlert} from "../types/portfolioRisk";
import type {PortfolioExposure} from "../types/portfolioExposure";
import type {PortfolioRiskPolicy} from "../types/portfolioRiskPolicy";
import {getJsonResponse} from "./apiError";

export async function getPortfolioRiskAlerts(
    memberId: number,
    portfolioId: number,
): Promise<PortfolioRiskAlert[]> {
    const response = await fetch(
        `/api/members/${memberId}/portfolios/${portfolioId}/risk-alerts`,
    );

    return getJsonResponse<PortfolioRiskAlert[]>(
        response,
        "위험 경고를 불러오지 못했습니다.",
    );
}

export async function getPortfolioRiskPolicy(
    memberId: number,
    portfolioId: number,
): Promise<PortfolioRiskPolicy> {
    const response = await fetch(
        `/api/members/${memberId}/portfolios/${portfolioId}/risk-policy`,
    );

    return getJsonResponse<PortfolioRiskPolicy>(
        response,
        "위험 한도를 불러오지 못했습니다.",
    );
}

export async function getPortfolioExposures(
    memberId: number,
    portfolioId: number,
): Promise<PortfolioExposure[]> {
    const response = await fetch(
        `/api/members/${memberId}/portfolios/${portfolioId}/exposures`,
    );

    return getJsonResponse<PortfolioExposure[]>(
        response,
        "보유 종목 비중을 불러오지 못했습니다.",
    );
}

export async function updatePortfolioRiskPolicy(
    memberId: number,
    portfolioId: number,
    riskPolicy: PortfolioRiskPolicy,
): Promise<PortfolioRiskPolicy> {
    const response = await fetch(
        `/api/members/${memberId}/portfolios/${portfolioId}/risk-policy`,
        {
            method: "PUT",
            headers: {
                "Content-Type": "application/json",
            },
            body: JSON.stringify(riskPolicy),
        },
    );

    return getJsonResponse<PortfolioRiskPolicy>(
        response,
        "위험 한도를 저장하지 못했습니다.",
    );
}
