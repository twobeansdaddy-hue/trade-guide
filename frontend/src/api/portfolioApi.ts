import {getJsonResponse} from "./apiError";
import type {Portfolio} from "../types/portfolio";

export async function getPortfolios(memberId: number): Promise<Portfolio[]> {
    const response = await fetch(`/api/members/${memberId}/portfolios`);
    return getJsonResponse<Portfolio[]>(response, "포트폴리오 목록을 불러오지 못했습니다.");
}

export async function createPortfolio(memberId: number, name: string): Promise<Portfolio> {
    const response = await fetch(`/api/members/${memberId}/portfolios`, {
        method: "POST",
        headers: {"Content-Type": "application/json"},
        body: JSON.stringify({name}),
    });

    return getJsonResponse<Portfolio>(response, "포트폴리오를 만들지 못했습니다.");
}
