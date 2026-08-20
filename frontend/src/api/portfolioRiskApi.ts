import type { PortfolioRiskAlert } from "../types/portfolioRisk";

type ApiErrorResponse = {
    message?: string;
};

async function getErrorMessage(response: Response): Promise<string> {
    try {
        const errorResponse = (await response.json()) as ApiErrorResponse;

        if (errorResponse.message) {
            return errorResponse.message;
        }
    } catch {
        // JSON 오류 응답이 아닌 경우 기본 메시지를 사용한다.
    }

    return `위험 경고를 불러오지 못했습니다. (HTTP ${response.status})`;
}

export async function getPortfolioRiskAlerts(
    memberId: number,
    portfolioId: number,
): Promise<PortfolioRiskAlert[]> {
    const response = await fetch(
        `/api/members/${memberId}/portfolios/${portfolioId}/risk-alerts`,
    );

    if (!response.ok) {
        throw new Error(await getErrorMessage(response));
    }

    return (await response.json()) as PortfolioRiskAlert[];
}