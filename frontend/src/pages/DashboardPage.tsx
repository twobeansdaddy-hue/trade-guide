import {Link} from "react-router-dom";
import { ResourceBoundary } from "../components/common/ResourceBoundary";
import HoldingSummaryList from "../components/valuation/HoldingSummaryList";
import { getPortfolioRiskAlerts } from "../api/portfolioRiskApi";
import { getPortfolioValuation } from "../api/portfolioValuationApi";
import { usePortfolioContext } from "../context/portfolioContext";
import { usePortfolioResource } from "../hooks/usePortfolioResource";
import { formatPercent, formatUsd, formatKrw, getProfitLossClassName } from "../utils/format";
import RiskAlertItem from "../components/risk/RiskAlertItem";
import { isMarketDataRateLimitExceeded, getApiRetryAfterSeconds } from "../api/apiError";
import "../styles/pages/dashboard.css";

export default function DashboardPage() {
    const {memberId, selectedPortfolioId, portfolios} = usePortfolioContext();

    if (selectedPortfolioId === null) return null;

    const portfolioName = portfolios.find(p => p.id === selectedPortfolioId)?.name || "대시보드";

    return <DashboardContent key={`${memberId}-${selectedPortfolioId}`} memberId={memberId} portfolioId={selectedPortfolioId} portfolioName={portfolioName}/>;
}

function DashboardContent({memberId, portfolioId, portfolioName}: {memberId: number; portfolioId: number; portfolioName: string}) {
    const valuationResource = usePortfolioResource(memberId, portfolioId, getPortfolioValuation);
    const alertsResource = usePortfolioResource(memberId, portfolioId, getPortfolioRiskAlerts);

    const isValuationRateLimit = isMarketDataRateLimitExceeded(valuationResource.error);
    const isAlertsRateLimit = isMarketDataRateLimitExceeded(alertsResource.error);
    const showGlobalRateLimit = (isValuationRateLimit && valuationResource.data) || (isAlertsRateLimit && alertsResource.data);
    const cooldown = getApiRetryAfterSeconds(valuationResource.error) || getApiRetryAfterSeconds(alertsResource.error);

    return (
        <div className="dashboard-page">
            <header className="page-header dashboard-header">
                <div>
                    <h1>{portfolioName}</h1>
                    <p>현재 평가와 위험 상태를 먼저 확인한 뒤, 전략 가이드에서 다음 판단을 검토합니다.</p>
                </div>
                <Link className="accent-action" to="/strategy-guides">전략 가이드 보기</Link>
            </header>

            {showGlobalRateLimit && (
                <div className="global-rate-limit-banner">
                    <p>시장 데이터 요청 제한을 초과하여 이전 데이터를 표시합니다.</p>
                    <button onClick={() => { valuationResource.refresh(); alertsResource.refresh(); }}>
                        다시 시도 {cooldown ? `(${cooldown}초)` : ""}
                    </button>
                </div>
            )}

            <ResourceBoundary 
                resource={valuationResource}
                loadingMessage="포트폴리오 평가를 불러오는 중입니다."
                renderData={(data) => {
                    const usd = data.totalsByCurrency.USD ?? {
                        totalMarketValue: 0,
                        totalPurchaseAmount: 0,
                        totalUnrealizedProfitLoss: 0,
                        totalReturnRate: 0,
                    };
                    const krw = data.totalsByCurrency.KRW;

                    return (
                        <>
                            <section className="summary-grid" aria-label="포트폴리오 평가 요약">
                                <div className="summary-card">
                                    <span className="summary-label">현재 평가금액</span>
                                    <strong className="summary-value">
                                        <div>{formatUsd(usd.totalMarketValue)}</div>
                                        {krw ? <div className="summary-sub-value">+ {formatKrw(krw.totalMarketValue)}</div> : null}
                                    </strong>
                                    <small className="summary-meta">
                                        매입금액 {formatUsd(usd.totalPurchaseAmount)}
                                        {krw ? ` + ${formatKrw(krw.totalPurchaseAmount)}` : ""}
                                    </small>
                                </div>
                                <div className="summary-card">
                                    <span className="summary-label">평가손익</span>
                                    <strong className="summary-value">
                                        <div className={getProfitLossClassName(usd.totalUnrealizedProfitLoss)}>
                                            {usd.totalUnrealizedProfitLoss > 0 ? "▲ " : usd.totalUnrealizedProfitLoss < 0 ? "▼ " : ""}
                                            {formatUsd(usd.totalUnrealizedProfitLoss)}
                                        </div>
                                        {krw ? (
                                            <div className={`summary-sub-value ${getProfitLossClassName(krw.totalUnrealizedProfitLoss)}`}>
                                                {krw.totalUnrealizedProfitLoss > 0 ? "▲ " : krw.totalUnrealizedProfitLoss < 0 ? "▼ " : ""}
                                                + {formatKrw(krw.totalUnrealizedProfitLoss)}
                                            </div>
                                        ) : null}
                                    </strong>
                                    <small className="summary-meta">미실현 기준</small>
                                </div>
                                <div className="summary-card">
                                    <span className="summary-label">수익률</span>
                                    <strong className="summary-value">
                                        <div className={getProfitLossClassName(usd.totalReturnRate)}>
                                            {usd.totalReturnRate > 0 ? "▲ " : usd.totalReturnRate < 0 ? "▼ " : ""}
                                            {formatPercent(usd.totalReturnRate)}{krw ? " (USD)" : ""}
                                        </div>
                                        {krw ? (
                                            <div className={`summary-sub-value ${getProfitLossClassName(krw.totalReturnRate)}`}>
                                                {krw.totalReturnRate > 0 ? "▲ " : krw.totalReturnRate < 0 ? "▼ " : ""}
                                                {formatPercent(krw.totalReturnRate)} (KRW)
                                            </div>
                                        ) : null}
                                    </strong>
                                    <small className="summary-meta">보유 종목 {data.holdingValuations.length}개</small>
                                </div>
                            </section>
                            
                            <section className="content-section holdings-section">
                                <div className="section-heading">
                                    <h2>주요 보유 종목</h2>
                                    <Link className="section-link" to="/holdings">전체 보기</Link>
                                </div>
                                {data.holdingValuations.length > 0 ? (
                                    <div className="holding-summary-wrapper">
                                        <HoldingSummaryList holdings={data.holdingValuations}/>
                                    </div>
                                ) : (
                                    <div className="status-box status-empty">
                                        <p className="status-text">아직 등록된 매매 기록이 없습니다.</p>
                                        <div className="status-action">
                                            <Link className="accent-action" to="/transactions">매매 기록 등록</Link>
                                        </div>
                                    </div>
                                )}
                            </section>
                        </>
                    );
                }}
            />

            <section className="content-section risk-section">
                <div className="section-heading">
                    <h2>확인 필요</h2>
                    {!alertsResource.isLoading && !alertsResource.error && (
                        <span className="risk-count">{(alertsResource.data?.length ?? 0)}건</span>
                    )}
                </div>
                <ResourceBoundary 
                    resource={alertsResource}
                    loadingMessage="위험 경고를 불러오는 중입니다."
                    renderData={(data) => (
                        data.length > 0 ? (
                            <ul className="risk-alert-list">
                                {data.map((alert) => <RiskAlertItem key={`${alert.market}-${alert.ticker}`} alert={alert}/>)}
                            </ul>
                        ) : (
                            <div className="status-box status-empty">
                                <p className="status-text">현재 위험 한도를 넘는 보유 종목이 없습니다.</p>
                            </div>
                        )
                    )}
                />
            </section>
        </div>
    );
}
