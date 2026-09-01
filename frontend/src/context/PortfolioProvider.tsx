import {useEffect, useState} from "react";
import {getPortfolios} from "../api/portfolioApi";
import type {Portfolio} from "../types/portfolio";
import {PortfolioContext} from "./portfolioContext";

const localMemberId = Number(import.meta.env.VITE_LOCAL_MEMBER_ID);
const localConfigurationError = Number.isInteger(localMemberId) && localMemberId > 0
    ? null
    : "개발용 회원 ID가 설정되지 않았습니다.";

export function PortfolioProvider({children}: {children: React.ReactNode}) {
    const [portfolios, setPortfolios] = useState<Portfolio[]>([]);
    const [selectedPortfolioId, setSelectedPortfolioId] = useState<number | null>(null);
    const [isLoading, setIsLoading] = useState(() => localConfigurationError === null);
    const [errorMessage, setErrorMessage] = useState<string | null>(() => localConfigurationError);

    useEffect(() => {
        if (localConfigurationError !== null) return;

        void getPortfolios(localMemberId)
            .then((data) => {
                setPortfolios(data);
                setSelectedPortfolioId(data[0]?.id ?? null);
            })
            .catch((reason: unknown) => setErrorMessage(
                reason instanceof Error ? reason.message : "포트폴리오 목록을 불러오지 못했습니다.",
            ))
            .finally(() => setIsLoading(false));
    }, []);

    return <PortfolioContext.Provider value={{
        memberId: localConfigurationError === null ? localMemberId : 0,
        portfolios,
        selectedPortfolioId,
        isLoading,
        errorMessage,
        selectPortfolio: setSelectedPortfolioId,
    }}>{children}</PortfolioContext.Provider>;
}
