import type {PortfolioExposure} from "../../types/portfolioExposure";
import {formatPercent, formatUsd} from "../../utils/format";

type Props = {
    exposures: PortfolioExposure[];
    maximumExposureRate: number | null;
};

export default function PortfolioExposureList({exposures, maximumExposureRate}: Props) {
    if (exposures.length === 0) {
        return <p className="empty-state">현재 평가할 보유 종목이 없습니다.</p>;
    }

    return <ul className="exposure-list">
        {exposures.map((exposure) => {
            const exceedsLimit = maximumExposureRate !== null
                && exposure.exposureRate > maximumExposureRate;
            const barWidth = maximumExposureRate === null
                ? Math.min(exposure.exposureRate, 100)
                : Math.min((exposure.exposureRate / maximumExposureRate) * 100, 100);

            return <li key={`${exposure.market}-${exposure.ticker}`}>
                <div className="exposure-heading">
                    <div className="guide-asset">
                        <span className="market-badge">{exposure.market}</span>
                        <strong>{exposure.ticker}</strong>
                    </div>
                    <strong className={exceedsLimit ? "negative" : ""}>
                        {formatPercent(exposure.exposureRate)}
                    </strong>
                </div>
                <div className="exposure-bar" aria-hidden="true">
                    <span
                        className={exceedsLimit ? "exceeds-limit" : ""}
                        style={{width: `${barWidth}%`}}
                    />
                </div>
                <p>
                    평가금액 {formatUsd(exposure.marketValue)}
                    {maximumExposureRate !== null
                        ? ` · 설정 한도 ${formatPercent(maximumExposureRate)}`
                        : " · 위험 한도를 설정하면 기준과 비교할 수 있습니다."}
                </p>
            </li>;
        })}
    </ul>;
}
