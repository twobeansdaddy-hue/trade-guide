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
        {topHoldings.map((holding) => <li key={`${holding.market}-${holding.ticker}`} className="holding-summary-item" style={{display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '16px 24px'}}>
            <div className="holding-summary-asset" style={{display: 'flex', alignItems: 'center', gap: '8px'}}>
                <strong style={{fontFamily: 'var(--head-font)', fontSize: '15px'}}>{holding.ticker}</strong>
                <span style={{fontSize: '13px', color: 'var(--muted)'}}>평가금액 {formatUsd(holding.marketValue)}</span>
            </div>
            <div className="holding-summary-performance" style={{display: 'flex', alignItems: 'center', gap: '8px', fontSize: '14px', fontFamily: "'IBM Plex Mono', monospace"}}>
                <strong className={getProfitLossClassName(holding.returnRate)}>
                    {holding.returnRate > 0 ? "▲ " : holding.returnRate < 0 ? "▼ " : ""}{formatPercent(holding.returnRate)}
                </strong>
            </div>
        </li>)}
    </ul>;
}
