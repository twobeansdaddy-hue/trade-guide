import {getJsonResponse} from "./apiError";
import type {AuthenticatedMember} from "../types/auth";

export async function getCurrentMember(): Promise<AuthenticatedMember | null> {
    const response = await fetch("/api/auth/me");

    if (response.status === 401) {
        return null;
    }

    return getJsonResponse<AuthenticatedMember>(response, "로그인 정보를 불러오지 못했습니다.");
}
