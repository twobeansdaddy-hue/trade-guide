import {Link, useLocation} from "react-router-dom";
import {useEffect} from "react";
import HoldingValuationList from "../components/valuation/HoldingValuationList";
import BrokerReconciliationSection from "../components/broker/BrokerReconciliationSection";
import { getPortfolioValuation } from "../api/portfolioValuationApi";
import { usePortfolioContext } from "../context/portfolioContext";
import { usePortfolioResource } from "../hooks/usePortfolioResource";
import { ResourceBoundary } from "../components/common/ResourceBoundary";
import { isMarketDataRateLimitExceeded, getApiRetryAfterSeconds } from "../api/apiError";

export default function HoldingsPage() {
    const location = useLocation();
    const {memberId, selectedPortfolioId, portfolios} = usePortfolioContext();

    if (selectedPortfolioId === null) return null;

    const portfolioName = portfolios.find(p => p.id === selectedPortfolioId)?.name || "보유 종목";

    return <HoldingsContent key={`${memberId}-${selectedPortfolioId}`} memberId={memberId} portfolioId={selectedPortfolioId} portfolioName={portfolioName} successMessage={(location.state as {successMessage?: string} | null)?.successMessage}/>;
}

function HoldingsContent({memberId, portfolioId, portfolioName, successMessage}: {memberId: number; portfolioId: number; portfolioName: string; successMessage?: string}) {
    const location = useLocation();
    const valuationResource = usePortfolioResource(memberId, portfolioId, getPortfolioValuation);
    const isValuationRateLimit = isMarketDataRateLimitExceeded(valuationResource.error);
    const rateLimitCooldown = getApiRetryAfterSeconds(valuationResource.error);

    useEffect(() => {
        if (location.hash === "#broker-reconciliation") {
            const timer = setTimeout(() => {
                const el = document.getElementById("broker-reconciliation");
                if (el) {
                    el.scrollIntoView({behavior: "smooth", block: "start"});
                }
            }, 100);
            return () => clearTimeout(timer);
        }
    }, [location.hash]);

    return (
        <div className="holdings-page">
            <header className="page-header dashboard-header">
                <div>
                    <h1>{portfolioName} - 보유 종목</h1>
                    <p>평가 API가 제공하는 현재 평가 기준의 보유 현황입니다.</p>
                </div>
                <Link className="accent-action" to="/transactions">매매 기록 등록</Link>
            </header>
            
            <div className="form-feedback-area" aria-live="polite" style={{marginBottom: "16px"}}>
                {successMessage ? <p className="form-success-message" role="status" style={{color: "var(--gain)", margin: 0}}>{successMessage}</p> : null}
            </div>

            {isValuationRateLimit && valuationResource.data && (
                <div className="global-rate-limit-banner">
                    <p>시장 데이터 요청 제한을 초과하여 이전 데이터를 표시합니다.</p>
                    <button onClick={() => valuationResource.refresh()}>
                        다시 시도 {rateLimitCooldown ? `(${rateLimitCooldown}초)` : ""}
                    </button>
                </div>
            )}

            <div style={{marginBottom: "32px"}}>
                <ResourceBoundary 
                    resource={valuationResource}
                    loadingMessage="보유 종목을 불러오는 중입니다."
                    renderData={(data) => (
                        <HoldingValuationList holdings={data.holdingValuations}/>
                    )}
                />
            </div>

            <div id="broker-reconciliation">
                <BrokerReconciliationSection memberId={memberId} portfolioId={portfolioId} />
            </div>
        </div>
    );
}
