import {getJsonResponse} from "./apiError";
import type {PremarketGuide} from "../types/premarketGuide";

export function getTodayPremarketGuide(memberId: number, portfolioId: number) {
    return getPremarketGuide(
        `/api/members/${memberId}/portfolios/${portfolioId}/premarket-guide/today`,
        "오늘 장전 가이드를 불러오지 못했습니다.",
    );
}

export async function generateTodayPremarketGuide(
    memberId: number,
    portfolioId: number,
    force = false,
): Promise<PremarketGuide> {
    const query = force ? "?force=true" : "";
    const response = await fetch(
        `/api/members/${memberId}/portfolios/${portfolioId}/premarket-guide/today${query}`,
        {method: "POST"},
    );

    return getJsonResponse<PremarketGuide>(response, "오늘 장전 가이드를 생성하지 못했습니다.");
}

async function getPremarketGuide(path: string, fallbackMessage: string): Promise<PremarketGuide> {
    const response = await fetch(path);
    return getJsonResponse<PremarketGuide>(response, fallbackMessage);
}
