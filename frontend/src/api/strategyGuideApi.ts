import {getJsonResponse} from "./apiError";
import type {StrategyGuideBatch} from "../types/strategyGuide";

async function getStrategyGuideBatch(path: string, fallbackMessage: string): Promise<StrategyGuideBatch> {
    const response = await fetch(path);

    return getJsonResponse<StrategyGuideBatch>(response, fallbackMessage);
}

export function getPortfolioStrategyGuides(memberId: number, portfolioId: number) {
    return getStrategyGuideBatch(
        `/api/members/${memberId}/portfolios/${portfolioId}/strategy-guides`,
        "보유 종목 전략 가이드를 불러오지 못했습니다.",
    );
}

export function getCandidateStrategyGuides(memberId: number, portfolioId: number) {
    return getStrategyGuideBatch(
        `/api/members/${memberId}/portfolios/${portfolioId}/candidate-strategy-guides`,
        "후보 전략 가이드를 불러오지 못했습니다.",
    );
}
