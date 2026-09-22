import type {PortfolioRiskAlert} from "../../types/portfolioRisk";

type RiskAlertItemProps = {
    alert: PortfolioRiskAlert;
}

function RiskAlertItem({ alert }: RiskAlertItemProps) {
    return (
        <li className="risk-alert-item">
            <div className="risk-alert-info">
                <span className="risk-alert-ticker">{alert.ticker}</span>
                <span className="risk-count">비중 초과</span>
                <span className="risk-alert-msg">{alert.message}</span>
            </div>
            <div className="risk-alert-stats">
                현재 {formatRate(alert.exposureRate)} / 한도 {formatRate(alert.maxExposureRate)}
            </div>
        </li>
    );
}

function formatRate(rate: number) {
    return `${rate.toFixed(2)}%`;
}

export default RiskAlertItem;
