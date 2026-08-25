import {useEffect, useState, type FormEvent} from "react";
import {getPortfolioRiskAlerts, getPortfolioRiskPolicy, updatePortfolioRiskPolicy} from "./api/portfolioRiskApi";
import RiskAlertItem from "./components/risk/RiskAlertItem";
import type {PortfolioRiskAlert} from "./types/portfolioRisk";
import type {PortfolioRiskPolicy} from "./types/portfolioRiskPolicy";
import "./App.css";

const MEMBER_ID = 1;
const PORTFOLIO_ID = 1;

function formatRatio(ratio: number) {
    return `${(ratio * 100).toFixed(2)}%`;
}

function App() {
    const [riskAlerts, setRiskAlerts] = useState<PortfolioRiskAlert[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [errorMessage, setErrorMessage] = useState<string | null>(null);
    const [riskPolicyErrorMessage, setRiskPolicyErrorMessage] = useState<string | null>(null);
    const [riskPolicySuccessMessage, setRiskPolicySuccessMessage] = useState<string | null>(null);
    const alertCount = riskAlerts.length
    const [riskPolicy, setRiskPolicy] = useState<PortfolioRiskPolicy | null>(null);
    const [maxLossPerTradePercent, setMaxLossPerTradePercent] = useState("");
    const [maxSingleAssetExposurePercent, setMaxSingleAssetExposurePercent] = useState("");
    const [isSavingRiskPolicy, setIsSavingRiskPolicy] = useState(false);

    const loadRiskAlerts = async () => {
        try {
            const alerts = await getPortfolioRiskAlerts(MEMBER_ID, PORTFOLIO_ID);
            setRiskAlerts(alerts);
        } catch (error) {
            const message =
                error instanceof Error
                    ? error.message
                    : "위험 경고를 불러오는 중 오류가 발생했습니다.";

            setErrorMessage(message);
        }
    };

    const loadRiskPolicy = async () => {
        try {
            const riskPolicy = await getPortfolioRiskPolicy(MEMBER_ID, PORTFOLIO_ID);
            setRiskPolicy(riskPolicy);
            setMaxLossPerTradePercent(String(riskPolicy.maxLossPerTradeRatio * 100));
            setMaxSingleAssetExposurePercent(String(riskPolicy.maxSingleAssetExposureRatio * 100));
        } catch (error) {
            const message = error instanceof Error
                ? error.message
                : "위험 한도를 불러오지 못했습니다."
            setRiskPolicyErrorMessage(message);
        }
    }

    const handleRefresh = async () => {
        setIsLoading(true);
        setErrorMessage(null);
        setRiskPolicyErrorMessage(null);

        try {
            await Promise.all([
                loadRiskAlerts(),
                loadRiskPolicy()
            ]);
        } finally {
            setIsLoading(false);
        }
    }

    const handleRiskPolicySubmit = async (event: FormEvent<HTMLFormElement>,) => {
        event.preventDefault();
        setRiskPolicyErrorMessage(null);
        setRiskPolicySuccessMessage(null);

        const maxLossPerTrade = Number(maxLossPerTradePercent);
        const maxSingleAssetExposure = Number(maxSingleAssetExposurePercent);

        if (!Number.isFinite(maxLossPerTrade) ||
            !Number.isFinite(maxSingleAssetExposure) ||
            maxLossPerTrade <= 0 ||
            maxSingleAssetExposure <= 0 ||
            maxLossPerTrade > 100 ||
            maxSingleAssetExposure > 100) {
            setRiskPolicyErrorMessage("위험 한도는 0보다 크고 100 이하여야 합니다.",);
            return;
        }

        if (maxLossPerTrade > maxSingleAssetExposure) {
            setRiskPolicyErrorMessage("주문당 최대 손실은 종목당 최대 노출보다 클 수 없습니다.");
            return;
        }

        setIsSavingRiskPolicy(true);
        setRiskPolicyErrorMessage(null);

        try {
            const updatedRiskPolicy = await updatePortfolioRiskPolicy(
                MEMBER_ID,
                PORTFOLIO_ID,
                {
                    maxLossPerTradeRatio: maxLossPerTrade / 100,
                    maxSingleAssetExposureRatio: maxSingleAssetExposure / 100,
                },
            );

            setRiskPolicy(updatedRiskPolicy);
            setMaxLossPerTradePercent(String(updatedRiskPolicy.maxLossPerTradeRatio * 100),);
            setMaxSingleAssetExposurePercent(String(updatedRiskPolicy.maxSingleAssetExposureRatio * 100),);

            setRiskPolicySuccessMessage("위험 한도를 저장했습니다.",);

            await loadRiskAlerts();
        } catch (error) {
            setRiskPolicyErrorMessage(
                error instanceof Error
                    ? error.message
                    : "위험 한도를 저장하지 못했습니다.",
            );
        } finally {
            setIsSavingRiskPolicy(false);
        }

    };

    useEffect(() => {
        void getPortfolioRiskAlerts(MEMBER_ID, PORTFOLIO_ID)
            .then((alerts) => {
                setRiskAlerts(alerts)
            })
            .catch((error: unknown) => {
                const message =
                    error instanceof Error
                        ? error.message
                        : "위험 경고를 불러오는 중 오류가 발생했습니다."

                setErrorMessage(message)
            })
            .finally(() => {
                setIsLoading(false)
            })
    }, [])

    useEffect(() => {
        void getPortfolioRiskPolicy(MEMBER_ID, PORTFOLIO_ID)
            .then((riskPolicy) => {
                setRiskPolicy(riskPolicy);
                setMaxLossPerTradePercent(String(riskPolicy.maxLossPerTradeRatio * 100));
                setMaxSingleAssetExposurePercent(String(riskPolicy.maxSingleAssetExposureRatio * 100));
            })
            .catch((error: unknown) => {
                const message =
                    error instanceof Error
                        ? error.message
                        : "위험 한도를 불러오지 못했습니다."
                setRiskPolicyErrorMessage(message);
            })
    }, []);

    useEffect(() => {
        document.title = `Trade Guide | 위험 경고 ${alertCount}건`
    }, [alertCount]);

    return (
        <main className="app-shell">
            <header className="app-header">
                <p className="eyebrow">TRADE GUIDE</p>
                <h1>포트폴리오 위험 경고</h1>
                <p className="page-description">
                    설정한 최대 단일 종목 비중을 초과한 보유 종목입니다.
                </p>
                {riskPolicy ? (
                    <p className="risk-policy-summary">
                        설정된 종목당 최대 노출:{" "}
                        {formatRatio(riskPolicy.maxSingleAssetExposureRatio)}
                    </p>
                ) : null}
            </header>

            <form className="risk-policy-editor" aria-labelledby="risk-policy-editor-title"
                  onSubmit={handleRiskPolicySubmit}>
                <h2 id="risk-policy-editor-title">위험 한도 변경</h2>

                <label>
                    주문당 최대 손실 (%)
                    <input
                        type="number"
                        value={maxLossPerTradePercent}
                        onChange={(event) => setMaxLossPerTradePercent(event.target.value)}
                        min="0"
                        max="100"
                        step="0.01"
                    />
                </label>

                <label>
                    종목당 최대 노출 (%)
                    <input
                        type="number"
                        value={maxSingleAssetExposurePercent}
                        onChange={(event) => setMaxSingleAssetExposurePercent(event.target.value)}
                        min="0"
                        max="100"
                        step="0.01"
                    />
                </label>

                {riskPolicyErrorMessage ? (
                    <p className="form-error-message" role="alert">
                        {riskPolicyErrorMessage}
                    </p>
                ) : null}

                {riskPolicySuccessMessage ? (
                    <p className="form-success-message" role="status">
                        {riskPolicySuccessMessage}
                    </p>
                ) : null}

                <button type={"submit"} disabled={isSavingRiskPolicy}>
                    {isSavingRiskPolicy ? "저장 중..." : "위험 한도 저장"}
                </button>
            </form>

            <section className="risk-section" aria-labelledby="risk-alert-title">
                <div className="section-heading">
                    <div>
                        <p className="section-label">Portfolio {PORTFOLIO_ID}</p>
                        <h2 id="risk-alert-title">확인 필요</h2>
                    </div>

                    <div className="section-actions">
                        <span className="alert-count">
                            {isLoading ? "조회 중" : `${alertCount}건`}
                        </span>
                        <button
                            type="button"
                            className="refresh-button"
                            onClick={handleRefresh}
                            disabled={isLoading}
                            aria-label="위험 경고 새로고침"
                            title="위험 경고 새로고침"
                        >
                            ↻
                        </button>
                    </div>
                </div>

                {isLoading ? (
                    <p className="status-message" aria-live="polite">
                        위험 경고를 불러오는 중입니다.
                    </p>
                ) : errorMessage ? (
                    <p className="status-message error" role="alert">
                        {errorMessage}
                    </p>
                ) : alertCount > 0 ? (
                    <ul className="risk-alert-list">
                        {riskAlerts.map((alert) => (
                            <RiskAlertItem
                                key={`${alert.market}-${alert.ticker}`}
                                alert={alert}/>
                        ))}
                    </ul>
                ) : (
                    <p className="empty-state">현재 위험 경고가 없습니다.</p>
                )}
            </section>
        </main>
    );
}

export default App;