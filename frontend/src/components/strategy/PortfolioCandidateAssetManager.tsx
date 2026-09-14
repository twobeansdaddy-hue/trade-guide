import {useCallback, useEffect, useState, type FormEvent} from "react";
import {searchAssetListings} from "../../api/assetListingApi";
import {
    createPortfolioCandidateAsset,
    deletePortfolioCandidateAsset,
    getPortfolioCandidateAssets,
} from "../../api/portfolioCandidateAssetApi";
import AssetSearchInput from "../asset/AssetSearchInput";
import RequestError from "../common/RequestError";
import type {AssetListing} from "../../types/assetListing";
import type {PortfolioCandidateAsset} from "../../types/portfolioCandidateAsset";

type Props = {
    memberId: number;
    portfolioId: number;
    onCandidateChanged?: () => void;
    onCandidateCountChange?: (count: number | null) => void;
};

export default function PortfolioCandidateAssetManager({
    memberId,
    portfolioId,
    onCandidateChanged,
    onCandidateCountChange,
}: Props) {
    const [candidates, setCandidates] = useState<PortfolioCandidateAsset[] | null>(null);
    const [isLoading, setIsLoading] = useState(true);
    const [loadError, setLoadError] = useState<Error | null>(null);
    const [refreshTrigger, setRefreshTrigger] = useState(0);

    // Add candidate form state
    const [searchTicker, setSearchTicker] = useState("");
    const [selectedAsset, setSelectedAsset] = useState<AssetListing | null>(null);
    const [isSubmitting, setIsSubmitting] = useState(false);
    const [formError, setFormError] = useState<string | null>(null);
    const [successMessage, setSuccessMessage] = useState<string | null>(null);

    // Delete state
    const [deletingId, setDeletingId] = useState<number | null>(null);
    const [deleteError, setDeleteError] = useState<string | null>(null);

    const loadCandidates = useCallback(() => {
        setIsLoading(true);
        setLoadError(null);
        setRefreshTrigger((c) => c + 1);
    }, []);

    useEffect(() => {
        let isCurrentRequest = true;

        void getPortfolioCandidateAssets(memberId, portfolioId)
            .then((data) => {
                if (!isCurrentRequest) return;
                setCandidates(data);
                setLoadError(null);
                onCandidateCountChange?.(data.length);
            })
            .catch((reason: unknown) => {
                if (!isCurrentRequest) return;
                setCandidates(null);
                onCandidateCountChange?.(null);
                setLoadError(
                    reason instanceof Error
                        ? reason
                        : new Error("후보 종목 목록을 불러오지 못했습니다."),
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
    }, [memberId, portfolioId, refreshTrigger, onCandidateCountChange]);

    const handleAddCandidate = async (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        const normalizedTicker = searchTicker.trim().toUpperCase();

        if (!normalizedTicker) {
            setFormError("후보로 추가할 종목을 검색해 주세요.");
            return;
        }

        setFormError(null);
        setDeleteError(null);
        setSuccessMessage(null);
        setIsSubmitting(true);

        try {
            let assetToAdd = selectedAsset;

            // If user typed without clicking a suggestion, resolve display name via search
            if (!assetToAdd || assetToAdd.ticker.toUpperCase() !== normalizedTicker) {
                const searchResults = await searchAssetListings("US", normalizedTicker);
                const exactMatch = searchResults.find(
                    (item) => item.ticker.toUpperCase() === normalizedTicker,
                );
                if (exactMatch) {
                    assetToAdd = exactMatch;
                } else if (searchResults.length > 0) {
                    assetToAdd = searchResults[0];
                } else {
                    setFormError("유효한 종목을 검색 결과에서 선택해 주세요.");
                    setIsSubmitting(false);
                    return;
                }
            }

            // Check duplicate in current local list
            if (
                candidates?.some(
                    (c) =>
                        c.market === assetToAdd.market &&
                        c.ticker.toUpperCase() === assetToAdd.ticker.toUpperCase(),
                )
            ) {
                setFormError(
                    `이미 등록된 후보 종목입니다: ${assetToAdd.market} / ${assetToAdd.ticker}`,
                );
                setIsSubmitting(false);
                return;
            }

            const created = await createPortfolioCandidateAsset(memberId, portfolioId, {
                market: assetToAdd.market,
                ticker: assetToAdd.ticker,
                displayName: assetToAdd.displayName,
            });

            setCandidates((current) => {
                const next = current ? [...current, created] : [created];
                onCandidateCountChange?.(next.length);
                return next;
            });
            setSuccessMessage(
                `${created.displayName} (${created.ticker}) 종목을 Track A 후보로 추가했습니다. (미보유 상태일 때 가이드 계산)`,
            );
            setSearchTicker("");
            setSelectedAsset(null);
            onCandidateChanged?.();
        } catch (reason) {
            setFormError(
                reason instanceof Error
                    ? reason.message
                    : "후보 종목을 등록하지 못했습니다.",
            );
        } finally {
            setIsSubmitting(false);
        }
    };

    const handleDelete = async (candidate: PortfolioCandidateAsset) => {
        const isConfirmed = window.confirm(
            `${candidate.displayName} (${candidate.ticker}) 종목을 후보 목록에서 삭제할까요?`,
        );
        if (!isConfirmed) return;

        setDeleteError(null);
        setFormError(null);
        setSuccessMessage(null);
        setDeletingId(candidate.id);

        try {
            await deletePortfolioCandidateAsset(
                memberId,
                portfolioId,
                candidate.market,
                candidate.ticker,
            );

            setCandidates((current) => {
                const next = current
                    ? current.filter(
                          (item) =>
                              !(
                                  item.market === candidate.market &&
                                  item.ticker === candidate.ticker
                              ),
                      )
                    : current;
                onCandidateCountChange?.(next ? next.length : 0);
                return next;
            });
            setSuccessMessage(
                `${candidate.displayName} (${candidate.ticker}) 후보 종목을 삭제했습니다.`,
            );
            onCandidateChanged?.();
        } catch (reason) {
            setDeleteError(
                reason instanceof Error
                    ? reason.message
                    : "후보 종목을 삭제하지 못했습니다.",
            );
        } finally {
            setDeletingId(null);
        }
    };

    return (
        <div
            className="candidate-asset-manager"
            aria-labelledby="candidate-asset-manager-heading"
        >
            <div className="candidate-manager-header">
                <div>
                    <h3
                        id="candidate-asset-manager-heading"
                        className="candidate-manager-title"
                    >
                        포트폴리오 Track A 후보 종목 관리 (미보유 관심 종목)
                    </h3>
                    <p className="candidate-manager-description">
                        현재 포트폴리오에서 미보유 상태인 관심 종목을 직접 등록합니다. 후보 미설정 시 관리자 선별 Track A 검토 유니버스(기본 대상군)가 자동 적용됩니다.
                    </p>
                </div>
                {candidates && candidates.length > 0 ? (
                    <span className="candidate-count-badge">
                        등록 종목 <strong>{candidates.length}</strong>개
                    </span>
                ) : null}
            </div>

            <form
                className="candidate-add-form"
                onSubmit={(e) => void handleAddCandidate(e)}
            >
                <div className="candidate-search-row">
                    <div className="candidate-search-input-col">
                        <AssetSearchInput
                            market="US"
                            ticker={searchTicker}
                            onTickerChange={(t) => {
                                setSearchTicker(t);
                                setFormError(null);
                                setDeleteError(null);
                                setSuccessMessage(null);
                            }}
                            onSelectAsset={(asset) => {
                                setSelectedAsset(asset);
                                setSearchTicker(asset.ticker);
                                setFormError(null);
                                setDeleteError(null);
                                setSuccessMessage(null);
                            }}
                            label="후보 종목 검색"
                            placeholder="티커 또는 종목명을 입력 (예: AAPL, MSFT, SOXL)"
                            disabled={isSubmitting}
                        />
                    </div>
                    <button
                        type="submit"
                        className="primary-button candidate-add-btn"
                        disabled={isSubmitting || !searchTicker.trim()}
                        aria-label="후보 종목 추가"
                    >
                        {isSubmitting ? "추가 중..." : "후보 추가"}
                    </button>
                </div>

                {selectedAsset &&
                selectedAsset.ticker.toUpperCase() ===
                    searchTicker.trim().toUpperCase() ? (
                    <div className="candidate-selected-preview" aria-live="polite">
                        <span className="preview-label">선택된 종목:</span>
                        <span className="market-badge">{selectedAsset.market}</span>
                        <strong className="preview-name">{selectedAsset.displayName}</strong>
                        <span className="preview-ticker">({selectedAsset.ticker})</span>
                    </div>
                ) : null}
            </form>

            <div className="candidate-feedback-area" aria-live="polite">
                {formError ? (
                    <p className="candidate-feedback error" role="alert">
                        {formError}
                    </p>
                ) : null}
                {deleteError ? (
                    <p className="candidate-feedback error" role="alert">
                        {deleteError}
                    </p>
                ) : null}
                {successMessage ? (
                    <p className="candidate-feedback success" role="status">
                        {successMessage}
                    </p>
                ) : null}
            </div>

            <div className="candidate-manager-content">
                {isLoading && !candidates ? (
                    <p className="status-message" aria-live="polite">
                        후보 종목 목록을 불러오는 중입니다.
                    </p>
                ) : null}

                {loadError && !candidates ? (
                    <RequestError
                        message={loadError.message}
                        onRetry={() => void loadCandidates()}
                        retryLabel="후보 목록 다시 시도"
                    />
                ) : null}

                {!isLoading && candidates && candidates.length === 0 ? (
                    <div className="candidate-empty-box">
                        <p className="candidate-empty-title">
                            이 포트폴리오에 직접 등록한 Track A 후보가 없습니다.
                        </p>
                        <p className="candidate-empty-desc">
                            등록된 후보가 없으므로 관리자가 선별한 전역 Track A 검토 유니버스(기본 대상군)를 기준으로 미보유 종목 가이드가 자동 조회됩니다.
                            위 검색창에서 종목을 추가하면 해당 포트폴리오 후보가 우선 적용됩니다.
                        </p>
                    </div>
                ) : null}

                {candidates && candidates.length > 0 ? (
                    <div className="candidate-list-wrapper">
                        <div className="candidate-list-header">
                            <h4 className="candidate-list-title">이 포트폴리오에 설정한 Track A 후보 종목</h4>
                            <span className="candidate-applied-notice">포트폴리오 후보 우선 적용 중 (미보유 대상)</span>
                        </div>
                        <ul
                            className="candidate-asset-list"
                            aria-label="포트폴리오 Track A 후보 종목 목록"
                        >
                            {candidates.map((candidate) => {
                                const isDeleting = deletingId === candidate.id;
                                return (
                                    <li
                                        key={`${candidate.market}-${candidate.ticker}`}
                                        className={`candidate-asset-item ${
                                            isDeleting ? "deleting" : ""
                                        }`}
                                    >
                                        <div className="candidate-asset-info">
                                            <div className="candidate-asset-badges">
                                                <span className="market-badge">
                                                    {candidate.market}
                                                </span>
                                                <span className="candidate-track-badge">
                                                    Track A 후보
                                                </span>
                                            </div>
                                            <div className="candidate-asset-names">
                                                <strong className="candidate-asset-ticker">
                                                    {candidate.ticker}
                                                </strong>
                                                <span
                                                    className="candidate-asset-display-name"
                                                    title={candidate.displayName}
                                                >
                                                    {candidate.displayName}
                                                </span>
                                            </div>
                                        </div>
                                        <div className="candidate-asset-actions">
                                            <button
                                                type="button"
                                                className="candidate-icon-delete-btn"
                                                onClick={() => void handleDelete(candidate)}
                                                disabled={isDeleting || isSubmitting}
                                                aria-label={`${candidate.displayName} (${candidate.ticker}) 후보 삭제`}
                                                title={`${candidate.displayName} (${candidate.ticker}) 후보 삭제`}
                                            >
                                                {isDeleting ? (
                                                    <span
                                                        className="candidate-delete-spinner"
                                                        aria-hidden="true"
                                                    />
                                                ) : (
                                                    <svg
                                                        width="16"
                                                        height="16"
                                                        viewBox="0 0 24 24"
                                                        fill="none"
                                                        stroke="currentColor"
                                                        strokeWidth="2"
                                                        strokeLinecap="round"
                                                        strokeLinejoin="round"
                                                        aria-hidden="true"
                                                    >
                                                        <path d="M3 6h18" />
                                                        <path d="M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6" />
                                                        <path d="M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2" />
                                                        <line x1="10" y1="11" x2="10" y2="17" />
                                                        <line x1="14" y1="11" x2="14" y2="17" />
                                                    </svg>
                                                )}
                                            </button>
                                        </div>
                                    </li>
                                );
                            })}
                        </ul>
                    </div>
                ) : null}
            </div>
        </div>
    );
}
