import {getJsonResponse} from "./apiError";
import type {
    BrokerConnection,
    BrokerConnectionCreateRequest,
    BrokerProviderCatalogItem,
} from "../types/brokerConnection";

export async function getBrokerProviders(): Promise<BrokerProviderCatalogItem[]> {
    const response = await fetch("/api/broker-providers");

    return getJsonResponse<BrokerProviderCatalogItem[]>(
        response,
        "증권사 제공자 목록을 불러오지 못했습니다.",
    );
}

export async function getBrokerConnections(memberId: number): Promise<BrokerConnection[]> {
    const response = await fetch(`/api/members/${memberId}/broker-connections`);

    return getJsonResponse<BrokerConnection[]>(
        response,
        "증권사 연결 정보를 불러오지 못했습니다.",
    );
}

export async function createBrokerConnection(
    memberId: number,
    request: BrokerConnectionCreateRequest,
): Promise<BrokerConnection> {
    const response = await fetch(`/api/members/${memberId}/broker-connections`, {
        method: "POST",
        headers: {
            "Content-Type": "application/json",
        },
        body: JSON.stringify(request),
    });

    return getJsonResponse<BrokerConnection>(
        response,
        "증권사 연결 정보를 저장하지 못했습니다.",
    );
}

export async function deleteBrokerConnection(
    memberId: number,
    connectionId: number,
): Promise<void> {
    const response = await fetch(
        `/api/members/${memberId}/broker-connections/${connectionId}`,
        {method: "DELETE"},
    );

    if (!response.ok) {
        await getJsonResponse<void>(response, "증권사 연결 정보를 해제하지 못했습니다.");
    }
}

export async function verifyBrokerConnection(
    memberId: number,
    connectionId: number,
): Promise<BrokerConnection> {
    const response = await fetch(
        `/api/members/${memberId}/broker-connections/${connectionId}/verify`,
        {method: "POST"},
    );

    return getJsonResponse<BrokerConnection>(response, "증권사 연결을 확인하지 못했습니다.");
}
