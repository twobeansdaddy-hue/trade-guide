import {useState} from "react";
import {getPortfolioAssetBacktest} from "../../api/backtestApi";
import RequestError from "../common/RequestError";
import type {PortfolioAssetBacktest} from "../../types/backtest";
import {formatPercent, formatUsd, getProfitLossClassName} from "../../utils/format";

type Props = {
    memberId: number;
    portfolioId: number;
    market: string;
    ticker: string;
    strategyId?: string;
};

export default function StrategyBacktestSection({
    memberId,
    portfolioId,
    market,
    ticker,
    strategyId,
}: Props) {
    const isTrackA =
        strategyId === "track-a-weekly-ma-crossover" ||
        (strategyId?.toLowerCase().startsWith("track-a") ?? false);

    const [initialCashInput, setInitialCashInput] = useState("10000");
    const [inputError, setInputError] = useState<string | null>(null);
    const [isLoading, setIsLoading] = useState(false);
    const [error, setError] = useState<Error | null>(null);
    const [data, setData] = useState<PortfolioAssetBacktest | null>(null);

    if (!isTrackA) {
        return (
            <div className="guide-backtest-section">
                <div className="backtest-unavailable-notice" role="status">
                    <p>TRACK_A 종목만 주봉 교차 백테스트를 실행할 수 있습니다.</p>
                </div>
            </div>
        );
    }

    const runBacktest = async (cashToRun: number) => {
        setIsLoading(true);
        setError(null);
        setInputError(null);
        try {
            const result = await getPortfolioAssetBacktest(
                memberId,
                portfolioId,
                market,
                ticker,
                cashToRun,
            );
            setData(result);
        } catch (err) {
            setError(err instanceof Error ? err : new Error(String(err)));
        } finally {
            setIsLoading(false);
        }
    };

    const handleRunClick = () => {
        const raw = initialCashInput.trim();
        if (!raw) {
            setInputError("초기 자산(USD)을 입력해주세요.");
            return;
        }
        const parsed = Number(raw);
        if (Number.isNaN(parsed) || parsed <= 0) {
            setInputError("초기 자산은 0보다 큰 금액이어야 합니다.");
            return;
        }
        void runBacktest(parsed);
    };

    const handleRetry = () => {
        const parsed = Number(initialCashInput.trim());
        if (!Number.isNaN(parsed) && parsed > 0) {
            void runBacktest(parsed);
        } else {
            handleRunClick();
        }
    };

    return (
        <div className="guide-backtest-section">
            <div className="guide-backtest-header">
                <div className="guide-backtest-title-group">
                    <h3 className="guide-backtest-title">주봉 교차 백테스트</h3>
                    <span className="guide-backtest-badge">TRACK A 10/40주</span>
                </div>
                {data ? (
                    <button
                        type="button"
                        className="secondary-button backtest-close-button"
                        onClick={() => setData(null)}
                    >
                        결과 닫기
                    </button>
                ) : null}
            </div>

            <div className="guide-backtest-form">
                <div className="guide-backtest-field">
                    <label htmlFor={`backtest-initial-cash-${market}-${ticker}`}>
                        초기 자산 (USD)
                    </label>
                    <input
                        id={`backtest-initial-cash-${market}-${ticker}`}
                        type="number"
                        min="1"
                        step="any"
                        value={initialCashInput}
                        onChange={(e) => {
                            setInitialCashInput(e.target.value);
                            if (inputError) setInputError(null);
                        }}
                        disabled={isLoading}
                        placeholder="10000"
                    />
                </div>
                <button
                    type="button"
                    className="primary-button backtest-run-button"
                    onClick={handleRunClick}
                    disabled={isLoading}
                >
                    {isLoading ? "실행 중..." : "백테스트 실행"}
                </button>
            </div>

            {inputError ? (
                <p className="backtest-validation-error" role="alert">
                    {inputError}
                </p>
            ) : null}

            {isLoading ? (
                <p className="status-message backtest-loading-message" aria-live="polite">
                    백테스트를 실행하고 있습니다...
                </p>
            ) : null}

            {error && !isLoading ? (
                <RequestError
                    message={error.message}
                    onRetry={handleRetry}
                    retryLabel="백테스트 다시 시도"
                    className="backtest-request-error"
                />
            ) : null}

            {data && !isLoading ? (
                <div className="backtest-results-panel">
                    <div className="backtest-meta-banner">
                        <span className="backtest-data-date">
                            시세 데이터 기준일 <strong>{data.dataAsOfDate}</strong>
                        </span>
                        <span className="backtest-assumptions">
                            수수료 {data.result.assumptions.feeRate}%, 슬리피지 {data.result.assumptions.slippageRate}% 가정
                        </span>
                    </div>

                    <dl className="backtest-metrics-grid">
                        <div className="backtest-metric-card">
                            <dt>백테스트 기간</dt>
                            <dd>{data.result.periodStart} ~ {data.result.periodEnd}</dd>
                        </div>
                        <div className="backtest-metric-card">
                            <dt>누적 수익률</dt>
                            <dd className={getProfitLossClassName(data.result.cumulativeReturnRate)}>
                                {data.result.cumulativeReturnRate > 0 ? "+" : ""}
                                {formatPercent(data.result.cumulativeReturnRate)}
                            </dd>
                        </div>
                        <div className="backtest-metric-card">
                            <dt>최대낙폭 (MDD)</dt>
                            <dd className={data.result.maxDrawdownRate > 0 ? "negative" : "neutral"}>
                                {data.result.maxDrawdownRate === 0
                                    ? "0.00%"
                                    : `-${formatPercent(Math.abs(data.result.maxDrawdownRate))}`}
                            </dd>
                        </div>
                        <div className="backtest-metric-card">
                            <dt>매매 횟수</dt>
                            <dd>{data.result.tradeCount}회</dd>
                        </div>
                        <div className="backtest-metric-card">
                            <dt>시작 자산</dt>
                            <dd>{formatUsd(data.result.startingPortfolioValue)}</dd>
                        </div>
                        <div className="backtest-metric-card">
                            <dt>최종 자산</dt>
                            <dd>{formatUsd(data.result.endingPortfolioValue)}</dd>
                        </div>
                    </dl>

                    <div className="backtest-trades-container">
                        <h4 className="backtest-trades-heading">
                            체결 이력 요약 ({data.result.trades.length}건)
                        </h4>
                        {data.result.trades.length === 0 ? (
                            <p className="empty-state backtest-empty-trades">
                                해당 기간 내 체결된 매매가 없습니다.
                            </p>
                        ) : (
                            <div className="backtest-table-wrap">
                                <table
                                    className="backtest-table"
                                    aria-label={`${ticker} 주봉 교차 백테스트 체결 이력`}
                                >
                                    <thead>
                                        <tr>
                                            <th scope="col">체결일</th>
                                            <th scope="col">구분</th>
                                            <th scope="col">체결가</th>
                                            <th scope="col">수량</th>
                                            <th scope="col">체결 후 현금</th>
                                            <th scope="col">체결 후 총 자산</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {data.result.trades.map((trade, idx) => (
                                            <tr key={`${trade.tradingDate}-${trade.type}-${idx}`}>
                                                <td>{trade.tradingDate}</td>
                                                <td>
                                                    <span className={`action-badge ${trade.type.toLowerCase()}`}>
                                                        {trade.type === "BUY" ? "매수" : "매도"}
                                                    </span>
                                                </td>
                                                <td>{formatUsd(trade.price)}</td>
                                                <td>
                                                    {trade.quantity.toLocaleString(undefined, {
                                                        maximumFractionDigits: 4,
                                                    })}주
                                                </td>
                                                <td>{formatUsd(trade.cashAfter)}</td>
                                                <td>{formatUsd(trade.portfolioValueAfter)}</td>
                                            </tr>
                                        ))}
                                    </tbody>
                                </table>
                            </div>
                        )}
                    </div>
                </div>
            ) : null}
        </div>
    );
}
