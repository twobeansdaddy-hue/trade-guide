export type ApiErrorCode =
    | "MARKET_DATA_PROVIDER_NOT_CONFIGURED"
    | "MARKET_DATA_UNAVAILABLE"
    | "MARKET_DATA_RATE_LIMIT_EXCEEDED"
    | "MARKET_DATA_STALE"
    | "BROKER_CONNECTION_UNAVAILABLE"
    | "BROKER_CAPABILITY_UNAVAILABLE"
    | "BROKER_LEDGER_MARKET_UNSUPPORTED"
    | "PORTFOLIO_NOT_FOUND"
    | "PORTFOLIO_BROKER_LINK_NOT_FOUND"
    | "BROKER_CONNECTION_REVERIFICATION_REQUIRED"
    | "RECONCILIATION_MISMATCH"
    | "BASELINE_EXCLUDED"
    | "REPLAY_VALIDATION_FAILED"
    | "BROKER_CALL_IN_PROGRESS"
    | "BROKER_CALL_COOLDOWN"
    | "ORDER_IMPORT_COVERAGE_ACKNOWLEDGEMENT_REQUIRED"
    | "PAGE_LIMIT_EXCEEDED"
    | "CURSOR_REPEATED"
    | string;

export type ApiErrorResponse = {
    message?: string;
    code?: ApiErrorCode;
};

export function getApiErrorCode(error: unknown): ApiErrorCode | undefined {
    return error instanceof ApiError ? error.code : undefined;
}

export class ApiError extends Error {
    public readonly status: number;
    public readonly code?: ApiErrorCode;
    public readonly retryAfterSeconds?: number;

    constructor(status: number, message: string, code?: ApiErrorCode, retryAfterSeconds?: number) {
        super(message);
        this.name = "ApiError";
        this.status = status;
        this.code = code;
        this.retryAfterSeconds = retryAfterSeconds;
    }
}

export function hasApiStatus(error: unknown, status: number): boolean {
    return error instanceof ApiError && error.status === status;
}

export function hasApiErrorCode(error: unknown, code: ApiErrorCode): boolean {
    return error instanceof ApiError && error.code === code;
}

export function isMarketDataProviderNotConfigured(error: unknown): boolean {
    if (!(error instanceof ApiError)) {
        return false;
    }
    return (
        error.code === "MARKET_DATA_PROVIDER_NOT_CONFIGURED" ||
        (error.status === 503 && (!error.code || error.code === "MARKET_DATA_PROVIDER_NOT_CONFIGURED"))
    );
}

export function isMarketDataRateLimitExceeded(error: unknown): boolean {
    if (!(error instanceof ApiError)) {
        return false;
    }
    return (
        error.code === "MARKET_DATA_RATE_LIMIT_EXCEEDED" ||
        (error.status === 429 && (!error.code || error.code === "MARKET_DATA_RATE_LIMIT_EXCEEDED"))
    );
}

export function getApiRetryAfterSeconds(error: unknown, fallbackSeconds = 60): number {
    if (error instanceof ApiError && typeof error.retryAfterSeconds === "number" && error.retryAfterSeconds > 0) {
        return error.retryAfterSeconds;
    }
    return fallbackSeconds;
}

export async function parseApiError(
    response: Response,
    fallbackMessage: string,
): Promise<ApiError> {
    let message = `${fallbackMessage} (HTTP ${response.status})`;
    let code: ApiErrorCode | undefined;
    let retryAfterSeconds: number | undefined;

    const retryAfterHeader = response.headers.get("retry-after") ?? response.headers.get("Retry-After");
    if (retryAfterHeader) {
        const parsed = parseInt(retryAfterHeader, 10);
        if (!Number.isNaN(parsed) && parsed > 0) {
            retryAfterSeconds = parsed;
        }
    }

    try {
        const responseToParse = typeof response.clone === "function" ? response.clone() : response;
        const errorResponse = (await responseToParse.json()) as ApiErrorResponse & {
            retryAfterSeconds?: number;
        };

        if (errorResponse.message) {
            message = errorResponse.message;
        }
        if (errorResponse.code) {
            code = errorResponse.code;
        }
        if (typeof errorResponse.retryAfterSeconds === "number" && errorResponse.retryAfterSeconds > 0) {
            retryAfterSeconds = errorResponse.retryAfterSeconds;
        }
    } catch {
        // JSON 오류 응답이 아닌 경우 기본 메시지를 사용한다.
    }

    return new ApiError(response.status, message, code, retryAfterSeconds);
}

export async function getErrorMessage(
    response: Response,
    fallbackMessage: string,
): Promise<string> {
    const error = await parseApiError(response, fallbackMessage);
    return error.message;
}

export async function getJsonResponse<T>(
    response: Response,
    fallbackMessage: string,
): Promise<T> {
    if (!response.ok) {
        throw await parseApiError(response, fallbackMessage);
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
