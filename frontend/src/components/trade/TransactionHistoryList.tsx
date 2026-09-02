import type {TradeTransaction} from "../../types/tradeTransaction";
import {formatDateTime, formatUsd} from "../../utils/format";

type Props = {
    transactions: TradeTransaction[];
};

const tradeTypeLabels = {
    BUY: "매수",
    SELL: "매도",
};

export default function TransactionHistoryList({transactions}: Props) {
    if (transactions.length === 0) {
        return <p className="empty-state">등록된 매매 기록이 없습니다.</p>;
    }

    return <ul className="transaction-history-list">
        {transactions.map((transaction) => <li key={transaction.id}>
            <div className="transaction-history-heading">
                <div className="guide-asset">
                    <span className="market-badge">{transaction.market}</span>
                    <strong>{transaction.ticker}</strong>
                </div>
                <span className={`transaction-type ${transaction.tradeType.toLowerCase()}`}>
                    {tradeTypeLabels[transaction.tradeType]}
                </span>
            </div>
            <dl>
                <div><dt>체결 시각</dt><dd>{formatDateTime(transaction.tradedAt)}</dd></div>
                <div><dt>수량</dt><dd>{transaction.quantity}</dd></div>
                <div><dt>체결 단가</dt><dd>{formatUsd(transaction.executedPrice)}</dd></div>
                <div><dt>수수료</dt><dd>{formatUsd(transaction.fee)}</dd></div>
            </dl>
        </li>)}
    </ul>;
}
