import {useCallback, useEffect, useRef, useState, type FormEvent} from "react";
import {
    deleteHeldAssetStrategyProfile,
    getHeldAssetStrategyProfiles,
    upsertHeldAssetStrategyProfile,
} from "../../api/portfolioStrategyProfileApi";
import {
    deletePortfolioAssetRiskOverride,
    getPortfolioAssetRiskOverrides,
    upsertPortfolioAssetRiskOverride,
} from "../../api/portfolioAssetRiskOverrideApi";
import {getPortfolioRiskPolicy} from "../../api/portfolioRiskApi";
import RequestError from "../common/RequestError";
import AccordionSection from "./AccordionSection";
import type {
    InvestmentTrack,
    PortfolioAssetStrategyProfile,
} from "../../types/portfolioStrategyProfile";
import type {PortfolioAssetRiskOverride} from "../../types/portfolioAssetRiskOverride";
import {formatDateTime} from "../../utils/format";

function findRiskOverride(
    overrides: PortfolioAssetRiskOverride[] | null,
    market: string,
    ticker: string,
): PortfolioAssetRiskOverride | undefined {
    return overrides?.find(
        (item) => item.market === market && item.ticker.toUpperCase() === ticker.toUpperCase(),
    );
}

type SectionProps = {
    memberId: number;
    portfolioId: number;
    onProfileChanged?: () => void;
};

function formatTrackLabel(track: InvestmentTrack | null, descriptive = false): string {
    if (track === "TRACK_A") {
        return descriptive ? "Track A (주봉 10/40 이동평균 가이드)" : "Track A";
    }
    if (track === "TRACK_B") {
        return descriptive ? "Track B (현재 미지원)" : "Track B (미지원)";
    }
    return "미설정";
}

