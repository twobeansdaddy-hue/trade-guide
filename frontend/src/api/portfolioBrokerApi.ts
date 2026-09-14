import {getJsonResponse} from "./apiError";
import type {
    BrokerHistoryPage,
    BrokerHoldingImportBatchResponse,
    BrokerHoldingPreview,
    BrokerHoldingSnapshot,
    BrokerLinkCandidate,
    PortfolioBrokerHoldingAdjustment,
    PortfolioBrokerHoldingImport,
    PortfolioBrokerLink,
} from "../types/portfolioBroker";

const base = (memberId: number, portfolioId: number) => `/api/members/${memberId}/portfolios/${portfolioId}`;

export const getBrokerLinkCandidates = async (m: number, p: number) =>
    getJsonResponse<BrokerLinkCandidate[]>(
        await fetch(`${base(m, p)}/broker-link-candidates`),
        "연결 가능한 계좌를 불러오지 못했습니다.",
    );

export const getPortfolioBrokerLinks = async (m: number, p: number) =>
    getJsonResponse<PortfolioBrokerLink[]>(
        await fetch(`${base(m, p)}/broker-links`),
        "연결된 계좌를 불러오지 못했습니다.",
    );

export const linkBrokerAccount = async (m: number, p: number, c: BrokerLinkCandidate) =>
    getJsonResponse<PortfolioBrokerLink>(
        await fetch(`${base(m, p)}/broker-links/${c.brokerConnectionId}`, {
            method: "PUT",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify({brokerAccountId: c.brokerAccountId}),
        }),
        "증권사 계좌를 연결하지 못했습니다.",
    );

export const refreshBrokerHoldingSnapshot = async (m: number, p: number) =>
    getJsonResponse<BrokerHoldingSnapshot>(
        await fetch(`${base(m, p)}/broker-holding-snapshots`, {method: "POST"}),
        "보유 종목 스냅샷을 갱신하지 못했습니다.",
    );

export const getLatestBrokerHoldingSnapshot = async (m: number, p: number) =>
    getJsonResponse<BrokerHoldingSnapshot>(
        await fetch(`${base(m, p)}/broker-holding-snapshots/latest`),
        "최신 보유 종목 스냅샷을 불러오지 못했습니다.",
    );

export const getLatestBrokerHoldingSnapshotComparison = async (m: number, p: number) =>
    getJsonResponse<BrokerHoldingPreview>(
        await fetch(`${base(m, p)}/broker-holding-snapshots/latest/comparison`),
        "저장된 보유 종목 비교 결과를 불러오지 못했습니다.",
    );

export const approveBrokerHoldingOpeningBalance = async (m: number, p: number, snapshotItemId: number) =>
    getJsonResponse<PortfolioBrokerHoldingImport>(
        await fetch(`${base(m, p)}/broker-holding-imports`, {
            method: "POST",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify({snapshotItemId}),
        }),
        "개시 잔고 반영에 실패했습니다.",
    );

export const approveBrokerHoldingOpeningBalanceBatch = async (m: number, p: number, snapshotId: number) =>
    getJsonResponse<BrokerHoldingImportBatchResponse>(
        await fetch(`${base(m, p)}/broker-holding-imports/batch`, {
            method: "POST",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify({snapshotId}),
        }),
        "개시 잔고 일괄 반영에 실패했습니다.",
    );

export const getBrokerHoldingOpeningBalanceImports = async (m: number, p: number, page = 0, size = 20) =>
    getJsonResponse<BrokerHistoryPage<PortfolioBrokerHoldingImport>>(
        await fetch(`${base(m, p)}/broker-holding-imports?page=${page}&size=${size}`),
        "개시 잔고 반영 이력을 불러오지 못했습니다.",
    );

export const revokeBrokerHoldingOpeningBalanceImport = async (m: number, p: number, importId: number) => {
    const response = await fetch(`${base(m, p)}/broker-holding-imports/${importId}`, {method: "DELETE"});
    if (!response.ok) {
        await getJsonResponse<void>(response, "개시 잔고 반영을 취소하지 못했습니다.");
    }
};
export const approveBrokerHoldingAdjustment = async (m: number, p: number, snapshotItemId: number) =>
    getJsonResponse<PortfolioBrokerHoldingAdjustment>(
        await fetch(`${base(m, p)}/broker-holding-adjustments`, {
            method: "POST",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify({snapshotItemId}),
        }),
        "수량 차이 조정 반영에 실패했습니다.",
    );

export const getBrokerHoldingAdjustments = async (m: number, p: number, page = 0, size = 20) =>
    getJsonResponse<BrokerHistoryPage<PortfolioBrokerHoldingAdjustment>>(
        await fetch(`${base(m, p)}/broker-holding-adjustments?page=${page}&size=${size}`),
        "수량 차이 조정 이력을 불러오지 못했습니다.",
    );

export const revokeBrokerHoldingAdjustment = async (m: number, p: number, adjustmentId: number) => {
    const response = await fetch(`${base(m, p)}/broker-holding-adjustments/${adjustmentId}`, {method: "DELETE"});
    if (!response.ok) {
        await getJsonResponse<void>(response, "수량 차이 조정 반영을 취소하지 못했습니다.");
    }
};

export const unlinkBrokerAccount = async (m: number, p: number, connectionId: number) => {
    const response = await fetch(`${base(m, p)}/broker-links/${connectionId}`, {method: "DELETE"});
    if (!response.ok) {
        await getJsonResponse<void>(response, "증권사 계좌 연결을 해제하지 못했습니다.");
    }
};
