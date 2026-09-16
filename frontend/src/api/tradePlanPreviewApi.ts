import {getJsonResponse} from "./apiError";
import type {TradePlanPreviewBatch} from "../types/tradePlanPreview";

export async function getTradePlanPreview(
    memberId: number,
    portfolioId: number,
): Promise<TradePlanPreviewBatch> {
    const response = await fetch(
        `/api/members/${memberId}/portfolios/${portfolioId}/trade-plan-preview`,
    );
    return getJsonResponse<TradePlanPreviewBatch>(
        response,
        "검토용 매매 계획 초안을 불러오지 못했습니다.",
    );
}
