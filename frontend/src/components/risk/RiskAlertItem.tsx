import type {PortfolioRiskAlert} from "../../types/portfolioRisk";

type RiskAlertItemProps = {
    alert: PortfolioRiskAlert;
}

function RiskAlertItem({ alert }: RiskAlertItemProps) {
    return (
        <li className="risk-alert-item">
            <div className="risk-asset">
                <div className="ticker-group"><span className="market-badge">{alert.market}</span><strong>{alert.ticker}</strong></div>
                <span className="risk-state">비중 초과</span>
            </div>

            <dl className="risk-details">
                <div>
                    <dt>현재 비중</dt>
                    <dd>{formatRate(alert.exposureRate)}</dd>
                </div>
                <div>
                    <dt>최대 비중</dt>
                    <dd>{formatRate(alert.maxExposureRate)}</dd>
                </div>
            </dl>

            <p className="risk-message">{alert.message} 설정에서 최대 노출 비중을 조정할 수 있습니다.</p>
        </li>
    );
}

function formatRate(rate: number) {
    return `${rate.toFixed(2)}%`;
}

export default RiskAlertItem;
