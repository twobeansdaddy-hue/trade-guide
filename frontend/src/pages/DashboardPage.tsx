import {useEffect, useState} from "react";
import {getPortfolioRiskAlerts} from "../api/portfolioRiskApi";
import {getPortfolioValuation} from "../api/portfolioValuationApi";
import {usePortfolioContext} from "../context/portfolioContext";
import type {PortfolioRiskAlert} from "../types/portfolioRisk";
import type {PortfolioValuation} from "../types/portfolioValuation";
import {formatPercent, formatUsd, getProfitLossClassName} from "../utils/format";
import HoldingValuationList from "../components/valuation/HoldingValuationList";
import RiskAlertItem from "../components/risk/RiskAlertItem";

export default function DashboardPage() {
    const {memberId, selectedPortfolioId} = usePortfolioContext();
    const [valuation, setValuation] = useState<PortfolioValuation | null>(null);
    const [alerts, setAlerts] = useState<PortfolioRiskAlert[]>([]);
    const [valuationError, setValuationError] = useState<string | null>(null);
    const [alertsError, setAlertsError] = useState<string | null>(null);
    const [isValuationLoading, setIsValuationLoading] = useState(true);
    const [isAlertsLoading, setIsAlertsLoading] = useState(true);

    useEffect(() => {
        if (selectedPortfolioId === null) return;
        void getPortfolioValuation(memberId, selectedPortfolioId)
            .then(setValuation)
            .catch((reason: unknown) => setValuationError(reason instanceof Error ? reason.message : "포트폴리오 평가를 불러오지 못했습니다."))
            .finally(() => setIsValuationLoading(false));

        void getPortfolioRiskAlerts(memberId, selectedPortfolioId)
            .then(setAlerts)
            .catch((reason: unknown) => setAlertsError(reason instanceof Error ? reason.message : "위험 경고를 불러오지 못했습니다."))
            .finally(() => setIsAlertsLoading(false));
    }, [memberId, selectedPortfolioId]);

    return <><header className="page-header"><p className="eyebrow">PORTFOLIO {selectedPortfolioId ?? "-"}</p><h1>대시보드</h1><p>평가와 위험 상태를 먼저 확인하고, 다음 판단은 전략 가이드에서 검토합니다.</p></header>
        {isValuationLoading ? <p className="status-message" aria-live="polite">포트폴리오 평가를 불러오는 중입니다.</p> : valuationError ? <p className="status-message error" role="alert">{valuationError}</p> : valuation ? <>
            <section className="summary-grid" aria-label="포트폴리오 평가 요약"><div><span>현재 평가금액</span><strong>{formatUsd(valuation.totalMarketValue)}</strong></div><div><span>평가손익</span><strong className={getProfitLossClassName(valuation.totalUnrealizedProfitLoss)}>{formatUsd(valuation.totalUnrealizedProfitLoss)}</strong></div><div><span>수익률</span><strong className={getProfitLossClassName(valuation.totalReturnRate)}>{formatPercent(valuation.totalReturnRate)}</strong></div></section>
            <section className="content-section"><div className="section-heading"><div><p className="section-label">HOLDINGS</p><h2>보유 종목 현황</h2></div></div><HoldingValuationList holdings={valuation.holdingValuations}/></section>
        </> : null}
        <section className="content-section"><div className="section-heading"><div><p className="section-label">RISK</p><h2>확인 필요</h2></div>{!isAlertsLoading && !alertsError ? <span className={alerts.length > 0 ? "alert-count" : "muted-count"}>{alerts.length}건</span> : null}</div>{isAlertsLoading ? <p className="status-message" aria-live="polite">위험 경고를 불러오는 중입니다.</p> : alertsError ? <p className="status-message error" role="alert">{alertsError}</p> : alerts.length ? <ul className="risk-alert-list">{alerts.map((alert) => <RiskAlertItem key={`${alert.market}-${alert.ticker}`} alert={alert}/>)}</ul> : <p className="empty-state">현재 위험 경고가 없습니다.</p>}</section>
    </>;
}
