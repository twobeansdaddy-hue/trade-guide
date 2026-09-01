export type ApiErrorResponse = {
    message?: string
}

export async function getErrorMessage(
    response: Response,
    fallbackMessage: string,
): Promise<string> {
    try {
        const errorResponse = (await response.json()) as ApiErrorResponse;

        if (errorResponse.message) {
            return errorResponse.message;
        }
    } catch {
        // JSON 오류 응답이 아닌 경우 기본 메시지를 사용한다.
    }

    return `${fallbackMessage} (HTTP ${response.status})`;
}

export async function getJsonResponse<T>(
    response: Response,
    fallbackMessage: string,
): Promise<T> {
    if (!response.ok) {
        throw new Error(await getErrorMessage(response, fallbackMessage));
    }

    if (!response.headers.get("content-type")?.includes("application/json")) {
        throw new Error(`${fallbackMessage} (서버가 JSON 응답을 반환하지 않았습니다.)`);
    }

    try {
        return (await response.json()) as T;
    } catch {
        throw new Error(`${fallbackMessage} (응답 데이터를 해석할 수 없습니다.)`);
    }
}
