import type { PortfolioRiskAlert } from "../types/portfolioRisk";

export async function getPortfolioRiskAlerts(
    memberId: number,
    portfolioId: number,
): Promise<PortfolioRiskAlert[]> {
    const response = await fetch(`/api/members/${memberId}/portfolios/${portfolioId}/risk-alerts`,)

    if (!response.ok) {
        throw new Error(
            `위험 경고를 불러오지 못했습니다. (HTTP ${response.status})`,
        )
    }

    return (await response.json()) as PortfolioRiskAlert[]
}