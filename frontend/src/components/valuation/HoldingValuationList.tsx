import type {HoldingValuation} from "../../types/portfolioValuation";
import {formatPercent, formatUsd, getProfitLossClassName} from "../../utils/format";

type Props = { holdings: HoldingValuation[] };

export default function HoldingValuationList({holdings}: Props) {
    if (holdings.length === 0) {
        return <p className="empty-state">현재 평가할 보유 종목이 없습니다.</p>;
    }

    return (
        <>
            <div className="holding-table-wrap">
                <table className="holding-table">
                    <thead><tr><th scope="col">종목</th><th scope="col">수량</th><th scope="col">평단가</th><th scope="col">현재가</th><th scope="col">매입금액</th><th scope="col">평가금액</th><th scope="col">평가손익</th><th scope="col">수익률</th></tr></thead>
                    <tbody>{holdings.map((holding) => <tr key={`${holding.market}-${holding.ticker}`}>
                        <th scope="row"><div className="asset-cell"><span className="market-badge">{holding.market}</span><strong>{holding.ticker}</strong></div></th>
                        <td>{holding.quantity}</td><td>{formatUsd(holding.averagePurchasePrice)}</td><td>{formatUsd(holding.currentPrice)}</td><td>{formatUsd(holding.purchaseAmount)}</td><td>{formatUsd(holding.marketValue)}</td>
                        <td className={getProfitLossClassName(holding.unrealizedProfitLoss)}>{formatUsd(holding.unrealizedProfitLoss)}</td>
                        <td className={getProfitLossClassName(holding.returnRate)}>{formatPercent(holding.returnRate)}</td>
                    </tr>)}</tbody>
                </table>
            </div>
            <ul className="holding-cards">{holdings.map((holding) => <li key={`${holding.market}-${holding.ticker}`}>
                <div className="holding-card-heading"><div className="asset-cell"><span className="market-badge">{holding.market}</span><strong>{holding.ticker}</strong></div><strong className={getProfitLossClassName(holding.returnRate)}>{formatPercent(holding.returnRate)}</strong></div>
                <dl><div><dt>평가금액</dt><dd>{formatUsd(holding.marketValue)}</dd></div><div><dt>평가손익</dt><dd className={getProfitLossClassName(holding.unrealizedProfitLoss)}>{formatUsd(holding.unrealizedProfitLoss)}</dd></div><div><dt>수량</dt><dd>{holding.quantity}</dd></div><div><dt>평단가 / 현재가</dt><dd>{formatUsd(holding.averagePurchasePrice)} / {formatUsd(holding.currentPrice)}</dd></div></dl>
            </li>)}</ul>
        </>
    );
}
