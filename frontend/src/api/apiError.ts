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