import {createContext, useContext} from "react";
import type {Portfolio} from "../types/portfolio";

export type PortfolioContextValue = {
    memberId: number
    portfolios: Portfolio[]
    selectedPortfolioId: number | null
    isLoading: boolean
    errorMessage: string | null
    selectPortfolio: (portfolioId: number) => void
}

export const PortfolioContext = createContext<PortfolioContextValue | null>(null);

export function usePortfolioContext() {
    const context = useContext(PortfolioContext);
    if (context === null) {
        throw new Error("PortfolioProvider 안에서 사용해야 합니다.");
    }
    return context;
}