export default function PortfolioAssetStrategyProfileSection({
    memberId,
    portfolioId,
    onProfileChanged,
}: SectionProps) {
    const [profiles, setProfiles] = useState<PortfolioAssetStrategyProfile[] | null>(null);
    const [isLoading, setIsLoading] = useState(true);
    const [error, setError] = useState<Error | null>(null);

    const [refreshTrigger, setRefreshTrigger] = useState(0);

    const [riskOverrides, setRiskOverrides] = useState<PortfolioAssetRiskOverride[] | null>(null);
    const [portfolioDefaultStopLossRatio, setPortfolioDefaultStopLossRatio] = useState<number | null>(null);

    useEffect(() => {
        let isCurrentRequest = true;

        void getPortfolioAssetRiskOverrides(memberId, portfolioId)
            .then((data) => {
                if (!isCurrentRequest) return;
                setRiskOverrides(data);
            })
            .catch(() => {
                if (!isCurrentRequest) return;
                setRiskOverrides([]);
            });

        void getPortfolioRiskPolicy(memberId, portfolioId)
            .then((policy) => {
                if (!isCurrentRequest) return;
                setPortfolioDefaultStopLossRatio(policy.stopLossRatio);
            })
            .catch(() => {
                if (!isCurrentRequest) return;
                setPortfolioDefaultStopLossRatio(null);
            });

        return () => {
            isCurrentRequest = false;
        };
    }, [memberId, portfolioId]);

    const handleRiskOverrideUpserted = useCallback((updated: PortfolioAssetRiskOverride) => {
        setRiskOverrides((current) => {
            const next = current ? current.filter(
                (item) => !(item.market === updated.market && item.ticker.toUpperCase() === updated.ticker.toUpperCase()),
            ) : [];
            return [...next, updated];
        });
    }, []);

    const handleRiskOverrideDeleted = useCallback((market: string, ticker: string) => {
        setRiskOverrides((current) =>
            current
                ? current.filter(
                      (item) => !(item.market === market && item.ticker.toUpperCase() === ticker.toUpperCase()),
                  )
                : current,
        );
    }, []);

    const loadProfiles = useCallback(() => {
        setIsLoading(true);
        setError(null);
        setRefreshTrigger((c) => c + 1);
    }, []);

    useEffect(() => {
        let isCurrentRequest = true;

        void getHeldAssetStrategyProfiles(memberId, portfolioId)
            .then((data) => {
                if (!isCurrentRequest) return;
                setProfiles(data);
                setError(null);
            })
            .catch((reason: unknown) => {
                if (!isCurrentRequest) return;
                setProfiles(null);
                setError(
                    reason instanceof Error
                        ? reason
                        : new Error("보유 종목 투자 트랙 및 손절 기준 설정을 불러오지 못했습니다."),
                );
            })
            .finally(() => {
                if (isCurrentRequest) {
                    setIsLoading(false);
                }
            });

        return () => {
            isCurrentRequest = false;
        };
    }, [memberId, portfolioId, refreshTrigger]);

    const handleProfileUpdated = useCallback((updated: PortfolioAssetStrategyProfile) => {
        setProfiles((current) =>
            current
                ? current.map((item) =>
                      item.market === updated.market &&
                      item.ticker.toUpperCase() === updated.ticker.toUpperCase()
                          ? updated
                          : item,
                  )
                : current,
        );
        onProfileChanged?.();
    }, [onProfileChanged]);

    const handleProfileDeleted = useCallback(
        (market: string, ticker: string) => {
            setProfiles((current) =>
                current
                    ? current.map((item) => {
                          if (
                              item.market === market &&
                              item.ticker.toUpperCase() === ticker.toUpperCase()
                          ) {
                              return {
                                  ...item,
                                  overrideTrack: null,
                                  effectiveTrack: item.globalTrack,
                                  updatedAt: null,
                              };
                          }
                          return item;
                      })
                    : current,
            );
            onProfileChanged?.();
        },
        [onProfileChanged],
    );

    return (
        <AccordionSection
            id="held-asset-strategy-profiles"
            anchorIds={["held-asset-strategy-profiles", "strategy-profiles"]}
            className="portfolio-strategy-profile-section"
            sectionLabel="PORTFOLIO TRACK & STOP-LOSS SETTINGS"
            title="보유 종목 투자 트랙 및 손절 기준 설정"
            defaultOpen={false}
            summary={
                profiles && profiles.length > 0
                    ? `설정 ${profiles.length}건`
                    : undefined
            }
        >
            <p className="section-description">
                이 설정은 현재 포트폴리오에 실제로 매수하여 보유 중인 종목의 가이드 생성에만 적용되며, 전역 자산
                카탈로그(/api/admin/asset-profiles)나 다른 포트폴리오, 미보유 후보 종목(후보 가이드)에는
                영향을 주지 않습니다. 손절 기준은 가이드 검토에 사용하는 참고용 수치이며, 증권사 주문을 자동으로
                생성하거나 실행하지 않습니다.
            </p>
            <div className="strategy-track-policy-notice" role="note" aria-label="전략 트랙 안내">
                <p className="strategy-track-policy-title">전략 트랙 안내 (기술적 분석 가이드)</p>
                <p className="strategy-track-policy-desc">
                    현재 실행 가능한 전략은 <strong>Track A(주봉 10/40 이동평균 가이드)</strong>뿐입니다.
                    <strong>Track B는 현재 지원되지 않으며</strong> 선택할 수 없습니다.
                </p>
                <p className="strategy-track-policy-disclaimer">
                    제공되는 모든 가이드는 기술적 지표에 기반한 참고 정보이며, 투자를 권유하거나 주문을 자동 실행하지 않습니다.
                </p>
            </div>

            {isLoading && !profiles ? (
                <p className="status-message" aria-live="polite">
                    보유 종목 투자 트랙 및 손절 기준 설정을 불러오는 중입니다.
                </p>
            ) : null}

            {error && !profiles ? (
                <RequestError
                    message={error.message}
                    onRetry={() => void loadProfiles()}
                    retryLabel="투자 트랙 설정 다시 시도"
                />
            ) : null}

            {profiles && profiles.length === 0 ? (
                <p className="empty-state">투자 트랙을 설정할 보유 종목이 없습니다.</p>
            ) : null}

            {profiles && profiles.length > 0 ? (
                <ul className="strategy-profile-card-list" aria-label="보유 종목 투자 트랙 목록">
                    {profiles.map((profile) => (
                        <HeldAssetStrategyProfileCard
                            key={`${profile.market}-${profile.ticker}`}
                            memberId={memberId}
                            portfolioId={portfolioId}
                            profile={profile}
                            onProfileUpdated={handleProfileUpdated}
                            onProfileDeleted={handleProfileDeleted}
                            riskOverride={findRiskOverride(riskOverrides, profile.market, profile.ticker)}
                            portfolioDefaultStopLossRatio={portfolioDefaultStopLossRatio}
                            onRiskOverrideUpserted={handleRiskOverrideUpserted}
                            onRiskOverrideDeleted={handleRiskOverrideDeleted}
                        />
                    ))}
                </ul>
            ) : null}
        </AccordionSection>
    );
}

