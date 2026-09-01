import {useEffect, useState} from "react";
import {Link} from "react-router-dom";
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
            .then((data) => {
                setValuation(data);
                setValuationError(null);
            })
            .catch((reason: unknown) => setValuationError(reason instanceof Error ? reason.message : "포트폴리오 평가를 불러오지 못했습니다."))
            .finally(() => setIsValuationLoading(false));

        void getPortfolioRiskAlerts(memberId, selectedPortfolioId)
            .then((data) => {
                setAlerts(data);
                setAlertsError(null);
            })
            .catch((reason: unknown) => setAlertsError(reason instanceof Error ? reason.message : "위험 경고를 불러오지 못했습니다."))
            .finally(() => setIsAlertsLoading(false));
    }, [memberId, selectedPortfolioId]);

    return <>
        <header className="page-header dashboard-header">
            <div>
                <p className="eyebrow">PORTFOLIO {selectedPortfolioId ?? "-"}</p>
                <h1>대시보드</h1>
                <p>현재 평가와 위험 상태를 먼저 확인한 뒤, 전략 가이드에서 다음 판단을 검토합니다.</p>
            </div>
            <Link className="quiet-action" to="/strategy-guides">전략 가이드 보기</Link>
        </header>
        {isValuationLoading ? <p className="status-message" aria-live="polite">포트폴리오 평가를 불러오는 중입니다.</p> : valuationError ? <p className="status-message error" role="alert">{valuationError}</p> : valuation ? <>
            <section className="summary-grid" aria-label="포트폴리오 평가 요약">
                <div className="summary-primary"><span>현재 평가금액</span><strong>{formatUsd(valuation.totalMarketValue)}</strong><small>매입금액 {formatUsd(valuation.totalPurchaseAmount)}</small></div>
                <div><span>평가손익</span><strong className={getProfitLossClassName(valuation.totalUnrealizedProfitLoss)}>{formatUsd(valuation.totalUnrealizedProfitLoss)}</strong><small>미실현 기준</small></div>
                <div><span>수익률</span><strong className={getProfitLossClassName(valuation.totalReturnRate)}>{formatPercent(valuation.totalReturnRate)}</strong><small>보유 종목 {valuation.holdingValuations.length}개</small></div>
            </section>
            <section className="content-section">
                <div className="section-heading">
                    <div><p className="section-label">HOLDINGS</p><h2>보유 종목 현황</h2></div>
                    <Link className="section-link" to="/holdings">전체 보기</Link>
                </div>
                <HoldingValuationList holdings={valuation.holdingValuations}/>
            </section>
        </> : null}
        <section className="content-section risk-section">
            <div className="section-heading">
                <div><p className="section-label">RISK</p><h2>확인 필요</h2></div>
                {!isAlertsLoading && !alertsError ? <span className={alerts.length > 0 ? "alert-count" : "muted-count"}>{alerts.length}건</span> : null}
            </div>
            {isAlertsLoading ? <p className="status-message" aria-live="polite">위험 경고를 불러오는 중입니다.</p> : alertsError ? <p className="status-message error" role="alert">{alertsError}</p> : alerts.length ? <ul className="risk-alert-list">{alerts.map((alert) => <RiskAlertItem key={`${alert.market}-${alert.ticker}`} alert={alert}/>)}</ul> : <p className="empty-state">현재 위험 한도를 넘는 보유 종목이 없습니다.</p>}
        </section>
    </>;
}
