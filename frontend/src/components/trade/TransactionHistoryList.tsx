import {Link} from "react-router-dom";
import type {TradeTransaction, TradeTransactionSource} from "../../types/tradeTransaction";
import {formatDateTime, formatUsd} from "../../utils/format";

type Props = {
    transactions: TradeTransaction[];
    deletingTransactionId: number | null;
    onDelete: (transaction: TradeTransaction) => void;
};

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

export default function TransactionHistoryList({
    transactions,
    deletingTransactionId,
    onDelete,
}: Props) {
    if (transactions.length === 0) {
        return <p className="empty-state">등록된 매매 기록이 없습니다.</p>;
    }

    return <ul className="transaction-history-list">
        {transactions.map((transaction) => {
            const source: TradeTransactionSource = transaction.source ?? "MANUAL";
            const isBrokerOpeningBalance = source === "BROKER_OPENING_BALANCE";
            const isBrokerOrderHistory = source === "BROKER_ORDER_HISTORY";
            const isBrokerHoldingAdjustment = source === "BROKER_HOLDING_ADJUSTMENT";

            return <li key={transaction.id}>
            <div className="transaction-history-heading">
                <div className="guide-asset">
                    <span className="market-badge">{transaction.market}</span>
                    <h3>{transaction.ticker}</h3>
                </div>
                <div className="transaction-actions">
                    <span className={`transaction-type ${transaction.tradeType.toLowerCase()}`}>
                        <span className="sr-only">거래 유형: </span>{tradeTypeLabels[transaction.tradeType]}
                    </span>
                    <span className={`transaction-source ${source.toLowerCase()}`}>
                        <span className="sr-only">거래 출처: </span>{sourceLabels[source] ?? "직접 등록"}
                    </span>
                    {isBrokerOpeningBalance ? (
                        <Link
                            className="transaction-source-link"
                            to="/broker-accounts#broker-holding-imports"
                            aria-label={`${transaction.ticker} 증권사 개시 잔고 이력에서 취소`}
                        >
                            개시 잔고 이력에서 취소
                        </Link>
                    ) : isBrokerOrderHistory ? (
                        <Link
                            className="transaction-source-link"
                            to="/broker-accounts#broker-order-imports"
                            aria-label={`${transaction.ticker} 증권사 주문 이력에서 반영 취소`}
                        >
                            주문 이력에서 반영 취소
                        </Link>
                    ) : isBrokerHoldingAdjustment ? (
                        <Link
                            className="transaction-source-link"
                            to="/broker-accounts#broker-holding-adjustments"
                            aria-label={`${transaction.ticker} 증권사 잔고 조정 이력에서 반영 취소`}
                        >
                            잔고 조정 이력에서 반영 취소
                        </Link>
                    ) : (
                        <button
                            type="button"
                            className="transaction-delete-button"
                            onClick={() => onDelete(transaction)}
                            disabled={deletingTransactionId !== null}
                            aria-label={`${transaction.ticker} ${tradeTypeLabels[transaction.tradeType]} 기록 삭제`}
                        >
                            {deletingTransactionId === transaction.id ? "삭제 중..." : "삭제"}
                        </button>
                    )}
                </div>
            </div>
            <dl>
                <div><dt>{isBrokerOpeningBalance || isBrokerHoldingAdjustment ? "반영 시각" : "체결 시각"}</dt><dd>{formatDateTime(transaction.tradedAt)}</dd></div>
                <div><dt>수량</dt><dd>{transaction.quantity}</dd></div>
                <div><dt>{isBrokerOpeningBalance || isBrokerHoldingAdjustment ? "증권사 평단가" : "체결 단가"}</dt><dd>{formatUsd(transaction.executedPrice)}</dd></div>
                <div><dt>수수료</dt><dd>{formatUsd(transaction.fee)}</dd></div>
            </dl>
        </li>;
        })}
    </ul>;
}
