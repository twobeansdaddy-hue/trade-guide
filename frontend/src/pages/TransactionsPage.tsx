import { useState } from "react";
import type { FormEvent } from "react";
import { Link } from "react-router-dom";
import { usePortfolioContext } from "../context/portfolioContext";
import { usePortfolioResource } from "../hooks/usePortfolioResource";
import { getTradeTransactions, createTradeTransaction, deleteTradeTransaction } from "../api/tradeTransactionApi";
import { ResourceBoundary } from "../components/common/ResourceBoundary";
import AssetSearchInput from "../components/asset/AssetSearchInput";
import type { Market, TradeType, TradeTransactionSource } from "../types/tradeTransaction";
import { formatDateTime, formatUsd } from "../utils/format";
import "../styles/pages/transactions.css";

const tradeTypeLabels = {
    BUY: "매수",
    SELL: "매도",
};

const sourceLabels: Record<TradeTransactionSource, string> = {
    MANUAL: "직접 등록",
    BROKER_OPENING_BALANCE: "증권사 개시 잔고",
    BROKER_ORDER_HISTORY: "증권사 주문 이력",
    BROKER_HOLDING_ADJUSTMENT: "증권사 잔고 조정",
};

function createDefaultTradeTime() {
    const now = new Date();
    now.setMinutes(now.getMinutes() - now.getTimezoneOffset());
    return now.toISOString().slice(0, 16);
}

export default function TransactionsPage() {
    const { memberId, selectedPortfolioId, portfolios } = usePortfolioContext();
    if (selectedPortfolioId === null) return null;
    
    const portfolioName = portfolios.find(p => p.id === selectedPortfolioId)?.name || "포트폴리오";
    
    return <TransactionsContent key={`${memberId}-${selectedPortfolioId}`} memberId={memberId} portfolioId={selectedPortfolioId} portfolioName={portfolioName} />;
}

