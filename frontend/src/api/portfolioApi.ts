import {getJsonResponse} from "./apiError";
import type {Portfolio} from "../types/portfolio";

export async function getPortfolios(memberId: number): Promise<Portfolio[]> {
    const response = await fetch(`/api/members/${memberId}/portfolios`);
    return getJsonResponse<Portfolio[]>(response, "포트폴리오 목록을 불러오지 못했습니다.");
}
