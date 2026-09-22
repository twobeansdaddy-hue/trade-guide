import {useCallback, useEffect, useState, type FormEvent} from "react";
import {hasApiStatus} from "../api/apiError";
import {
    getMarketDataProviders,
    getPortfolioMarketDataPreference,
    updatePortfolioMarketDataPreference,
} from "../api/marketDataProviderApi";
import {getPortfolioExposures, getPortfolioRiskPolicy, updatePortfolioRiskPolicy} from "../api/portfolioRiskApi";
import RequestError from "../components/common/RequestError";
import AccordionSection from "../components/strategy/AccordionSection";
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
    const [stopLoss, setStopLoss] = useState("");
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
        setStopLoss(data.stopLossRatio == null ? "" : String(data.stopLossRatio * 100));
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
        } else if (window.location.hash === "#risk-policy") {
            document.getElementById("risk-policy")?.scrollIntoView({behavior: "smooth", block: "start"});
        }
    }, []);

    const submit = async (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        setError(null);
        setSuccess(null);

        const loss = Number(maxLoss);
        const exposure = Number(maxExposure);
        const stopLossValue = stopLoss.trim() === "" ? null : Number(stopLoss);
        if (![loss, exposure].every((value) => Number.isFinite(value) && value > 0 && value <= 100)) {
            setError("위험 한도는 0보다 크고 100 이하여야 합니다.");
            return;
        }
        if (stopLossValue !== null && !(Number.isFinite(stopLossValue) && stopLossValue > 0 && stopLossValue < 100)) {
            setError("손절 기준은 비워 두거나 0보다 크고 100%보다 작게 입력해 주세요.");
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
                stopLossRatio: stopLossValue === null ? null : stopLossValue / 100,
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
                <p>위험 한도와 손절 기준은 가이드 검토에 사용하며, 증권사 주문을 자동으로 전송하지 않습니다.</p>
            </header>
            {policy ? (
                <p className="setting-summary">
                    현재 주문당 최대 손실 {formatRatio(policy.maxLossPerTradeRatio)} · 종목당 최대 노출 {formatRatio(policy.maxSingleAssetExposureRatio)} · 손절 기준 {policy.stopLossRatio == null ? "미설정" : formatRatio(policy.stopLossRatio)}
                </p>
            ) : null}
            {policyResource.isLoading ? (
                <p className="status-message" aria-live="polite">위험 한도를 불러오는 중입니다.</p>
            ) : isPolicyMissing ? (
                <p className="empty-state">위험 한도를 아직 설정하지 않았습니다. 아래에서 기준을 입력해 저장해 주세요.</p>
            ) : policyResource.error ? (
                <RequestError message={policyResource.error.message} onRetry={policyResource.refresh} retryLabel="위험 한도 다시 시도"/>
            ) : null}
            <AccordionSection
                id="risk-policy"
                className="risk-policy-accordion"
                sectionLabel="RISK LIMITS"
                title="위험 한도 변경"
                defaultOpen={true}
                summary={
                    policy
                        ? `손실 ${formatRatio(policy.maxLossPerTradeRatio)} · 노출 ${formatRatio(policy.maxSingleAssetExposureRatio)} · 손절 ${policy.stopLossRatio == null ? "미설정" : formatRatio(policy.stopLossRatio)}`
                        : "미설정"
                }
            >
                <form className="risk-policy-editor" onSubmit={submit}>
                    <div style={{display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(230px, 1fr))", gap: "24px"}}>
                        <label className="form-field-group">
                            <span className="form-field-label">주문당 최대 손실</span>
                            <span className="form-field-hint">포트폴리오 총 평가액 대비 1회 주문 최대 손실</span>
                            <div className="input-with-unit">
                                <input
                                    className="form-input"
                                    type="number"
                                    value={maxLoss}
                                    onChange={(event) => {
                                        setMaxLoss(event.target.value);
                                        setSuccess(null);
                                    }}
                                    min="0"
                                    max="100"
                                    step="0.01"
                                    placeholder="2.0"
                                />
                                <span className="input-unit">%</span>
                            </div>
                        </label>
                        <label className="form-field-group">
                            <span className="form-field-label">종목당 최대 노출</span>
                            <span className="form-field-hint">포트폴리오 총 평가액 대비 단일 종목 최대 비중</span>
                            <div className="input-with-unit">
                                <input
                                    className="form-input"
                                    type="number"
                                    value={maxExposure}
                                    onChange={(event) => {
                                        setMaxExposure(event.target.value);
                                        setSuccess(null);
                                    }}
                                    min="0"
                                    max="100"
                                    step="0.01"
                                    placeholder="20.0"
                                />
                                <span className="input-unit">%</span>
                            </div>
                        </label>
                        <label className="form-field-group">
                            <span className="form-field-label">손절 기준 (선택)</span>
                            <span className="form-field-hint">기준가에서 차감해 검토용 손절가 계산</span>
                            <div className="input-with-unit">
                                <input
                                    className="form-input"
                                    type="number"
                                    value={stopLoss}
                                    onChange={(event) => {
                                        setStopLoss(event.target.value);
                                        setSuccess(null);
                                    }}
                                    min="0"
                                    max="99.99"
                                    step="0.01"
                                    placeholder="25.0"
                                />
                                <span className="input-unit">%</span>
                            </div>
                        </label>
                    </div>
                    <div className="form-feedback-area" aria-live="polite" style={{marginTop: "16px"}}>
                        {error ? <p className="form-error-message" role="alert">{error}</p> : null}
                        {success ? <p className="form-success-message" role="status">{success}</p> : null}
                    </div>
                    <div className="form-actions-inline" style={{marginTop: "16px"}}>
                        <button type="submit" className="btn-submit" disabled={isSaving}>
                            {isSaving ? "저장 중..." : "위험 한도 저장"}
                        </button>
                    </div>
                </form>
            </AccordionSection>
            <AccordionSection
                id="market-data-provider"
                className="market-data-provider-section"
                sectionLabel="MARKET DATA"
                title="시장 데이터 제공자"
                defaultOpen={false}
                summary={
                    selectedProviderCapability
                        ? selectedProviderCapability.displayName
                        : selectedMarketDataProvider || "설정"
                }
            >
                <p className="section-description">현재가, 캔들, 종목 정보에 사용할 제공자를 선택합니다. 연결이 필요한 제공자는 계좌 연결 기능이 준비된 뒤 선택할 수 있습니다.</p>
                {marketDataProvidersResource.isLoading || marketDataPreferenceResource.isLoading ? (
                    <p className="status-message" aria-live="polite">시장 데이터 제공자 설정을 불러오는 중입니다.</p>
                ) : marketDataProvidersResource.error ? (
                    <RequestError message={marketDataProvidersResource.error.message} onRetry={marketDataProvidersResource.refresh} retryLabel="제공자 목록 다시 시도"/>
                ) : marketDataPreferenceResource.error ? (
                    <RequestError message={marketDataPreferenceResource.error.message} onRetry={marketDataPreferenceResource.refresh} retryLabel="제공자 설정 다시 시도"/>
                ) : (
                    <>
                        <form className="market-data-provider-editor" onSubmit={submitMarketDataPreference} style={{display: "flex", alignItems: "center", gap: "16px"}}>
                            <div style={{flex: 1}}>
                                <select className="form-input" value={selectedMarketDataProvider} onChange={(event) => setSelectedMarketDataProvider(event.target.value as MarketDataProvider)} style={{width: "250px"}}>
                                    {marketDataProvidersResource.data?.map((capability) => (
                                        <option key={capability.provider} value={capability.provider} disabled={!capability.selectable}>
                                            {capability.displayName}{capability.selectable ? "" : " (준비 중)"}
                                        </option>
                                    ))}
                                </select>
                                <span style={{marginLeft: "12px", fontSize: "13px", color: "var(--muted)"}}>
                                    {selectedProviderCapability ? `지원 시장: ${selectedProviderCapability.supportedMarkets.join(", ")}` : ""}
                                </span>
                            </div>
                            <button type="submit" className="btn-submit" disabled={isSavingMarketDataPreference || !selectedProviderCapability?.selectable}>
                                {isSavingMarketDataPreference ? "변경 중..." : "변경"}
                            </button>
                        </form>
                        {(marketDataError || marketDataSuccess) && (
                            <div className="form-feedback-area" aria-live="polite" style={{marginTop: "8px"}}>
                                {marketDataError ? <p className="form-error-message" role="alert" style={{color: "var(--loss)"}}>{marketDataError}</p> : null}
                                {marketDataSuccess ? <p className="form-success-message" role="status" style={{color: "var(--gain)"}}>{marketDataSuccess}</p> : null}
                            </div>
                        )}
                    </>
                )}
            </AccordionSection>
            <AccordionSection
                id="portfolio-exposures"
                className="exposure-section"
                sectionLabel="CURRENT ALLOCATION"
                title="보유 종목 비중"
                defaultOpen={false}
                summary={
                    exposureResource.data && exposureResource.data.length > 0
                        ? `종목 ${exposureResource.data.length}건`
                        : undefined
                }
            >
                {exposureResource.isLoading ? (
                    <p className="status-message" aria-live="polite">보유 종목 비중을 불러오는 중입니다.</p>
                ) : exposureResource.error ? (
                    <RequestError message={exposureResource.error.message} onRetry={exposureResource.refresh} retryLabel="보유 종목 비중 다시 시도"/>
                ) : exposureResource.data ? (
                    <PortfolioExposureList exposures={exposureResource.data} maximumExposureRate={maximumExposureRate}/>
                ) : null}
            </AccordionSection>
        </>
    );
}
