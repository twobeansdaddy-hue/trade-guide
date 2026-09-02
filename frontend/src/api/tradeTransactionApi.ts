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

export async function getTradeTransactions(
    memberId: number,
    portfolioId: number,
): Promise<TradeTransaction[]> {
    const response = await fetch(
        `/api/members/${memberId}/portfolios/${portfolioId}/transactions`,
    );

    return getJsonResponse<TradeTransaction[]>(
        response,
        "매매 기록을 불러오지 못했습니다.",
    );
}

export async function deleteTradeTransaction(
    memberId: number,
    portfolioId: number,
    transactionId: number,
): Promise<void> {
    const response = await fetch(
        `/api/members/${memberId}/portfolios/${portfolioId}/transactions/${transactionId}`,
        {method: "DELETE"},
    );

    if (!response.ok) {
        await getJsonResponse<void>(response, "매매 기록을 삭제하지 못했습니다.");
    }
}
