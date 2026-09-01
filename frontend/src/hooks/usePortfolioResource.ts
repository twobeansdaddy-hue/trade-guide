import {useCallback, useEffect, useState} from "react";

type PortfolioRequest<T> = (memberId: number, portfolioId: number) => Promise<T>;

type ResourceState<T> = {
    data: T | null;
    error: Error | null;
    isLoading: boolean;
};

const initialResourceState = <T,>(): ResourceState<T> => ({
    data: null,
    error: null,
    isLoading: true,
});

export function usePortfolioResource<T>(
    memberId: number,
    portfolioId: number,
    request: PortfolioRequest<T>,
    onSuccess?: (data: T) => void,
) {
    const [resource, setResource] = useState<ResourceState<T>>(initialResourceState);
    const [requestVersion, setRequestVersion] = useState(0);

    useEffect(() => {
        let isCurrentRequest = true;

        void request(memberId, portfolioId)
            .then((data) => {
                if (!isCurrentRequest) return;

                setResource({data, error: null, isLoading: false});
                onSuccess?.(data);
            })
            .catch((reason: unknown) => {
                if (!isCurrentRequest) return;

                setResource({
                    data: null,
                    error: reason instanceof Error
                        ? reason
                        : new Error("데이터를 불러오지 못했습니다."),
                    isLoading: false,
                });
            });

        return () => {
            isCurrentRequest = false;
        };
    }, [memberId, onSuccess, portfolioId, request, requestVersion]);

    const refresh = useCallback(() => {
        setResource(initialResourceState());
        setRequestVersion((current) => current + 1);
    }, []);

    const replaceData = useCallback((data: T) => {
        setResource({data, error: null, isLoading: false});
        onSuccess?.(data);
    }, [onSuccess]);

    return {...resource, refresh, replaceData};
}
