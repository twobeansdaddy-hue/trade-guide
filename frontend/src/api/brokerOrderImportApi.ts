import {getJsonResponse} from "./apiError";
import type {BrokerHistoryPage} from "../types/portfolioBroker";
import type {
    BrokerOrderApprovalResponse,
    BrokerOrderImportCreateRequest,
    BrokerOrderImportItem,
    BrokerOrderImportItemOverrideRequest,
    BrokerOrderImportItemOverrideResponse,
    BrokerOrderImportRun,
    BrokerOrderImportRunDetail,
} from "../types/brokerOrderImport";

const base = (memberId: number, portfolioId: number) =>
    `/api/members/${memberId}/portfolios/${portfolioId}/broker-order-imports`;

export const createBrokerOrderImportPreview = async (
    memberId: number,
    portfolioId: number,
    request: BrokerOrderImportCreateRequest,
): Promise<BrokerOrderImportRunDetail> =>
    getJsonResponse<BrokerOrderImportRunDetail>(
        await fetch(base(memberId, portfolioId), {
            method: "POST",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify(request),
        }),
        "주문 이력 미리보기를 생성하지 못했습니다.",
    );
export const getBrokerOrderImportRuns = async (
    memberId: number,
    portfolioId: number,
    page = 0,
    size = 20,
): Promise<BrokerHistoryPage<BrokerOrderImportRun>> =>
    getJsonResponse<BrokerHistoryPage<BrokerOrderImportRun>>(
        await fetch(`${base(memberId, portfolioId)}?page=${page}&size=${size}`),
        "주문 이력 가져오기 목록을 불러오지 못했습니다.",
    );

export const getBrokerOrderImportRunDetail = async (
    memberId: number,
    portfolioId: number,
    runId: number,
): Promise<BrokerOrderImportRunDetail> =>
    getJsonResponse<BrokerOrderImportRunDetail>(
        await fetch(`${base(memberId, portfolioId)}/${runId}`),
        "주문 이력 가져오기 상세를 불러오지 못했습니다.",
    );

export const approveBrokerOrderImportRun = async (
    memberId: number,
    portfolioId: number,
    runId: number,
    acknowledgeIncompleteCoverage = false,
): Promise<BrokerOrderApprovalResponse> => {
    const query = acknowledgeIncompleteCoverage ? "?acknowledgeIncompleteCoverage=true" : "";
    return getJsonResponse<BrokerOrderApprovalResponse>(
        await fetch(`${base(memberId, portfolioId)}/${runId}/approval${query}`, {
            method: "POST",
        }),
        "주문 이력 반영 승인에 실패했습니다.",
    );
};

export const getBrokerOrderImportRunItems = async (
    memberId: number,
    portfolioId: number,
    runId: number,
    page = 0,
    size = 20,
    status?: string,
    symbol?: string,
): Promise<BrokerHistoryPage<BrokerOrderImportItem>> => {
    const params = new URLSearchParams();
    params.set("page", String(page));
    params.set("size", String(size));
    if (status && status.trim().length > 0) {
        params.set("status", status.trim());
    }
    if (symbol && symbol.trim().length > 0) {
        params.set("symbol", symbol.trim());
    }
    return getJsonResponse<BrokerHistoryPage<BrokerOrderImportItem>>(
        await fetch(`${base(memberId, portfolioId)}/${runId}/items?${params.toString()}`),
        "주문 상세 항목을 불러오지 못했습니다.",
    );
};

export const revokeBrokerOrderImportApproval = async (
    memberId: number,
    portfolioId: number,
    runId: number,
): Promise<void> => {
    const response = await fetch(`${base(memberId, portfolioId)}/${runId}/approval`, {
        method: "DELETE",
    });
    if (!response.ok) {
        await getJsonResponse<void>(response, "주문 이력 반영 승인을 취소하지 못했습니다.");
    }
};

export const overrideBrokerOrderImportItem = async (
    memberId: number,
    portfolioId: number,
    runId: number,
    itemId: number,
    request: BrokerOrderImportItemOverrideRequest,
): Promise<BrokerOrderImportItemOverrideResponse> =>
    getJsonResponse<BrokerOrderImportItemOverrideResponse>(
        await fetch(`${base(memberId, portfolioId)}/${runId}/items/${itemId}/override`, {
            method: "POST",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify(request),
        }),
        "주문 항목 재판정에 실패했습니다.",
    );

export const getBrokerOrderImportItemOverrides = async (
    memberId: number,
    portfolioId: number,
    runId: number,
    page = 0,
    size = 20,
): Promise<BrokerHistoryPage<BrokerOrderImportItemOverrideResponse>> =>
    getJsonResponse<BrokerHistoryPage<BrokerOrderImportItemOverrideResponse>>(
        await fetch(`${base(memberId, portfolioId)}/${runId}/item-overrides?page=${page}&size=${size}`),
        "재판정 감사 이력을 불러오지 못했습니다.",
    );
