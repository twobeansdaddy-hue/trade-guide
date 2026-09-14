import {useCallback, useEffect, useState, type FormEvent} from "react";
import {hasApiStatus} from "../api/apiError";
import {
    getMarketDataProviders,
    getPortfolioMarketDataPreference,
    updatePortfolioMarketDataPreference,
} from "../api/marketDataProviderApi";
import {getPortfolioExposures, getPortfolioRiskPolicy, updatePortfolioRiskPolicy} from "../api/portfolioRiskApi";
import RequestError from "../components/common/RequestError";
import BrokerConnectionSection from "../components/broker/BrokerConnectionSection";
import PortfolioExposureList from "../components/risk/PortfolioExposureList";
import {usePortfolioContext} from "../context/portfolioContext";
import {usePortfolioResource} from "../hooks/usePortfolioResource";
import type {PortfolioRiskPolicy} from "../types/portfolioRiskPolicy";
import type {
    MarketDataProvider,
    PortfolioMarketDataPreference,
} from "../types/marketDataProvider";
import {formatRatio} from "../utils/format";

export default function SettingsPage() {
    const {memberId, selectedPortfolioId} = usePortfolioContext();

    if (selectedPortfolioId === null) return null;

    return <SettingsContent key={`${memberId}-${selectedPortfolioId}`} memberId={memberId} portfolioId={selectedPortfolioId}/>;
}

