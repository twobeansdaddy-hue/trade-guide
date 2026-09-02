import {Link, useLocation} from "react-router-dom";
import RequestError from "../components/common/RequestError";
import TransactionHistoryList from "../components/trade/TransactionHistoryList";
import HoldingValuationList from "../components/valuation/HoldingValuationList";
import {getTradeTransactions} from "../api/tradeTransactionApi";
import {getPortfolioValuation} from "../api/portfolioValuationApi";
import {usePortfolioContext} from "../context/portfolioContext";
import {usePortfolioResource} from "../hooks/usePortfolioResource";

export default function HoldingsPage() {
    const location = useLocation();
    const {memberId, selectedPortfolioId} = usePortfolioContext();

    if (selectedPortfolioId === null) return null;

    return <HoldingsContent key={`${memberId}-${selectedPortfolioId}`} memberId={memberId} portfolioId={selectedPortfolioId} successMessage={(location.state as {successMessage?: string} | null)?.successMessage}/>;
}

function HoldingsContent({memberId, portfolioId, successMessage}: {memberId: number; portfolioId: number; successMessage?: string}) {
    const valuationResource = usePortfolioResource(memberId, portfolioId, getPortfolioValuation);
    const transactionResource = usePortfolioResource(memberId, portfolioId, getTradeTransactions);

    return <><header className="page-header dashboard-header"><div><p className="eyebrow">PORTFOLIO {portfolioId}</p><h1>보유 종목</h1><p>평가 API가 제공하는 현재 평가 기준의 보유 현황입니다.</p></div><Link className="quiet-action" to="/transactions/new">매매 기록 등록</Link></header>{successMessage ? <p className="form-success-message" role="status">{successMessage}</p> : null}{valuationResource.isLoading ? <p className="status-message" aria-live="polite">보유 종목을 불러오는 중입니다.</p> : valuationResource.error ? <RequestError message={valuationResource.error.message} onRetry={valuationResource.refresh} retryLabel="보유 종목 다시 시도"/> : valuationResource.data ? <HoldingValuationList holdings={valuationResource.data.holdingValuations}/> : null}<section className="content-section transaction-history-section"><div className="section-heading"><div><p className="section-label">TRANSACTION HISTORY</p><h2>매매 기록</h2></div></div>{transactionResource.isLoading ? <p className="status-message" aria-live="polite">매매 기록을 불러오는 중입니다.</p> : transactionResource.error ? <RequestError message={transactionResource.error.message} onRetry={transactionResource.refresh} retryLabel="매매 기록 다시 시도"/> : transactionResource.data ? <TransactionHistoryList transactions={transactionResource.data}/> : null}</section></>;
}
