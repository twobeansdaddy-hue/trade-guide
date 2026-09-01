import {useEffect, useState} from "react";
import {Link, useLocation} from "react-router-dom";
import {getPortfolioValuation} from "../api/portfolioValuationApi";
import {usePortfolioContext} from "../context/portfolioContext";
import type {PortfolioValuation} from "../types/portfolioValuation";
import HoldingValuationList from "../components/valuation/HoldingValuationList";

export default function HoldingsPage() {
    const location = useLocation();
    const {memberId, selectedPortfolioId} = usePortfolioContext();
    const [valuation, setValuation] = useState<PortfolioValuation | null>(null);
    const [error, setError] = useState<string | null>(null);
    useEffect(() => { if (selectedPortfolioId !== null) void getPortfolioValuation(memberId, selectedPortfolioId).then(setValuation).catch((reason: unknown) => setError(reason instanceof Error ? reason.message : "보유 종목을 불러오지 못했습니다.")); }, [memberId, selectedPortfolioId]);
    const successMessage = (location.state as {successMessage?: string} | null)?.successMessage;
    return <><header className="page-header dashboard-header"><div><p className="eyebrow">PORTFOLIO {selectedPortfolioId ?? "-"}</p><h1>보유 종목</h1><p>평가 API가 제공하는 현재 평가 기준의 보유 현황입니다.</p></div><Link className="quiet-action" to="/transactions/new">매매 기록 등록</Link></header>{successMessage ? <p className="form-success-message" role="status">{successMessage}</p> : null}{error ? <p className="status-message error" role="alert">{error}</p> : valuation ? <HoldingValuationList holdings={valuation.holdingValuations}/> : <p className="status-message" aria-live="polite">보유 종목을 불러오는 중입니다.</p>}</>;
}