type CardProps = {
    memberId: number;
    portfolioId: number;
    profile: PortfolioAssetStrategyProfile;
    onProfileUpdated: (updated: PortfolioAssetStrategyProfile) => void;
    onProfileDeleted: (market: string, ticker: string) => void;
    riskOverride: PortfolioAssetRiskOverride | undefined;
    portfolioDefaultStopLossRatio: number | null;
    onRiskOverrideUpserted: (updated: PortfolioAssetRiskOverride) => void;
    onRiskOverrideDeleted: (market: string, ticker: string) => void;
};

function formatPercentInput(ratio: number): string {
    return String(Math.round(ratio * 10000) / 100);
}

function HeldAssetStrategyProfileCard({
    memberId,
    portfolioId,
    profile,
    onProfileUpdated,
    onProfileDeleted,
    riskOverride,
    portfolioDefaultStopLossRatio,
    onRiskOverrideUpserted,
    onRiskOverrideDeleted,
}: CardProps) {
    const [selectedTrack, setSelectedTrack] = useState<InvestmentTrack | "">(
        profile.overrideTrack ?? profile.globalTrack ?? "",
    );
    const [isSaving, setIsSaving] = useState(false);
    const [isDeleting, setIsDeleting] = useState(false);
    const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);
    const [cardError, setCardError] = useState<string | null>(null);
    const [cardSuccess, setCardSuccess] = useState<string | null>(null);

    const deleteButtonRef = useRef<HTMLButtonElement | null>(null);
    const confirmButtonRef = useRef<HTMLButtonElement | null>(null);

    // Keep the selector aligned with the saved profile when the parent reloads.
    const [prevOverride, setPrevOverride] = useState(profile.overrideTrack);
    const [prevGlobal, setPrevGlobal] = useState(profile.globalTrack);
    if (profile.overrideTrack !== prevOverride || profile.globalTrack !== prevGlobal) {
        setPrevOverride(profile.overrideTrack);
        setPrevGlobal(profile.globalTrack);
        setSelectedTrack(profile.overrideTrack ?? profile.globalTrack ?? "");
    }

    const [stopLossInput, setStopLossInput] = useState(
        riskOverride ? formatPercentInput(riskOverride.stopLossRatio) : "",
    );
    const [isSavingStopLoss, setIsSavingStopLoss] = useState(false);
    const [isResettingStopLoss, setIsResettingStopLoss] = useState(false);
    const [stopLossError, setStopLossError] = useState<string | null>(null);
    const [stopLossSuccess, setStopLossSuccess] = useState<string | null>(null);

    // Keep the stop-loss field aligned with the saved override when the parent reloads.
    const [prevRiskOverrideRatio, setPrevRiskOverrideRatio] = useState(riskOverride?.stopLossRatio ?? null);
    if ((riskOverride?.stopLossRatio ?? null) !== prevRiskOverrideRatio) {
        setPrevRiskOverrideRatio(riskOverride?.stopLossRatio ?? null);
        setStopLossInput(riskOverride ? formatPercentInput(riskOverride.stopLossRatio) : "");
    }

    const hasAssetStopLossOverride = riskOverride !== undefined;
    const hasPortfolioDefaultStopLoss = portfolioDefaultStopLossRatio !== null;

    const handleSaveStopLoss = async (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        const percentValue = Number(stopLossInput);
        if (!Number.isFinite(percentValue) || percentValue <= 0 || percentValue >= 100) {
            setStopLossError("손절 비율은 0보다 크고 100%보다 작게 입력해 주세요.");
            return;
        }
        setStopLossError(null);
        setStopLossSuccess(null);
        setIsSavingStopLoss(true);

        try {
            const result = await upsertPortfolioAssetRiskOverride(
                memberId,
                portfolioId,
                profile.market,
                profile.ticker,
                percentValue / 100,
            );
            onRiskOverrideUpserted(result);
            setStopLossSuccess(
                `${profile.ticker}의 손절 기준을 ${percentValue}%로 저장했습니다. 새 가이드 생성부터 반영됩니다.`,
            );
        } catch (reason) {
            setStopLossError(
                reason instanceof Error
                    ? reason.message
                    : "손절 비율 재정의를 저장하지 못했습니다.",
            );
        } finally {
            setIsSavingStopLoss(false);
        }
    };

    const handleUseDefaultStopLoss = async () => {
        setStopLossError(null);
        setStopLossSuccess(null);
        setIsResettingStopLoss(true);

        try {
            await deletePortfolioAssetRiskOverride(memberId, portfolioId, profile.market, profile.ticker);
            onRiskOverrideDeleted(profile.market, profile.ticker);
            setStopLossSuccess(
                `${profile.ticker}의 손절 기준을 포트폴리오 기본값으로 되돌렸습니다. 새 가이드 생성부터 반영됩니다.`,
            );
        } catch (reason) {
            setStopLossError(
                reason instanceof Error
                    ? reason.message
                    : "손절 비율 재정의를 삭제하지 못했습니다.",
            );
        } finally {
            setIsResettingStopLoss(false);
        }
    };

    // Focus confirm button when delete confirmation opens, and handle Escape key
    useEffect(() => {
        if (!showDeleteConfirm) return;

        confirmButtonRef.current?.focus();

        const handleKeyDown = (e: KeyboardEvent) => {
            if (e.key === "Escape") {
                setShowDeleteConfirm(false);
                deleteButtonRef.current?.focus();
            }
        };

        window.addEventListener("keydown", handleKeyDown);
        return () => window.removeEventListener("keydown", handleKeyDown);
    }, [showDeleteConfirm]);

    const handleSave = async (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        if (selectedTrack !== "TRACK_A") {
            setCardError("가이드를 생성하려면 Track A를 선택해 저장해 주세요.");
            return;
        }
        setCardError(null);
        setCardSuccess(null);
        setIsSaving(true);

        try {
            const result = await upsertHeldAssetStrategyProfile(
                memberId,
                portfolioId,
                profile.market,
                profile.ticker,
                selectedTrack,
            );
            onProfileUpdated(result);
            setCardSuccess(
                `${profile.ticker}의 투자 트랙을 ${formatTrackLabel(result.overrideTrack, true)}로 재정의했습니다.`,
            );
        } catch (reason) {
            setCardError(
                reason instanceof Error
                    ? reason.message
                    : "투자 트랙 재정의를 저장하지 못했습니다.",
            );
        } finally {
            setIsSaving(false);
        }
    };

    const handleConfirmDelete = async () => {
        setCardError(null);
        setCardSuccess(null);
        setIsDeleting(true);

        try {
            await deleteHeldAssetStrategyProfile(
                memberId,
                portfolioId,
                profile.market,
                profile.ticker,
            );
            onProfileDeleted(profile.market, profile.ticker);
            setShowDeleteConfirm(false);
            setCardSuccess(
                `${profile.ticker}의 포트폴리오 재정의를 삭제하고 전역 기본값으로 복원했습니다.`,
            );
        } catch (reason) {
            setCardError(
                reason instanceof Error
                    ? reason.message
                    : "투자 트랙 재정의를 삭제하지 못했습니다.",
            );
        } finally {
            setIsDeleting(false);
        }
    };

    const handleCancelDelete = () => {
        setShowDeleteConfirm(false);
        deleteButtonRef.current?.focus();
    };

    const hasOverride = profile.overrideTrack !== null;
    const hasEffectiveTrack = profile.effectiveTrack !== null;

    return (
        <li className="strategy-profile-card">
            <div className="strategy-profile-card-header">
                <div className="strategy-profile-asset">
                    <span className="market-badge">{profile.market}</span>
                    <strong className="strategy-profile-ticker">{profile.ticker}</strong>
                </div>
                <div className="strategy-profile-badges">
                    <span
                        className={`strategy-profile-track-badge ${
                            hasEffectiveTrack
                                ? profile.effectiveTrack === "TRACK_A"
                                    ? "track-a"
                                    : "track-b"
                                : "track-none"
                        }`}
                    >
                        {formatTrackLabel(profile.effectiveTrack)}
                    </span>
                    <span
                        className={`status-pill ${
                            hasOverride ? "pill-warning" : "pill-info"
                        }`}
                    >
                        {hasOverride ? "포트폴리오 재정의 적용" : "전역 기본값 적용"}
                    </span>
                </div>
            </div>

            {!hasEffectiveTrack ? (
                <div className="strategy-profile-missing-notice" role="status">
                    <p>
                        설정된 투자 트랙이 없습니다. 가이드를 생성하려면 아래에서 Track A를
                        선택해 저장해 주세요.
                    </p>
                </div>
            ) : null}

            <dl className="strategy-profile-details">
                <div>
                    <dt>적용 트랙</dt>
                    <dd>
                        {hasEffectiveTrack
                            ? `${formatTrackLabel(profile.effectiveTrack, true)} (${
                                  hasOverride ? "포트폴리오 재정의" : "전역 기본값"
                              })`
                            : "설정된 트랙 없음 (가이드 미제공)"}
                    </dd>
                </div>
                <div>
                    <dt>전역 기본값</dt>
                    <dd>
                        {profile.globalTrack
                            ? formatTrackLabel(profile.globalTrack, true)
                            : "전역 기본값 없음"}
                    </dd>
                </div>
                <div>
                    <dt>포트폴리오 재정의</dt>
                    <dd>
                        {hasOverride
                            ? `${formatTrackLabel(profile.overrideTrack, true)}${
                                  profile.updatedAt
                                      ? ` (${formatDateTime(profile.updatedAt)} 저장)`
                                      : ""
                              }`
                            : "재정의 없음 (전역 기본값 사용)"}
                    </dd>
                </div>
            </dl>

        <form className="strategy-profile-form" onSubmit={handleSave}>
                <div className="strategy-profile-field">
                    <label htmlFor={`track-select-${profile.market}-${profile.ticker}`}>
                        포트폴리오 투자 트랙 재정의
                    </label>
                    <select
                        id={`track-select-${profile.market}-${profile.ticker}`}
                        value={selectedTrack}
                        onChange={(e) => {
                            const val = e.target.value as InvestmentTrack | "";
                            setSelectedTrack(val);
                            setCardError(null);
                            setCardSuccess(null);
                        }}
                        disabled={isSaving || isDeleting}
                    >
                        <option value="">트랙을 선택해 주세요</option>
                        <option value="TRACK_A">Track A (주봉 10/40 이동평균 가이드)</option>
                    </select>
                    <p className="strategy-track-field-note">
                        현재 실행 가능한 전략은 Track A(주봉 10/40 이동평균 가이드)뿐이며, Track B는 현재 지원되지 않습니다.
                    </p>
                </div>

                {!showDeleteConfirm ? (
                    <div className="strategy-profile-actions">
                        <button
                            type="submit"
                            className="primary-button"
                            disabled={selectedTrack !== "TRACK_A" || isSaving || isDeleting}
                            aria-label={`${profile.ticker} 투자 트랙 재정의 저장`}
                        >
                            {isSaving
                                ? "저장 중..."
                                : hasOverride || hasEffectiveTrack
                                  ? "재정의 저장"
                                  : "Track A 저장"}
                        </button>
                        {hasOverride ? (
                            <button
                                type="button"
                                className="secondary-button danger-action-button"
                                onClick={() => {
                                    setShowDeleteConfirm(true);
                                    setCardError(null);
                                    setCardSuccess(null);
                                }}
                                disabled={isSaving || isDeleting}
                                ref={deleteButtonRef}
                                aria-label={`${profile.ticker} 투자 트랙 재정의 삭제`}
                            >
                                {isDeleting ? "삭제 중..." : "재정의 삭제"}
                            </button>
                        ) : null}
                    </div>
                ) : (
                    <div
                        className="profile-delete-confirm-box"
                        role="alertdialog"
                        aria-labelledby={`confirm-title-${profile.market}-${profile.ticker}`}
                        aria-describedby={`confirm-desc-${profile.market}-${profile.ticker}`}
                    >
                        <p
                            id={`confirm-title-${profile.market}-${profile.ticker}`}
                            className="confirm-title"
                        >
                            <strong>{profile.ticker}</strong>의 포트폴리오 재정의를
                            삭제하시겠습니까?
                        </p>
                        <p
                            id={`confirm-desc-${profile.market}-${profile.ticker}`}
                            className="confirm-note"
                        >
                            삭제 시 전역 기본값(
                            {profile.globalTrack
                                ? formatTrackLabel(profile.globalTrack, true)
                                : "설정 없음"}
                            )으로 복원되며, 이 포트폴리오의 가이드 생성에만 반영됩니다.
                        </p>
                        <div className="confirm-actions">
                            <button
                                type="button"
                                className="primary-button danger-button"
                                onClick={() => void handleConfirmDelete()}
                                disabled={isDeleting}
                                ref={confirmButtonRef}
                            >
                                {isDeleting ? "삭제 중..." : "삭제 확인"}
                            </button>
                            <button
                                type="button"
                                className="secondary-button"
                                onClick={handleCancelDelete}
                                disabled={isDeleting}
                            >
                                취소
                            </button>
                        </div>
                    </div>
                )}

                <div className="profile-card-feedback" aria-live="polite">
                    {isSaving ? (
                        <p className="form-loading-message" role="status">
                            투자 트랙 재정의를 저장하는 중입니다...
                        </p>
                    ) : null}
                    {isDeleting ? (
                        <p className="form-loading-message" role="status">
                            투자 트랙 재정의를 삭제하는 중입니다...
                        </p>
                    ) : null}
                    {!isSaving && !isDeleting && cardError ? (
                        <p className="form-error-message" role="alert">
                            {cardError}
                        </p>
                    ) : null}
                    {!isSaving && !isDeleting && cardSuccess ? (
                        <p className="form-success-message" role="status">
                            {cardSuccess}
                        </p>
                    ) : null}
                </div>
            </form>

            <div className="strategy-profile-divider" role="separator"/>

            <div className="asset-stop-loss-group">
                <div className="asset-stop-loss-header">
                    <h3>손절 기준 재정의 (검토용)</h3>
                    <span
                        className={`status-pill ${
                            hasAssetStopLossOverride
                                ? "pill-warning"
                                : hasPortfolioDefaultStopLoss
                                  ? "pill-info"
                                  : "pill-neutral"
                        }`}
                    >
                        {hasAssetStopLossOverride
                            ? "종목별 설정 적용"
                            : hasPortfolioDefaultStopLoss
                              ? "포트폴리오 기본값 적용"
                              : "손절 기준 미설정"}
                    </span>
                </div>
                <p className="asset-stop-loss-status-desc">
                    {hasAssetStopLossOverride
                        ? `이 종목에는 ${formatPercentInput(riskOverride!.stopLossRatio)}%의 종목별 손절 기준이 적용됩니다.`
                        : hasPortfolioDefaultStopLoss
                          ? `종목별 설정이 없어 포트폴리오 기본값(${formatPercentInput(portfolioDefaultStopLossRatio!)}%)이 적용됩니다.`
                          : "종목별 설정과 포트폴리오 기본값이 모두 없어 손절가를 계산하지 않습니다."}
                </p>

                <form className="asset-stop-loss-form" onSubmit={(e) => void handleSaveStopLoss(e)}>
                    <div className="strategy-profile-field">
                        <label htmlFor={`stop-loss-input-${profile.market}-${profile.ticker}`}>
                            종목별 손절 비율 (%)
                        </label>
                        <input
                            id={`stop-loss-input-${profile.market}-${profile.ticker}`}
                            type="number"
                            inputMode="decimal"
                            min="0"
                            max="99.99"
                            step="0.01"
                            placeholder="예: 5.0"
                            value={stopLossInput}
                            onChange={(e) => {
                                setStopLossInput(e.target.value);
                                setStopLossError(null);
                                setStopLossSuccess(null);
                            }}
                            disabled={isSavingStopLoss || isResettingStopLoss}
                        />
                    </div>

                    <div className="strategy-profile-actions">
                        <button
                            type="submit"
                            className="primary-button"
                            disabled={isSavingStopLoss || isResettingStopLoss || stopLossInput.trim() === ""}
                            aria-label={`${profile.ticker} 손절 비율 재정의 저장`}
                        >
                            {isSavingStopLoss ? "저장 중..." : "손절 비율 저장"}
                        </button>
                        {hasAssetStopLossOverride ? (
                            <button
                                type="button"
                                className="secondary-button"
                                onClick={() => void handleUseDefaultStopLoss()}
                                disabled={isSavingStopLoss || isResettingStopLoss}
                                aria-label={`${profile.ticker} 손절 비율을 포트폴리오 기본값으로 되돌리기`}
                            >
                                {isResettingStopLoss ? "되돌리는 중..." : "기본값 사용"}
                            </button>
                        ) : null}
                    </div>

                    <div className="profile-card-feedback asset-stop-loss-feedback" aria-live="polite">
                        {isSavingStopLoss ? (
                            <p className="form-loading-message" role="status">
                                손절 비율 재정의를 저장하는 중입니다...
                            </p>
                        ) : null}
                        {isResettingStopLoss ? (
                            <p className="form-loading-message" role="status">
                                손절 비율을 포트폴리오 기본값으로 되돌리는 중입니다...
                            </p>
                        ) : null}
                        {!isSavingStopLoss && !isResettingStopLoss && stopLossError ? (
                            <p className="form-error-message" role="alert">
                                {stopLossError}
                            </p>
                        ) : null}
                        {!isSavingStopLoss && !isResettingStopLoss && stopLossSuccess ? (
                            <p className="form-success-message" role="status">
                                {stopLossSuccess}
                            </p>
                        ) : null}
                    </div>
                </form>
            </div>
        </li>
    );
}
