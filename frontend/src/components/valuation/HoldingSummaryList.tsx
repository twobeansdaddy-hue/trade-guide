import type {HoldingValuation} from "../../types/portfolioValuation";
import {formatPercent, formatUsd, getProfitLossClassName} from "../../utils/format";

type Props = {
    holdings: HoldingValuation[];
};

const DASHBOARD_HOLDING_LIMIT = 3;

export default function HoldingSummaryList({holdings}: Props) {
    const topHoldings = [...holdings]
        .sort((left, right) => right.marketValue - left.marketValue)
        .slice(0, DASHBOARD_HOLDING_LIMIT);

    return <ul className="holding-summary-list" aria-label="평가금액 기준 주요 보유 종목">
        {topHoldings.map((holding) => <li key={`${holding.market}-${holding.ticker}`}>
            <div className="holding-summary-asset">
                <div className="asset-cell">
                    <span className="market-badge">{holding.market}</span>
                    <strong>{holding.ticker}</strong>
                </div>
                <span>평가금액 {formatUsd(holding.marketValue)}</span>
            </div>
            <div className="holding-summary-performance">
                <span>수익률</span>
                <strong className={getProfitLossClassName(holding.returnRate)}>
                    {formatPercent(holding.returnRate)}
                </strong>
            </div>
        </li>)}
    </ul>;
}
