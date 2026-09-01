import {useEffect, useState, type FormEvent} from "react";
import {getPortfolioRiskPolicy, updatePortfolioRiskPolicy} from "../api/portfolioRiskApi";
import {usePortfolioContext} from "../context/portfolioContext";
import type {PortfolioRiskPolicy} from "../types/portfolioRiskPolicy";
import {formatRatio} from "../utils/format";

export default function SettingsPage() {
    const {memberId, selectedPortfolioId} = usePortfolioContext();
    const [policy, setPolicy] = useState<PortfolioRiskPolicy | null>(null);
    const [maxLoss, setMaxLoss] = useState("");
    const [maxExposure, setMaxExposure] = useState("");
    const [error, setError] = useState<string | null>(null);
    const [success, setSuccess] = useState<string | null>(null);
    const [isSaving, setIsSaving] = useState(false);
    useEffect(() => { if (selectedPortfolioId !== null) void getPortfolioRiskPolicy(memberId, selectedPortfolioId).then((data) => { setPolicy(data); setMaxLoss(String(data.maxLossPerTradeRatio * 100)); setMaxExposure(String(data.maxSingleAssetExposureRatio * 100)); }).catch((reason: unknown) => setError(reason instanceof Error ? reason.message : "위험 한도를 불러오지 못했습니다.")); }, [memberId, selectedPortfolioId]);
    const submit = async (event: FormEvent<HTMLFormElement>) => { event.preventDefault(); if (selectedPortfolioId === null) return; setError(null); setSuccess(null); const loss = Number(maxLoss); const exposure = Number(maxExposure); if (![loss, exposure].every((value) => Number.isFinite(value) && value > 0 && value <= 100)) { setError("위험 한도는 0보다 크고 100 이하여야 합니다."); return; } if (loss > exposure) { setError("주문당 최대 손실은 종목당 최대 노출보다 클 수 없습니다."); return; } setIsSaving(true); try { const data = await updatePortfolioRiskPolicy(memberId, selectedPortfolioId, {maxLossPerTradeRatio: loss / 100, maxSingleAssetExposureRatio: exposure / 100}); setPolicy(data); setMaxLoss(String(data.maxLossPerTradeRatio * 100)); setMaxExposure(String(data.maxSingleAssetExposureRatio * 100)); setSuccess("위험 한도를 저장했습니다."); } catch (reason) { setError(reason instanceof Error ? reason.message : "위험 한도를 저장하지 못했습니다."); } finally { setIsSaving(false); } };
    return <><header className="page-header"><p className="eyebrow">PORTFOLIO {selectedPortfolioId ?? "-"}</p><h1>설정</h1><p>위험 한도는 현재 경고 기준으로만 사용되며, 주문 수량이나 손절가를 자동으로 만들지 않습니다.</p></header>{policy ? <p className="setting-summary">현재 주문당 최대 손실 {formatRatio(policy.maxLossPerTradeRatio)} · 종목당 최대 노출 {formatRatio(policy.maxSingleAssetExposureRatio)}</p> : null}<form className="risk-policy-editor" onSubmit={submit}><h2>위험 한도 변경</h2><label>주문당 최대 손실 (%)<input type="number" value={maxLoss} onChange={(event) => setMaxLoss(event.target.value)} min="0" max="100" step="0.01"/></label><label>종목당 최대 노출 (%)<input type="number" value={maxExposure} onChange={(event) => setMaxExposure(event.target.value)} min="0" max="100" step="0.01"/></label>{error ? <p className="form-error-message" role="alert">{error}</p> : null}{success ? <p className="form-success-message" role="status">{success}</p> : null}<button type="submit" disabled={isSaving}>{isSaving ? "저장 중..." : "위험 한도 저장"}</button></form></>;
}
