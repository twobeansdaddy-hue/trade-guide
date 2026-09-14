import {useCallback, useEffect, useState} from "react";
import {isMarketDataRateLimitExceeded} from "../api/apiError";

type PortfolioRequest<T> = (memberId: number, portfolioId: number) => Promise<T>;

type ResourceState<T> = {
    data: T | null;
    lastSuccessfulData: T | null;
    error: Error | null;
    isLoading: boolean;
};

function getStorageKey(
    memberId: number,
    portfolioId: number,
    request: (memberId: number, portfolioId: number) => Promise<unknown>,
): string {
    return `tg_res_${memberId}_${portfolioId}_${request.name || "req"}`;
}

function loadStoredData<T>(key: string): T | null {
    try {
        const item = sessionStorage.getItem(key) ?? localStorage.getItem(key);
        if (item) {
            return JSON.parse(item) as T;
        }
    } catch {
        // ignore
    }
    return null;
}

function saveStoredData<T>(key: string, data: T | null) {
    try {
        if (data === null) {
            sessionStorage.removeItem(key);
            localStorage.removeItem(key);
        } else {
            const serialized = JSON.stringify(data);
            sessionStorage.setItem(key, serialized);
            localStorage.setItem(key, serialized);
        }
    } catch {
        // ignore
    }
}

export function usePortfolioResource<T>(
    memberId: number,
    portfolioId: number,
    request: PortfolioRequest<T>,
    onSuccess?: (data: T) => void,
) {
    const storageKey = getStorageKey(memberId, portfolioId, request);
    const [resource, setResource] = useState<ResourceState<T>>(() => {
        const cached = loadStoredData<T>(storageKey);
        return {
            data: null,
            lastSuccessfulData: cached,
            error: null,
            isLoading: true,
        };
    });
    const [requestVersion, setRequestVersion] = useState(0);

    useEffect(() => {
        let isCurrentRequest = true;

        void request(memberId, portfolioId)
            .then((data) => {
                if (!isCurrentRequest) return;

                saveStoredData(storageKey, data);
                setResource({
                    data,
                    lastSuccessfulData: data,
                    error: null,
                    isLoading: false,
                });
                onSuccess?.(data);
            })
            .catch((reason: unknown) => {
                if (!isCurrentRequest) return;

                const error = reason instanceof Error
                    ? reason
                    : new Error("데이터를 불러오지 못했습니다.");

                setResource((current) => {
                    const fallbackData = current.lastSuccessfulData ?? loadStoredData<T>(storageKey);
                    const preserveData = isMarketDataRateLimitExceeded(error) && fallbackData !== null;
                    return {
                        data: preserveData ? fallbackData : null,
                        lastSuccessfulData: fallbackData,
                        error,
                        isLoading: false,
                    };
                });
            });

        return () => {
            isCurrentRequest = false;
        };
    }, [memberId, onSuccess, portfolioId, request, requestVersion, storageKey]);

    const refresh = useCallback(() => {
        setResource((current) => ({
            ...current,
            isLoading: true,
            error: null,
        }));
        setRequestVersion((current) => current + 1);
    }, []);

    const replaceData = useCallback((data: T) => {
        saveStoredData(storageKey, data);
        setResource({
            data,
            lastSuccessfulData: data,
            error: null,
            isLoading: false,
        });
        onSuccess?.(data);
    }, [onSuccess, storageKey]);

    return {...resource, refresh, replaceData};
}
