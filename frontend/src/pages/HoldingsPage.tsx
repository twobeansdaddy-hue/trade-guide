import {Link, useLocation} from "react-router-dom";
import {useState} from "react";
import RequestError from "../components/common/RequestError";
import TransactionHistoryList from "../components/trade/TransactionHistoryList";
import HoldingValuationList from "../components/valuation/HoldingValuationList";
import {deleteTradeTransaction, getTradeTransactions} from "../api/tradeTransactionApi";
import {getPortfolioValuation} from "../api/portfolioValuationApi";
import {usePortfolioContext} from "../context/portfolioContext";
import {usePortfolioResource} from "../hooks/usePortfolioResource";
import type {TradeTransaction} from "../types/tradeTransaction";

export default function HoldingsPage() {
    const location = useLocation();
    const {memberId, selectedPortfolioId} = usePortfolioContext();

    if (selectedPortfolioId === null) return null;

    return <HoldingsContent key={`${memberId}-${selectedPortfolioId}`} memberId={memberId} portfolioId={selectedPortfolioId} successMessage={(location.state as {successMessage?: string} | null)?.successMessage}/>;
}

function HoldingsContent({memberId, portfolioId, successMessage}: {memberId: number; portfolioId: number; successMessage?: string}) {
    const valuationResource = usePortfolioResource(memberId, portfolioId, getPortfolioValuation);
    const transactionResource = usePortfolioResource(memberId, portfolioId, getTradeTransactions);
    const [deletingTransactionId, setDeletingTransactionId] = useState<number | null>(null);
    const [deleteErrorMessage, setDeleteErrorMessage] = useState<string | null>(null);
    const [deleteSuccessMessage, setDeleteSuccessMessage] = useState<string | null>(null);

    async function handleDelete(transaction: TradeTransaction) {
        const isConfirmed = window.confirm(
            `${transaction.ticker} ${transaction.tradeType === "BUY" ? "매수" : "매도"} 기록을 삭제할까요?`,
        );
        if (!isConfirmed) return;

        setDeleteErrorMessage(null);
        setDeleteSuccessMessage(null);
        setDeletingTransactionId(transaction.id);

        try {
            await deleteTradeTransaction(memberId, portfolioId, transaction.id);
            valuationResource.refresh();
            transactionResource.refresh();
            setDeleteSuccessMessage("매매 기록을 삭제했습니다.");
        } catch (reason) {
            setDeleteErrorMessage(
                reason instanceof Error ? reason.message : "매매 기록을 삭제하지 못했습니다.",
            );
        } finally {
            setDeletingTransactionId(null);
        }
    }

    return <><header className="page-header dashboard-header"><div><p className="eyebrow">PORTFOLIO {portfolioId}</p><h1>보유 종목</h1><p>평가 API가 제공하는 현재 평가 기준의 보유 현황입니다.</p></div><Link className="quiet-action" to="/transactions/new">매매 기록 등록</Link></header>{successMessage ? <p className="form-success-message" role="status">{successMessage}</p> : null}{deleteSuccessMessage ? <p className="form-success-message" role="status">{deleteSuccessMessage}</p> : null}{deleteErrorMessage ? <p className="form-error-message" role="alert">{deleteErrorMessage}</p> : null}{valuationResource.isLoading ? <p className="status-message" aria-live="polite">보유 종목을 불러오는 중입니다.</p> : valuationResource.error ? <RequestError message={valuationResource.error.message} onRetry={valuationResource.refresh} retryLabel="보유 종목 다시 시도"/> : valuationResource.data ? <HoldingValuationList holdings={valuationResource.data.holdingValuations}/> : null}<section className="content-section transaction-history-section"><div className="section-heading"><div><p className="section-label">TRANSACTION HISTORY</p><h2>매매 기록</h2></div></div>{transactionResource.isLoading ? <p className="status-message" aria-live="polite">매매 기록을 불러오는 중입니다.</p> : transactionResource.error ? <RequestError message={transactionResource.error.message} onRetry={transactionResource.refresh} retryLabel="매매 기록 다시 시도"/> : transactionResource.data ? <TransactionHistoryList transactions={transactionResource.data} deletingTransactionId={deletingTransactionId} onDelete={handleDelete}/> : null}</section></>;
}