function SettingsContent({memberId, portfolioId}: {memberId: number; portfolioId: number}) {
    const [policy, setPolicy] = useState<PortfolioRiskPolicy | null>(null);
    const [maxLoss, setMaxLoss] = useState("");
    const [maxExposure, setMaxExposure] = useState("");
    const [error, setError] = useState<string | null>(null);
    const [success, setSuccess] = useState<string | null>(null);
    const [isSaving, setIsSaving] = useState(false);
    const [selectedMarketDataProvider, setSelectedMarketDataProvider] = useState<MarketDataProvider | "">("");
    const [marketDataError, setMarketDataError] = useState<string | null>(null);
    const [marketDataSuccess, setMarketDataSuccess] = useState<string | null>(null);
    const [isSavingMarketDataPreference, setIsSavingMarketDataPreference] = useState(false);
    const applyPolicy = useCallback((data: PortfolioRiskPolicy) => {
        setPolicy(data);
        setMaxLoss(String(data.maxLossPerTradeRatio * 100));
        setMaxExposure(String(data.maxSingleAssetExposureRatio * 100));
    }, []);
    const policyResource = usePortfolioResource(memberId, portfolioId, getPortfolioRiskPolicy, applyPolicy);
    const exposureResource = usePortfolioResource(memberId, portfolioId, getPortfolioExposures);
    const applyMarketDataPreference = useCallback((data: PortfolioMarketDataPreference) => {
        setSelectedMarketDataProvider(data.priceProvider);
    }, []);
    const marketDataProvidersResource = usePortfolioResource(memberId, portfolioId, getMarketDataProviders);
    const marketDataPreferenceResource = usePortfolioResource(
        memberId,
        portfolioId,
        getPortfolioMarketDataPreference,
        applyMarketDataPreference,
    );
    const isPolicyMissing = hasApiStatus(policyResource.error, 404);
    const selectedProviderCapability = marketDataProvidersResource.data?.find(
        (capability) => capability.provider === selectedMarketDataProvider,
    );

    useEffect(() => {
        if (window.location.hash === "#market-data-provider") {
            document.getElementById("market-data-provider")?.scrollIntoView({behavior: "smooth", block: "start"});
        }
    }, []);

    const submit = async (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        setError(null);
        setSuccess(null);

        const loss = Number(maxLoss);
        const exposure = Number(maxExposure);
        if (![loss, exposure].every((value) => Number.isFinite(value) && value > 0 && value <= 100)) {
            setError("위험 한도는 0보다 크고 100 이하여야 합니다.");
            return;
        }
        if (loss > exposure) {
            setError("주문당 최대 손실은 종목당 최대 노출보다 클 수 없습니다.");
            return;
        }

        setIsSaving(true);
        try {
            const data = await updatePortfolioRiskPolicy(memberId, portfolioId, {
                maxLossPerTradeRatio: loss / 100,
                maxSingleAssetExposureRatio: exposure / 100,
            });
            policyResource.replaceData(data);
            setSuccess("위험 한도를 저장했습니다.");
        } catch (reason) {
            setError(reason instanceof Error ? reason.message : "위험 한도를 저장하지 못했습니다.");
        } finally {
            setIsSaving(false);
        }
    };

    const submitMarketDataPreference = async (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        setMarketDataError(null);
        setMarketDataSuccess(null);

        if (selectedMarketDataProvider === "") {
            setMarketDataError("시장 데이터 제공자를 선택해 주세요.");
            return;
        }

        setIsSavingMarketDataPreference(true);
        try {
            const data = await updatePortfolioMarketDataPreference(
                memberId,
                portfolioId,
                selectedMarketDataProvider,
            );
            marketDataPreferenceResource.replaceData(data);
            setMarketDataSuccess("시장 데이터 제공자 설정을 저장했습니다.");
        } catch (reason) {
            setMarketDataError(
                reason instanceof Error
                    ? reason.message
                    : "시장 데이터 제공자 설정을 저장하지 못했습니다.",
            );
        } finally {
            setIsSavingMarketDataPreference(false);
        }
    };

    const maximumExposureRate = policy
        ? policy.maxSingleAssetExposureRatio * 100
        : null;

    return (
        <>
            <header className="page-header">
                <p className="eyebrow">PORTFOLIO {portfolioId}</p>
                <h1>설정</h1>
                <p>위험 한도는 현재 경고 기준으로만 사용되며, 주문 수량이나 손절가를 자동으로 만들지 않습니다.</p>
            </header>
            {policy ? (
                <p className="setting-summary">
                    현재 주문당 최대 손실 {formatRatio(policy.maxLossPerTradeRatio)} · 종목당 최대 노출 {formatRatio(policy.maxSingleAssetExposureRatio)} (포트폴리오 평가액 기준)
                </p>
            ) : null}
            {policyResource.isLoading ? (
                <p className="status-message" aria-live="polite">위험 한도를 불러오는 중입니다.</p>
            ) : isPolicyMissing ? (
                <p className="empty-state">위험 한도를 아직 설정하지 않았습니다. 아래에서 기준을 입력해 저장해 주세요.</p>
            ) : policyResource.error ? (
                <RequestError message={policyResource.error.message} onRetry={policyResource.refresh} retryLabel="위험 한도 다시 시도"/>
            ) : null}
            <form className="risk-policy-editor" onSubmit={submit}>
                <h2>위험 한도 변경</h2>
                <label>
                    <span>주문당 최대 손실 (포트폴리오 대비 %)</span>
                    <span className="form-field-hint">포트폴리오 총 평가액 대비 1회 주문에서 감수할 최대 손실 비율</span>
                    <input
                        type="number"
                        value={maxLoss}
                        onChange={(event) => {
                            setMaxLoss(event.target.value);
                            setSuccess(null);
                        }}
                        min="0"
                        max="100"
                        step="0.01"
                        placeholder="예: 2.0"
                    />
                </label>
                <label>
                    <span>종목당 최대 노출 (포트폴리오 대비 %)</span>
                    <span className="form-field-hint">포트폴리오 총 평가액 대비 단일 종목의 최대 보유 비중</span>
                    <input
                        type="number"
                        value={maxExposure}
                        onChange={(event) => {
                            setMaxExposure(event.target.value);
                            setSuccess(null);
                        }}
                        min="0"
                        max="100"
                        step="0.01"
                        placeholder="예: 20.0"
                    />
                </label>
                <div className="form-feedback-area" aria-live="polite">
                    {error ? <p className="form-error-message" role="alert">{error}</p> : null}
                    {success ? <p className="form-success-message" role="status">{success}</p> : null}
                </div>
                <div className="form-actions">
                    <button type="submit" className="primary-button" disabled={isSaving}>
                        {isSaving ? "저장 중..." : "위험 한도 저장"}
                    </button>
                </div>
            </form>
            <section className="market-data-provider-section" id="market-data-provider">
                <div className="section-heading">
                    <div>
                        <p className="section-label">MARKET DATA</p>
                        <h2>시장 데이터 제공자</h2>
                    </div>
                </div>
                <p className="section-description">현재가, 캔들, 종목 정보에 사용할 제공자를 선택합니다. 연결이 필요한 제공자는 계좌 연결 기능이 준비된 뒤 선택할 수 있습니다.</p>
                {marketDataProvidersResource.isLoading || marketDataPreferenceResource.isLoading ? (
                    <p className="status-message" aria-live="polite">시장 데이터 제공자 설정을 불러오는 중입니다.</p>
                ) : marketDataProvidersResource.error ? (
                    <RequestError message={marketDataProvidersResource.error.message} onRetry={marketDataProvidersResource.refresh} retryLabel="제공자 목록 다시 시도"/>
                ) : marketDataPreferenceResource.error ? (
                    <RequestError message={marketDataPreferenceResource.error.message} onRetry={marketDataPreferenceResource.refresh} retryLabel="제공자 설정 다시 시도"/>
                ) : (
                    <form className="market-data-provider-editor" onSubmit={submitMarketDataPreference}>
                        <label>
                            시장 데이터 제공자
                            <select value={selectedMarketDataProvider} onChange={(event) => setSelectedMarketDataProvider(event.target.value as MarketDataProvider)}>
                                {marketDataProvidersResource.data?.map((capability) => (
                                    <option key={capability.provider} value={capability.provider} disabled={!capability.selectable}>
                                        {capability.displayName}{capability.selectable ? "" : " (준비 중)"}
                                    </option>
                                ))}
                            </select>
                        </label>
                        {selectedProviderCapability ? (
                            <p className="provider-description">
                                지원 시장: {selectedProviderCapability.supportedMarkets.join(", ")}
                                {selectedProviderCapability.requiresBrokerConnection ? " · 증권 계좌 연결 필요" : " · 서비스 제공 데이터"}
                                {!selectedProviderCapability.selectable ? " · 현재 선택할 수 없습니다." : ""}
                            </p>
                        ) : null}
                        <div className="form-feedback-area" aria-live="polite">
                            {marketDataError ? <p className="form-error-message" role="alert">{marketDataError}</p> : null}
                            {marketDataSuccess ? <p className="form-success-message" role="status">{marketDataSuccess}</p> : null}
                        </div>
                        <div className="form-actions">
                            <button type="submit" className="primary-button" disabled={isSavingMarketDataPreference || !selectedProviderCapability?.selectable}>
                                {isSavingMarketDataPreference ? "저장 중..." : "시장 데이터 제공자 저장"}
                            </button>
                        </div>
                    </form>
                )}
            </section>
            <BrokerConnectionSection memberId={memberId}/>
            <section className="exposure-section">
                <div className="section-heading">
                    <div>
                        <p className="section-label">CURRENT ALLOCATION</p>
                        <h2>보유 종목 비중</h2>
                    </div>
                </div>
                {exposureResource.isLoading ? (
                    <p className="status-message" aria-live="polite">보유 종목 비중을 불러오는 중입니다.</p>
                ) : exposureResource.error ? (
                    <RequestError message={exposureResource.error.message} onRetry={exposureResource.refresh} retryLabel="보유 종목 비중 다시 시도"/>
                ) : exposureResource.data ? (
                    <PortfolioExposureList exposures={exposureResource.data} maximumExposureRate={maximumExposureRate}/>
                ) : null}
            </section>
        </>
    );
}
