import {useCallback, useState, type FormEvent} from "react";
import {hasApiStatus} from "../api/apiError";
import {getPortfolioExposures, getPortfolioRiskPolicy, updatePortfolioRiskPolicy} from "../api/portfolioRiskApi";
import RequestError from "../components/common/RequestError";
import PortfolioExposureList from "../components/risk/PortfolioExposureList";
import {usePortfolioContext} from "../context/portfolioContext";
import {usePortfolioResource} from "../hooks/usePortfolioResource";
import type {PortfolioRiskPolicy} from "../types/portfolioRiskPolicy";
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
    const applyPolicy = useCallback((data: PortfolioRiskPolicy) => {
        setPolicy(data);
        setMaxLoss(String(data.maxLossPerTradeRatio * 100));
        setMaxExposure(String(data.maxSingleAssetExposureRatio * 100));
    }, []);
    const policyResource = usePortfolioResource(memberId, portfolioId, getPortfolioRiskPolicy, applyPolicy);
    const exposureResource = usePortfolioResource(memberId, portfolioId, getPortfolioExposures);
    const isPolicyMissing = hasApiStatus(policyResource.error, 404);

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

    const maximumExposureRate = policy
        ? policy.maxSingleAssetExposureRatio * 100
        : null;

    return <><header className="page-header"><p className="eyebrow">PORTFOLIO {portfolioId}</p><h1>설정</h1><p>위험 한도는 현재 경고 기준으로만 사용되며, 주문 수량이나 손절가를 자동으로 만들지 않습니다.</p></header>{policy ? <p className="setting-summary">현재 주문당 최대 손실 {formatRatio(policy.maxLossPerTradeRatio)} · 종목당 최대 노출 {formatRatio(policy.maxSingleAssetExposureRatio)}</p> : null}{policyResource.isLoading ? <p className="status-message" aria-live="polite">위험 한도를 불러오는 중입니다.</p> : isPolicyMissing ? <p className="empty-state">위험 한도를 아직 설정하지 않았습니다. 아래에서 기준을 입력해 저장해 주세요.</p> : policyResource.error ? <RequestError message={policyResource.error.message} onRetry={policyResource.refresh} retryLabel="위험 한도 다시 시도"/> : null}<form className="risk-policy-editor" onSubmit={submit}><h2>위험 한도 변경</h2><label>주문당 최대 손실 (%)<input type="number" value={maxLoss} onChange={(event) => setMaxLoss(event.target.value)} min="0" max="100" step="0.01"/></label><label>종목당 최대 노출 (%)<input type="number" value={maxExposure} onChange={(event) => setMaxExposure(event.target.value)} min="0" max="100" step="0.01"/></label>{error ? <p className="form-error-message" role="alert">{error}</p> : null}{success ? <p className="form-success-message" role="status">{success}</p> : null}<button type="submit" disabled={isSaving}>{isSaving ? "저장 중..." : "위험 한도 저장"}</button></form><section className="exposure-section"><div className="section-heading"><div><p className="section-label">CURRENT ALLOCATION</p><h2>보유 종목 비중</h2></div></div>{exposureResource.isLoading ? <p className="status-message" aria-live="polite">보유 종목 비중을 불러오는 중입니다.</p> : exposureResource.error ? <RequestError message={exposureResource.error.message} onRetry={exposureResource.refresh} retryLabel="보유 종목 비중 다시 시도"/> : exposureResource.data ? <PortfolioExposureList exposures={exposureResource.data} maximumExposureRate={maximumExposureRate}/> : null}</section></>;
}
