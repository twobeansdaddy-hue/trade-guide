import {getJsonResponse} from "./apiError";
import type {TradeTransaction, TradeTransactionCreateRequest} from "../types/tradeTransaction";

export async function createTradeTransaction(
    memberId: number,
    portfolioId: number,
    request: TradeTransactionCreateRequest,
): Promise<TradeTransaction> {
    const response = await fetch(`/api/members/${memberId}/portfolios/${portfolioId}/transactions`, {
        method: "POST",
        headers: {"Content-Type": "application/json"},
        body: JSON.stringify(request),
    });

    return getJsonResponse<TradeTransaction>(response, "매매 기록을 등록하지 못했습니다.");
}
