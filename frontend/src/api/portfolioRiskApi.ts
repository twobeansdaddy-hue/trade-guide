import type {PortfolioRiskAlert} from "../types/portfolioRisk";
import type {PortfolioRiskPolicy} from "../types/portfolioRiskPolicy";
import {getErrorMessage} from "./apiError";

export async function getPortfolioRiskAlerts(
    memberId: number,
    portfolioId: number,
): Promise<PortfolioRiskAlert[]> {
    const response = await fetch(
        `/api/members/${memberId}/portfolios/${portfolioId}/risk-alerts`,
    );

    if (!response.ok) {
        throw new Error(await getErrorMessage(response, "위험 경고를 불러오지 못했습니다."));
    }

    return (await response.json()) as PortfolioRiskAlert[];
}

export async function getPortfolioRiskPolicy(
    memberId: number,
    portfolioId: number,
): Promise<PortfolioRiskPolicy> {
    const response = await fetch(
        `/api/members/${memberId}/portfolios/${portfolioId}/risk-policy`,
    );

    if (!response.ok) {
        throw new Error(await getErrorMessage(response, "위험 한도를 불러오지 못했습니다."));
    }

    return (await response.json()) as PortfolioRiskPolicy;
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

    if (!response.ok) {
        throw new Error(await getErrorMessage(response, "위험 한도를 저장하지 못했습니다."));
    }

    return (await response.json()) as PortfolioRiskPolicy;
}