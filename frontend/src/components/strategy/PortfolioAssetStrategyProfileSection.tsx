import {useCallback, useEffect, useRef, useState, type FormEvent} from "react";
import {
    deleteHeldAssetStrategyProfile,
    getHeldAssetStrategyProfiles,
    upsertHeldAssetStrategyProfile,
} from "../../api/portfolioStrategyProfileApi";
import RequestError from "../common/RequestError";
import type {
    InvestmentTrack,
    PortfolioAssetStrategyProfile,
} from "../../types/portfolioStrategyProfile";
import {formatDateTime} from "../../utils/format";

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
                        : new Error("보유 종목 투자 트랙 설정을 불러오지 못했습니다."),
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
        <section
            className="content-section portfolio-strategy-profile-section"
            id="held-asset-strategy-profiles"
            aria-labelledby="portfolio-strategy-profile-heading"
        >
            <div className="section-heading">
                <div>
                    <p className="section-label">PORTFOLIO TRACK SETTINGS</p>
                    <h2 id="portfolio-strategy-profile-heading">보유 종목 투자 트랙 설정</h2>
                </div>
            </div>
            <p className="section-description">
                이 설정은 현재 포트폴리오에 실제로 매수하여 보유 중인 종목의 가이드 생성에만 적용되며, 전역 자산
                카탈로그(/api/admin/asset-profiles)나 다른 포트폴리오, 미보유 후보 종목(후보 가이드)에는
                영향을 주지 않습니다.
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
                    보유 종목 투자 트랙 설정을 불러오는 중입니다.
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
                        />
                    ))}
                </ul>
            ) : null}
        </section>
    );
}

type CardProps = {
    memberId: number;
    portfolioId: number;
    profile: PortfolioAssetStrategyProfile;
    onProfileUpdated: (updated: PortfolioAssetStrategyProfile) => void;
    onProfileDeleted: (market: string, ticker: string) => void;
};

function HeldAssetStrategyProfileCard({
    memberId,
    portfolioId,
    profile,
    onProfileUpdated,
    onProfileDeleted,
}: CardProps) {
    const [selectedTrack, setSelectedTrack] = useState<InvestmentTrack>("TRACK_A");
    const [isSaving, setIsSaving] = useState(false);
    const [isDeleting, setIsDeleting] = useState(false);
    const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);
    const [cardError, setCardError] = useState<string | null>(null);
    const [cardSuccess, setCardSuccess] = useState<string | null>(null);

    const deleteButtonRef = useRef<HTMLButtonElement | null>(null);
    const confirmButtonRef = useRef<HTMLButtonElement | null>(null);

    // Keep selectedTrack anchored to TRACK_A as only Track A is supported
    const [prevOverride, setPrevOverride] = useState(profile.overrideTrack);
    const [prevGlobal, setPrevGlobal] = useState(profile.globalTrack);
    if (profile.overrideTrack !== prevOverride || profile.globalTrack !== prevGlobal) {
        setPrevOverride(profile.overrideTrack);
        setPrevGlobal(profile.globalTrack);
        setSelectedTrack("TRACK_A");
    }

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
            setCardError("Track B는 현재 지원되지 않습니다. Track A만 저장할 수 있습니다.");
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
                            const val = e.target.value as InvestmentTrack;
                            if (val === "TRACK_A") {
                                setSelectedTrack(val);
                            }
                            setCardError(null);
                            setCardSuccess(null);
                        }}
                        disabled={isSaving || isDeleting}
                    >
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
                            disabled={isSaving || isDeleting}
                            aria-label={`${profile.ticker} 투자 트랙 재정의 저장`}
                        >
                            {isSaving ? "저장 중..." : "재정의 저장"}
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
        </li>
    );
}
