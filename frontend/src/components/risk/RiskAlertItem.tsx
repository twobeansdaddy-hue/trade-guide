import type {PortfolioRiskAlert} from "../../types/portfolioRisk";

type RiskAlertItemProps = {
    alert: PortfolioRiskAlert;
}

function RiskAlertItem({ alert }: RiskAlertItemProps) {
    return (
        <li className="risk-alert-item">
            <div className="ticker-group">
                <span className="market-badge">{alert.market}</span>
                <strong>{alert.ticker}</strong>
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

            <p className="risk-message">{alert.message}</p>
        </li>
    );
}

function formatRate(rate: number) {
    return `${rate.toFixed(2)}%`;
}

export default RiskAlertItem;