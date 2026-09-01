import {useEffect, useState} from "react";
import {getCurrentMember} from "../api/authApi";
import {createPortfolio as requestCreatePortfolio, getPortfolios} from "../api/portfolioApi";
import type {AuthenticatedMember} from "../types/auth";
import type {Portfolio} from "../types/portfolio";
import {PortfolioContext} from "./portfolioContext";

const localMemberId = Number(import.meta.env.VITE_LOCAL_MEMBER_ID);
const isLocalDevelopment = Number.isInteger(localMemberId) && localMemberId > 0;

export function PortfolioProvider({children}: {children: React.ReactNode}) {
    const [portfolios, setPortfolios] = useState<Portfolio[]>([]);
    const [selectedPortfolioId, setSelectedPortfolioId] = useState<number | null>(null);
    const [viewer, setViewer] = useState<AuthenticatedMember | null>(null);
    const [isLoading, setIsLoading] = useState(true);
    const [isAuthenticationRequired, setIsAuthenticationRequired] = useState(false);
    const [errorMessage, setErrorMessage] = useState<string | null>(null);

    useEffect(() => {
        async function loadWorkspace() {
            try {
                const currentMember = isLocalDevelopment
                    ? {id: localMemberId, email: "", nickname: "로컬 개발", provider: "GOOGLE" as const}
                    : await getCurrentMember();

                if (currentMember === null) {
                    setIsAuthenticationRequired(true);
                    return;
                }

                const data = await getPortfolios(currentMember.id);
                setViewer(currentMember);
                setPortfolios(data);
                setSelectedPortfolioId(data[0]?.id ?? null);
            } catch (reason) {
                setErrorMessage(reason instanceof Error ? reason.message : "포트폴리오 목록을 불러오지 못했습니다.");
            } finally {
                setIsLoading(false);
            }
        }

        void loadWorkspace();
    }, []);

    async function createPortfolio(name: string) {
        if (viewer === null) {
            throw new Error("로그인 정보를 확인할 수 없습니다.");
        }

        const portfolio = await requestCreatePortfolio(viewer.id, name);
        setPortfolios((current) => [...current, portfolio]);
        setSelectedPortfolioId(portfolio.id);
    }

    return <PortfolioContext.Provider value={{
        memberId: viewer?.id ?? 0,
        viewer,
        portfolios,
        selectedPortfolioId,
        isLoading,
        isAuthenticationRequired,
        errorMessage,
        selectPortfolio: setSelectedPortfolioId,
        createPortfolio,
    }}>{children}</PortfolioContext.Provider>;
}
