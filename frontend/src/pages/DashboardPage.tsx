import {Link} from "react-router-dom";
import RequestError from "../components/common/RequestError";
import RiskAlertItem from "../components/risk/RiskAlertItem";
import HoldingSummaryList from "../components/valuation/HoldingSummaryList";
import {hasApiStatus} from "../api/apiError";
import {getPortfolioRiskAlerts} from "../api/portfolioRiskApi";
import {getPortfolioValuation} from "../api/portfolioValuationApi";
import {usePortfolioContext} from "../context/portfolioContext";
import {usePortfolioResource} from "../hooks/usePortfolioResource";
import {formatPercent, formatUsd, getProfitLossClassName} from "../utils/format";

export default function DashboardPage() {
    const {memberId, selectedPortfolioId} = usePortfolioContext();

    if (selectedPortfolioId === null) return null;

    return <DashboardContent key={`${memberId}-${selectedPortfolioId}`} memberId={memberId} portfolioId={selectedPortfolioId}/>;
}

function DashboardContent({memberId, portfolioId}: {memberId: number; portfolioId: number}) {
    const valuationResource = usePortfolioResource(memberId, portfolioId, getPortfolioValuation);
    const alertsResource = usePortfolioResource(memberId, portfolioId, getPortfolioRiskAlerts);
    const isRiskPolicyMissing = hasApiStatus(alertsResource.error, 404);

    return <>
        <header className="page-header dashboard-header">
            <div>
                <p className="eyebrow">PORTFOLIO {portfolioId}</p>
                <h1>대시보드</h1>
                <p>현재 평가와 위험 상태를 먼저 확인한 뒤, 전략 가이드에서 다음 판단을 검토합니다.</p>
            </div>
            <Link className="quiet-action" to="/strategy-guides">전략 가이드 보기</Link>
        </header>
        {valuationResource.isLoading ? <p className="status-message" aria-live="polite">포트폴리오 평가를 불러오는 중입니다.</p> : valuationResource.error ? <RequestError message={valuationResource.error.message} onRetry={valuationResource.refresh} retryLabel="평가 다시 시도"/> : valuationResource.data ? <>
            <section className="summary-grid" aria-label="포트폴리오 평가 요약">
                <div className="summary-primary"><span>현재 평가금액</span><strong>{formatUsd(valuationResource.data.totalMarketValue)}</strong><small>매입금액 {formatUsd(valuationResource.data.totalPurchaseAmount)}</small></div>
                <div><span>평가손익</span><strong className={getProfitLossClassName(valuationResource.data.totalUnrealizedProfitLoss)}>{formatUsd(valuationResource.data.totalUnrealizedProfitLoss)}</strong><small>미실현 기준</small></div>
                <div><span>수익률</span><strong className={getProfitLossClassName(valuationResource.data.totalReturnRate)}>{formatPercent(valuationResource.data.totalReturnRate)}</strong><small>보유 종목 {valuationResource.data.holdingValuations.length}개</small></div>
            </section>
            <section className="content-section">
                <div className="section-heading">
                    <div><p className="section-label">HOLDINGS</p><h2>주요 보유 종목</h2></div>
                    <Link className="section-link" to="/holdings">전체 보기</Link>
                </div>
                {valuationResource.data.holdingValuations.length > 0 ? <HoldingSummaryList holdings={valuationResource.data.holdingValuations}/> : <div className="empty-state empty-state-action"><p>아직 등록된 매매 기록이 없습니다.</p><Link className="quiet-action" to="/transactions/new">매매 기록 등록</Link></div>}
            </section>
        </> : null}
        <section className="content-section risk-section">
            <div className="section-heading">
                <div><p className="section-label">RISK</p><h2>확인 필요</h2></div>
                {!alertsResource.isLoading && !alertsResource.error ? <span className={alertsResource.data && alertsResource.data.length > 0 ? "alert-count" : "muted-count"}>{alertsResource.data?.length ?? 0}건</span> : null}
            </div>
            {alertsResource.isLoading ? <p className="status-message" aria-live="polite">위험 경고를 불러오는 중입니다.</p> : isRiskPolicyMissing ? <div className="empty-state empty-state-action"><p>위험 한도를 설정하면 종목별 비중 초과를 확인할 수 있습니다.</p><Link className="quiet-action" to="/settings">위험 한도 설정</Link></div> : alertsResource.error ? <RequestError message={alertsResource.error.message} onRetry={alertsResource.refresh} retryLabel="위험 경고 다시 시도"/> : alertsResource.data && alertsResource.data.length > 0 ? <ul className="risk-alert-list">{alertsResource.data.map((alert) => <RiskAlertItem key={`${alert.market}-${alert.ticker}`} alert={alert}/>)}</ul> : <p className="empty-state">현재 위험 한도를 넘는 보유 종목이 없습니다.</p>}
        </section>
    </>;
}
