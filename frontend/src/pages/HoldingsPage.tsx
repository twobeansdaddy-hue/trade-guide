import {useEffect, useState} from "react";
import {getPortfolioValuation} from "../api/portfolioValuationApi";
import {MEMBER_ID, PORTFOLIO_ID} from "../constants/portfolio";
import type {PortfolioValuation} from "../types/portfolioValuation";
import HoldingValuationList from "../components/valuation/HoldingValuationList";

export default function HoldingsPage() {
    const [valuation, setValuation] = useState<PortfolioValuation | null>(null);
    const [error, setError] = useState<string | null>(null);
    useEffect(() => { void getPortfolioValuation(MEMBER_ID, PORTFOLIO_ID).then(setValuation).catch((reason: unknown) => setError(reason instanceof Error ? reason.message : "보유 종목을 불러오지 못했습니다.")); }, []);
    return <><header className="page-header"><p className="eyebrow">PORTFOLIO {PORTFOLIO_ID}</p><h1>보유 종목</h1><p>평가 API가 제공하는 현재 평가 기준의 보유 현황입니다.</p></header>{error ? <p className="status-message error" role="alert">{error}</p> : valuation ? <HoldingValuationList holdings={valuation.holdingValuations}/> : <p className="status-message" aria-live="polite">보유 종목을 불러오는 중입니다.</p>}</>;
}