function TransactionsContent({ memberId, portfolioId, portfolioName }: { memberId: number; portfolioId: number; portfolioName: string }) {
    const transactionResource = usePortfolioResource(memberId, portfolioId, getTradeTransactions);
    
    // Form state
    const [isFormOpen, setIsFormOpen] = useState(false);
    const market: Market = "US";
    const [ticker, setTicker] = useState("");
    const [tradeType, setTradeType] = useState<TradeType>("BUY");
    const [quantity, setQuantity] = useState("");
    const [executedPrice, setExecutedPrice] = useState("");
    const [fee, setFee] = useState("0");
    const [tradedAt, setTradedAt] = useState(createDefaultTradeTime);
    const [formError, setFormError] = useState<string | null>(null);
    const [formSuccess, setFormSuccess] = useState<string | null>(null);
    const [isSubmitting, setIsSubmitting] = useState(false);
    
    // Delete state
    const [deletingId, setDeletingId] = useState<number | null>(null);

    async function handleSubmit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        
        const normalizedTicker = ticker.trim().toUpperCase();
        const numericQuantity = Number(quantity);
        const numericPrice = Number(executedPrice);
        const numericFee = Number(fee);

        if (!normalizedTicker || ![numericQuantity, numericPrice, numericFee].every(Number.isFinite)
            || numericQuantity <= 0 || numericPrice <= 0 || numericFee < 0 || !tradedAt) {
            setFormError("종목, 수량, 단가, 수수료, 체결 시각을 올바르게 입력해 주세요.");
            setFormSuccess(null);
            return;
        }

        setFormError(null);
        setIsSubmitting(true);

        try {
            await createTradeTransaction(memberId, portfolioId, {
                market,
                ticker: normalizedTicker,
                tradeType,
                quantity: numericQuantity,
                executedPrice: numericPrice,
                fee: numericFee,
                tradedAt: new Date(tradedAt).toISOString(),
            });
            setFormSuccess("매매 기록을 등록했습니다.");
            
            // Reset form except defaults
            setTicker("");
            setQuantity("");
            setExecutedPrice("");
            setTradedAt(createDefaultTradeTime());
            setIsFormOpen(false);
            
            transactionResource.refresh();
        } catch (reason) {
            setFormError(reason instanceof Error ? reason.message : "매매 기록을 등록하지 못했습니다.");
        } finally {
            setIsSubmitting(false);
        }
    }

    async function handleDelete(id: number) {
        setFormError(null);
        setFormSuccess(null);
        setDeletingId(id);
        
        try {
            await deleteTradeTransaction(memberId, portfolioId, id);
            transactionResource.refresh();
            setFormSuccess("매매 기록을 삭제했습니다.");
        } catch (reason) {
            setFormError(reason instanceof Error ? reason.message : "매매 기록을 삭제하지 못했습니다.");
        } finally {
            setDeletingId(null);
        }
    }

    return (
        <div className="transactions-page">
            <header className="page-header dashboard-header">
                <div>
                    <h1>{portfolioName} - 매매 기록</h1>
                    <p>실제 주문을 전송하지 않습니다. 체결된 내역을 기록해 보유 현황과 평가를 계산합니다.</p>
                </div>
                <button className="accent-action" onClick={() => setIsFormOpen(!isFormOpen)} style={{border: 'none', cursor: 'pointer'}}>
                    {isFormOpen ? "폼 닫기" : "기록 등록"}
                </button>
            </header>
            
            <div className="form-feedback-area" aria-live="polite" style={{marginBottom: "16px"}}>
                {formSuccess ? <p className="form-success-message" role="status" style={{color: "var(--gain)", margin: 0}}>{formSuccess}</p> : null}
                {formError ? <p className="form-error-message" role="alert" style={{color: "var(--loss)", margin: 0}}>{formError}</p> : null}
            </div>

            {isFormOpen && (
                <div className="transaction-form-card">
                    <form className="transaction-form" onSubmit={handleSubmit}>
                        <div className="form-fields">
                            <div className="form-field-group market-scope">
                                <label className="form-field-label">시장</label>
                                <div className="form-readonly-value">미국 주식 (US)</div>
                            </div>
                            <div className="form-field-group">
                                <label htmlFor="trade-type" className="form-field-label">거래 유형</label>
                                <select id="trade-type" className="form-input" value={tradeType} onChange={(e) => setTradeType(e.target.value as TradeType)}>
                                    <option value="BUY">매수</option>
                                    <option value="SELL">매도</option>
                                </select>
                            </div>
                            <div className="form-field-group">
                                <label htmlFor="asset-search-input" className="form-field-label">종목</label>
                                <AssetSearchInput market={market} ticker={ticker} onTickerChange={setTicker} />
                            </div>
                            <div className="form-field-group">
                                <label htmlFor="trade-traded-at" className="form-field-label">체결 시각</label>
                                <input id="trade-traded-at" type="datetime-local" className="form-input" value={tradedAt} onChange={(e) => setTradedAt(e.target.value)} />
                            </div>
                            <div className="form-field-group">
                                <label htmlFor="trade-quantity" className="form-field-label">수량</label>
                                <input id="trade-quantity" type="number" className="form-input" value={quantity} onChange={(e) => setQuantity(e.target.value)} min="0" step="any" inputMode="decimal" placeholder="0" />
                            </div>
                            <div className="form-field-group">
                                <label htmlFor="trade-executed-price" className="form-field-label">체결 단가</label>
                                <input id="trade-executed-price" type="number" className="form-input" value={executedPrice} onChange={(e) => setExecutedPrice(e.target.value)} min="0" step="any" inputMode="decimal" placeholder="0.00" />
                            </div>
                            <div className="form-field-group">
                                <label htmlFor="trade-fee" className="form-field-label">수수료</label>
                                <input id="trade-fee" type="number" className="form-input" value={fee} onChange={(e) => setFee(e.target.value)} min="0" step="any" inputMode="decimal" />
                            </div>
                        </div>
                        <div className="form-actions-inline">
                            <button type="button" className="btn-cancel" onClick={() => setIsFormOpen(false)}>취소</button>
                            <button type="submit" className="btn-submit" disabled={isSubmitting}>{isSubmitting ? "등록 중..." : "등록"}</button>
                        </div>
                    </form>
                </div>
            )}

            <ResourceBoundary 
                resource={transactionResource}
                loadingMessage="매매 기록을 불러오는 중입니다."
                renderData={(data) => {
                    if (data.length === 0) {
                        return (
                            <div className="status-box status-empty">
                                <p className="status-text">등록된 매매 기록이 없습니다.</p>
                            </div>
                        );
                    }
                    
                    return (
                        <div className="transaction-list-container">
                            <ul className="transaction-list">
                                {data.map((transaction) => {
                                    const source = transaction.source ?? "MANUAL";
                                    const isBrokerRelated = source !== "MANUAL";
                                    const typeClass = transaction.tradeType === "BUY" ? "type-buy" : "type-sell";
                                    
                                    return (
                                        <li key={transaction.id} className="transaction-item">
                                            <div className="tx-col tx-type-ticker">
                                                <span className={`tx-type ${typeClass}`}>{tradeTypeLabels[transaction.tradeType]}</span>
                                                <span className="tx-ticker">{transaction.ticker}</span>
                                            </div>
                                            <div className="tx-col tx-source">
                                                <span className="tx-source-label">{sourceLabels[source]}</span>
                                            </div>
                                            <div className="tx-col tx-amount">
                                                <span className="tx-quantity">{transaction.quantity}주</span>
                                                <span className="tx-times">×</span>
                                                <span className="tx-price">{formatUsd(transaction.executedPrice)}</span>
                                            </div>
                                            <div className="tx-col tx-time">
                                                {formatDateTime(transaction.tradedAt)}
                                            </div>
                                            <div className="tx-col tx-actions">
                                                {isBrokerRelated ? (
                                                    <Link className="tx-link" to="/broker-accounts">연동 계좌에서 관리</Link>
                                                ) : deletingId === transaction.id ? (
                                                    <span className="tx-deleting">삭제 중...</span>
                                                ) : (
                                                    <TransactionDeleteConfirm onDelete={() => handleDelete(transaction.id)} />
                                                )}
                                            </div>
                                        </li>
                                    );
                                })}
                            </ul>
                        </div>
                    );
                }}
            />
        </div>
    );
}

function TransactionDeleteConfirm({ onDelete }: { onDelete: () => void }) {
    const [confirming, setConfirming] = useState(false);
    
    if (confirming) {
        return (
            <div className="tx-confirm-group">
                <button className="tx-btn tx-confirm" onClick={onDelete}>삭제 확인</button>
                <button className="tx-btn tx-cancel" onClick={() => setConfirming(false)}>취소</button>
            </div>
        );
    }
    
    return <button className="tx-btn tx-delete" onClick={() => setConfirming(true)}>삭제</button>;
}
