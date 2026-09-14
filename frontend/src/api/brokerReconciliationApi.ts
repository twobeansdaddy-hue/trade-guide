import {getJsonResponse} from "./apiError";
import type {BrokerHistoryPage} from "../types/portfolioBroker";
import type {
    BrokerReconciliationRun,
    BrokerReconciliationRunDetail,
} from "../types/brokerReconciliation";

const base = (memberId: number, portfolioId: number) =>
    `/api/members/${memberId}/portfolios/${portfolioId}/broker-reconciliations`;

export const createBrokerReconciliationRun = async (
    memberId: number,
    portfolioId: number,
): Promise<BrokerReconciliationRunDetail> =>
    getJsonResponse<BrokerReconciliationRunDetail>(
        await fetch(base(memberId, portfolioId), {
            method: "POST",
        }),
        "원장 정합성 점검을 실행하지 못했습니다.",
    );

export const getBrokerReconciliationRuns = async (
    memberId: number,
    portfolioId: number,
    page = 0,
    size = 10,
): Promise<BrokerHistoryPage<BrokerReconciliationRun>> =>
    getJsonResponse<BrokerHistoryPage<BrokerReconciliationRun>>(
        await fetch(`${base(memberId, portfolioId)}?page=${page}&size=${size}`),
        "정합성 점검 이력을 불러오지 못했습니다.",
    );

export const getBrokerReconciliationRunDetail = async (
    memberId: number,
    portfolioId: number,
    runId: number,
): Promise<BrokerReconciliationRunDetail> =>
    getJsonResponse<BrokerReconciliationRunDetail>(
        await fetch(`${base(memberId, portfolioId)}/${runId}`),
        "정합성 점검 상세 결과를 불러오지 못했습니다.",
    );
