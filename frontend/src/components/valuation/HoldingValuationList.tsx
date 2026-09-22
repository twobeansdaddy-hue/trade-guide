import type {HoldingValuation} from "../../types/portfolioValuation";
import {formatPercent, formatUsd, getProfitLossClassName} from "../../utils/format";
import "../../styles/pages/holdings.css";

type Props = { holdings: HoldingValuation[] };

export default function HoldingValuationList({holdings}: Props) {
    if (holdings.length === 0) {
        return <div className="status-box status-empty"><p className="status-text">현재 평가할 보유 종목이 없습니다.</p></div>;
    }

    const totalMarketValue = holdings.reduce((sum, h) => sum + h.marketValue, 0);
    const totalPurchaseAmount = holdings.reduce((sum, h) => sum + h.purchaseAmount, 0);
    const totalReturnRate = totalPurchaseAmount > 0 ? (totalMarketValue - totalPurchaseAmount) / totalPurchaseAmount : 0;

    return (
        <div className="holding-list">
            {holdings.map((holding) => (
                <div key={`${holding.market}-${holding.ticker}`} className="holding-row">
                    <div className="holding-row-top">
                        <div className="holding-ticker-group">
                            <span className="holding-ticker">{holding.ticker}</span>
                            <span className="holding-name">{holding.market}</span>
                        </div>
                        <div className="holding-top-right">
                            <span className="holding-main-value">{formatUsd(holding.marketValue)}</span>
                            <span className={`holding-main-rate ${getProfitLossClassName(holding.returnRate)}`}>
                                {holding.returnRate > 0 ? "▲ " : holding.returnRate < 0 ? "▼ " : ""}{formatPercent(holding.returnRate)}
                            </span>
                        </div>
                    </div>
                    <div className="holding-row-bottom">
                        <div className="holding-stat">
                            <span className="holding-stat-label">수량</span>
                            <span className="holding-stat-value">{holding.quantity}</span>
                        </div>
                        <div className="holding-stat">
                            <span className="holding-stat-label">평단가</span>
                            <span className="holding-stat-value">{formatUsd(holding.averagePurchasePrice)}</span>
                        </div>
                        <div className="holding-stat">
                            <span className="holding-stat-label">현재가</span>
                            <span className="holding-stat-value">{formatUsd(holding.currentPrice)}</span>
                        </div>
                        <div className="holding-stat">
                            <span className="holding-stat-label">매입금액</span>
                            <span className="holding-stat-value">{formatUsd(holding.purchaseAmount)}</span>
                        </div>
                        <div className="holding-stat">
                            <span className="holding-stat-label">평가손익</span>
                            <span className={`holding-stat-value ${getProfitLossClassName(holding.unrealizedProfitLoss)}`}>
                                {holding.unrealizedProfitLoss > 0 ? "+" : ""}{formatUsd(holding.unrealizedProfitLoss)}
                            </span>
                        </div>
                    </div>
                </div>
            ))}
            <div className="holding-row holding-total">
                <div className="holding-row-top">
                    <div className="holding-ticker-group">
                        <span className="holding-ticker">합계</span>
                    </div>
                    <div className="holding-top-right">
                        <span className="holding-main-value">{formatUsd(totalMarketValue)}</span>
                        <span className={`holding-main-rate ${getProfitLossClassName(totalReturnRate)}`}>
                            {totalReturnRate > 0 ? "▲ " : totalReturnRate < 0 ? "▼ " : ""}{formatPercent(totalReturnRate)}
                        </span>
                    </div>
                </div>
            </div>
        </div>
    );
}
