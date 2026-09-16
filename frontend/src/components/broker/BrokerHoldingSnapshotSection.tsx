import {useCallback, useEffect, useRef, useState} from "react";
import {
    approveBrokerHoldingAdjustment,
    approveBrokerHoldingOpeningBalance,
    approveBrokerHoldingOpeningBalanceBatch,
    getBrokerHoldingAdjustments,
    getBrokerHoldingOpeningBalanceImports,
    getLatestBrokerHoldingSnapshot,
    getLatestBrokerHoldingSnapshotComparison,
    refreshBrokerHoldingSnapshot,
    revokeBrokerHoldingAdjustment,
    revokeBrokerHoldingOpeningBalanceImport,
} from "../../api/portfolioBrokerApi";
import {hasApiStatus} from "../../api/apiError";
import {usePortfolioResource} from "../../hooks/usePortfolioResource";
import {formatDateTime} from "../../utils/format";
import RequestError from "../common/RequestError";
import type {BrokerProvider} from "../../types/brokerConnection";
import type {
    BrokerHoldingImportBatchResponse,
    BrokerHoldingPreviewItem,
    BrokerOpeningBalanceSkipReason,
    PortfolioBrokerHoldingAdjustment,
    PortfolioBrokerHoldingImport,
} from "../../types/portfolioBroker";

type BrokerHoldingSnapshotSectionProps = {
    memberId: number;
    portfolioId: number;
    viewMode?: "comparison" | "opening-balance" | "all";
    onNextStep?: () => void;
    onPrevStep?: () => void;
    onGoToStep?: (step: number, tab?: "opening-balance" | "order-history") => void;
    onSnapshotStateChange?: (hasSnapshot: boolean) => void;
};

const brokerProviderLabel: Record<BrokerProvider, string> = {
    TOSS_SECURITIES: "토스증권",
};

const comparisonLabel = {
    MATCHED: "수량 일치",
    QUANTITY_MISMATCH: "수량 차이",
    ONLY_IN_BROKER: "증권사에만 있음",
    ONLY_IN_TRADE_GUIDE: "Trade Guide에만 있음",
} as const;

const skipReasonLabels: Record<BrokerOpeningBalanceSkipReason, string> = {
    NOT_ONLY_IN_BROKER: "최신 스냅샷 비교 결과가 증권사에만 있는 종목이 아님",
    UNSUPPORTED_MARKET: "지원하지 않는 시장 종목",
    ALREADY_APPROVED: "같은 종목의 활성 개시 잔고 승인 이력이 이미 있음",
    PREVIOUSLY_REVOKED: "사용자가 이미 취소한 적이 있어 자동 재반영에서 제외됨",
    LEDGER_CONFLICT: "같은 종목의 매매 원장 기록이 이미 존재해 충돌",
    INACTIVE_LISTING: "자산 카탈로그의 상장 상태가 활성이 아님",
};

const formatQuantity = (quantity: number | null | undefined) => quantity === null || quantity === undefined ? "없음" : `${quantity.toLocaleString("en-US")}주`;
const formatPrice = (price: number | null | undefined) => price === null || price === undefined ? "미기록" : price.toLocaleString("en-US");

export default function BrokerHoldingSnapshotSection({
    memberId,
    portfolioId,
    viewMode = "all",
    onNextStep,
    onPrevStep,
    onGoToStep,
    onSnapshotStateChange,
}: BrokerHoldingSnapshotSectionProps) {
    const comparisonResource = usePortfolioResource(memberId, portfolioId, getLatestBrokerHoldingSnapshotComparison);
    const [latestSnapshotId, setLatestSnapshotId] = useState<number | null>(null);
    const [isRefreshing, setIsRefreshing] = useState(false);
    const [refreshError, setRefreshError] = useState<string | null>(null);
    const isSnapshotMissing = hasApiStatus(comparisonResource.error, 404);

    useEffect(() => {
        if (comparisonResource.data) {
            onSnapshotStateChange?.(true);
        } else if (isSnapshotMissing) {
            onSnapshotStateChange?.(false);
        }
    }, [comparisonResource.data, isSnapshotMissing, onSnapshotStateChange]);

    const [pendingApprovalItem, setPendingApprovalItem] = useState<BrokerHoldingPreviewItem | null>(null);
    const [approvingSnapshotItemId, setApprovingSnapshotItemId] = useState<number | null>(null);

    const [pendingAdjustmentItem, setPendingAdjustmentItem] = useState<BrokerHoldingPreviewItem | null>(null);
    const [approvingAdjustmentItemId, setApprovingAdjustmentItemId] = useState<number | null>(null);

    const [isConfirmingBatch, setIsConfirmingBatch] = useState(false);
    const [isBatchApproving, setIsBatchApproving] = useState(false);
    const [batchResult, setBatchResult] = useState<BrokerHoldingImportBatchResponse | null>(null);

    const [pendingRevokeId, setPendingRevokeId] = useState<number | null>(null);
    const [revokingImportId, setRevokingImportId] = useState<number | null>(null);

    const [pendingRevokeAdjustmentId, setPendingRevokeAdjustmentId] = useState<number | null>(null);
    const [revokingAdjustmentId, setRevokingAdjustmentId] = useState<number | null>(null);

    const [actionError, setActionError] = useState<string | null>(null);
    const [actionSuccess, setActionSuccess] = useState<string | null>(null);

    const [imports, setImports] = useState<PortfolioBrokerHoldingImport[]>([]);
    const [importPage, setImportPage] = useState(0);
    const [hasNextImports, setHasNextImports] = useState(false);
    const [totalImports, setTotalImports] = useState(0);
    const [isLoadingImports, setIsLoadingImports] = useState(true);
    const [isLoadingMoreImports, setIsLoadingMoreImports] = useState(false);
    const [importsError, setImportsError] = useState<string | null>(null);

    const [adjustments, setAdjustments] = useState<PortfolioBrokerHoldingAdjustment[]>([]);
    const [adjustmentPage, setAdjustmentPage] = useState(0);
    const [hasNextAdjustments, setHasNextAdjustments] = useState(false);
    const [totalAdjustments, setTotalAdjustments] = useState(0);
    const [isLoadingAdjustments, setIsLoadingAdjustments] = useState(true);
    const [isLoadingMoreAdjustments, setIsLoadingMoreAdjustments] = useState(false);
    const [adjustmentsError, setAdjustmentsError] = useState<string | null>(null);

    const [historyTab, setHistoryTab] = useState<"opening-balance" | "adjustments">("opening-balance");

    const hasInitialScrolledRef = useRef(false);

    // Initial snapshot ID loading
    useEffect(() => {
        let isCurrent = true;
        void getLatestBrokerHoldingSnapshot(memberId, portfolioId)
            .then((snap) => {
                if (isCurrent) setLatestSnapshotId(snap.id);
            })
            .catch(() => {
                if (isCurrent) setLatestSnapshotId(null);
            });
        return () => {
            isCurrent = false;
        };
    }, [memberId, portfolioId]);

    // Paged imports loader
    const loadImports = useCallback(async (page: number, append = false) => {
        if (append) {
            setIsLoadingMoreImports(true);
        } else {
            setIsLoadingImports(true);
        }
        setImportsError(null);

        try {
            const pageData = await getBrokerHoldingOpeningBalanceImports(memberId, portfolioId, page, 20);
            setImports((prev) => (append ? [...prev, ...pageData.items] : pageData.items));
            setImportPage(pageData.page);
            setHasNextImports(pageData.hasNext);
            setTotalImports(pageData.totalElements);
        } catch (reason) {
            if (!append) setImports([]);
            setImportsError(reason instanceof Error ? reason.message : "개시 잔고 반영 이력을 불러오지 못했습니다.");
        } finally {
            setIsLoadingImports(false);
            setIsLoadingMoreImports(false);
        }
    }, [memberId, portfolioId]);

    useEffect(() => {
        let isCurrent = true;
        void getBrokerHoldingOpeningBalanceImports(memberId, portfolioId, 0, 20)
            .then((pageData) => {
                if (!isCurrent) return;
                setImports(pageData.items);
                setImportPage(pageData.page);
                setHasNextImports(pageData.hasNext);
                setTotalImports(pageData.totalElements);
                setImportsError(null);
            })
            .catch((reason: unknown) => {
                if (!isCurrent) return;
                setImports([]);
                setImportsError(reason instanceof Error ? reason.message : "개시 잔고 반영 이력을 불러오지 못했습니다.");
            })
            .finally(() => {
                if (isCurrent) {
                    setIsLoadingImports(false);
                }
            });

        return () => {
            isCurrent = false;
        };
    }, [memberId, portfolioId]);

    // Paged adjustments loader
    const loadAdjustments = useCallback(async (page: number, append = false) => {
        if (append) {
            setIsLoadingMoreAdjustments(true);
        } else {
            setIsLoadingAdjustments(true);
        }
        setAdjustmentsError(null);

        try {
            const pageData = await getBrokerHoldingAdjustments(memberId, portfolioId, page, 20);
            setAdjustments((prev) => (append ? [...prev, ...pageData.items] : pageData.items));
            setAdjustmentPage(pageData.page);
            setHasNextAdjustments(pageData.hasNext);
            setTotalAdjustments(pageData.totalElements);
        } catch (reason) {
            if (!append) setAdjustments([]);
            setAdjustmentsError(reason instanceof Error ? reason.message : "수량 차이 조정 이력을 불러오지 못했습니다.");
        } finally {
            setIsLoadingAdjustments(false);
            setIsLoadingMoreAdjustments(false);
        }
    }, [memberId, portfolioId]);

    useEffect(() => {
        let isCurrent = true;
        void getBrokerHoldingAdjustments(memberId, portfolioId, 0, 20)
            .then((pageData) => {
                if (!isCurrent) return;
                setAdjustments(pageData.items);
                setAdjustmentPage(pageData.page);
                setHasNextAdjustments(pageData.hasNext);
                setTotalAdjustments(pageData.totalElements);
                setAdjustmentsError(null);
            })
            .catch((reason: unknown) => {
                if (!isCurrent) return;
                setAdjustments([]);
                setAdjustmentsError(reason instanceof Error ? reason.message : "수량 차이 조정 이력을 불러오지 못했습니다.");
            })
            .finally(() => {
                if (isCurrent) {
                    setIsLoadingAdjustments(false);
                }
            });

        return () => {
            isCurrent = false;
        };
    }, [memberId, portfolioId]);

    useEffect(() => {
        const evaluateHistoryHash = () => {
            const hash = window.location.hash;
            if (hash === "#broker-holding-adjustments") {
                setHistoryTab("adjustments");
            } else if (hash === "#broker-holding-imports" || hash === "#broker-opening-balance") {
                setHistoryTab("opening-balance");
            }
        };

        evaluateHistoryHash();
        window.addEventListener("hashchange", evaluateHistoryHash);
        return () => window.removeEventListener("hashchange", evaluateHistoryHash);
    }, []);

    useEffect(() => {
        if (hasInitialScrolledRef.current) return;
        if (window.location.hash === "#broker-holding-imports" && imports.length > 0) {
            hasInitialScrolledRef.current = true;
            document.getElementById("broker-holding-imports")?.scrollIntoView({block: "start"});
        } else if (window.location.hash === "#broker-holding-adjustments" && adjustments.length > 0) {
            hasInitialScrolledRef.current = true;
            document.getElementById("broker-holding-adjustments")?.scrollIntoView({block: "start"});
        }
    }, [imports.length, adjustments.length]);

    const isAnyActionPending =
        isRefreshing ||
        approvingSnapshotItemId !== null ||
        revokingImportId !== null ||
        isBatchApproving ||
        approvingAdjustmentItemId !== null ||
        revokingAdjustmentId !== null;

    const refresh = async () => {
        if (isRefreshing) return;

        setIsRefreshing(true);
        setRefreshError(null);
        setActionError(null);
        setActionSuccess(null);
        setBatchResult(null);

        try {
            const snap = await refreshBrokerHoldingSnapshot(memberId, portfolioId);
            setLatestSnapshotId(snap.id);
            comparisonResource.replaceData(await getLatestBrokerHoldingSnapshotComparison(memberId, portfolioId));
        } catch (reason) {
            setRefreshError(reason instanceof Error ? reason.message : "보유 종목 스냅샷을 갱신하지 못했습니다.");
        } finally {
            setIsRefreshing(false);
        }
    };

    const reloadComparisonAndSnap = async () => {
        const [comparison, snap] = await Promise.all([
            getLatestBrokerHoldingSnapshotComparison(memberId, portfolioId),
            getLatestBrokerHoldingSnapshot(memberId, portfolioId).catch(() => null),
        ]);
        comparisonResource.replaceData(comparison);
        if (snap) {
            setLatestSnapshotId(snap.id);
        }
    };

    const reloadAfterImportChange = async () => {
        await reloadComparisonAndSnap();
        await Promise.all([
            loadImports(0, false),
            loadAdjustments(0, false),
        ]);
    };

    const reloadAfterAdjustmentChange = async () => {
        await reloadComparisonAndSnap();
        await Promise.all([
            loadAdjustments(0, false),
            loadImports(0, false),
        ]);
    };

    const openApprovalConfirm = (item: BrokerHoldingPreviewItem) => {
        setActionError(null);
        setActionSuccess(null);
        setRefreshError(null);
        setPendingRevokeId(null);
        setPendingRevokeAdjustmentId(null);
        setPendingAdjustmentItem(null);
        setIsConfirmingBatch(false);
        setPendingApprovalItem(item);
    };

    const confirmApproval = async () => {
        if (!pendingApprovalItem || pendingApprovalItem.snapshotItemId === null) return;

        const snapshotItemId = pendingApprovalItem.snapshotItemId;
        setApprovingSnapshotItemId(snapshotItemId);
        setActionError(null);
        setActionSuccess(null);
        setRefreshError(null);

        try {
            await approveBrokerHoldingOpeningBalance(memberId, portfolioId, snapshotItemId);
            await reloadAfterImportChange();
            setPendingApprovalItem(null);
            setActionSuccess("개시 잔고로 반영했습니다.");
        } catch (reason) {
            if (hasApiStatus(reason, 409)) {
                setActionError("현재 비교 상태에서는 개시 잔고로 반영할 수 없습니다. 최신 상태를 다시 확인해 주세요.");
            } else if (hasApiStatus(reason, 422)) {
                setActionError("활성 자산 카탈로그에 없는 종목이라 개시 잔고로 반영할 수 없습니다.");
            } else {
                setActionError(reason instanceof Error ? reason.message : "개시 잔고 반영에 실패했습니다.");
            }
        } finally {
            setApprovingSnapshotItemId(null);
        }
    };

    const openAdjustmentConfirm = (item: BrokerHoldingPreviewItem) => {
        setActionError(null);
        setActionSuccess(null);
        setRefreshError(null);
        setPendingApprovalItem(null);
        setPendingRevokeId(null);
        setPendingRevokeAdjustmentId(null);
        setIsConfirmingBatch(false);
        setPendingAdjustmentItem(item);
    };

    const confirmAdjustment = async () => {
        if (!pendingAdjustmentItem || pendingAdjustmentItem.snapshotItemId === null) return;

        const item = pendingAdjustmentItem;
        const snapshotItemId = pendingAdjustmentItem.snapshotItemId;
        const brokerQty = item.brokerQuantity ?? 0;
        const tgQty = item.tradeGuideQuantity ?? 0;
        const deltaQty = brokerQty - tgQty;
        const isSell = deltaQty < 0;

        setApprovingAdjustmentItemId(snapshotItemId);
        setActionError(null);
        setActionSuccess(null);
        setRefreshError(null);

        try {
            await approveBrokerHoldingAdjustment(memberId, portfolioId, snapshotItemId);
            await reloadAfterAdjustmentChange();
            setPendingAdjustmentItem(null);
            const deltaDisplay = isSell
                ? `-${Math.abs(deltaQty).toLocaleString("en-US")}주 (매도)`
                : `+${deltaQty.toLocaleString("en-US")}주 (매수)`;
            setActionSuccess(`${item.displayName} (${item.ticker}) 수량 차이 ${deltaDisplay} 조정을 원장에 반영했습니다.`);
        } catch (reason) {
            if (hasApiStatus(reason, 409)) {
                setActionError("수량 불일치 상태가 변경되었거나 이미 처리된 항목입니다. 보유 종목을 다시 갱신해 주세요.");
            } else if (hasApiStatus(reason, 422)) {
                setActionError(
                    reason instanceof Error && reason.message
                        ? reason.message
                        : "수량 차이 조정을 반영할 수 없는 상태입니다."
                );
            } else if (hasApiStatus(reason, 404)) {
                setActionError("최신 스냅샷에서 해당 증권사 보유 종목을 찾을 수 없습니다.");
            } else {
                setActionError(reason instanceof Error ? reason.message : "수량 차이 조정 반영에 실패했습니다.");
            }
        } finally {
            setApprovingAdjustmentItemId(null);
        }
    };

    const openBatchConfirm = () => {
        setActionError(null);
        setActionSuccess(null);
        setRefreshError(null);
        setPendingApprovalItem(null);
        setPendingAdjustmentItem(null);
        setPendingRevokeId(null);
        setPendingRevokeAdjustmentId(null);
        setIsConfirmingBatch(true);
    };

    const confirmBatchApproval = async () => {
        if (latestSnapshotId === null) return;

        setIsBatchApproving(true);
        setActionError(null);
        setActionSuccess(null);
        setRefreshError(null);
        setBatchResult(null);

        try {
            const result = await approveBrokerHoldingOpeningBalanceBatch(memberId, portfolioId, latestSnapshotId);
            setBatchResult(result);
            setIsConfirmingBatch(false);
            if (result.approvedCount > 0) {
                setActionSuccess(`개시 잔고 ${result.approvedCount}건을 일괄 반영했습니다.`);
            } else {
                setActionSuccess("반영 가능한 신규 종목이 없어 추가 반영 없이 완료되었습니다.");
            }
            await reloadAfterImportChange();
        } catch (reason) {
            if (hasApiStatus(reason, 409)) {
                setActionError("다른 요청이 같은 종목을 먼저 반영했습니다. 보유 종목을 다시 갱신한 뒤 시도해 주세요.");
            } else {
                setActionError(reason instanceof Error ? reason.message : "개시 잔고 일괄 반영에 실패했습니다.");
            }
        } finally {
            setIsBatchApproving(false);
        }
    };

    const openRevokeConfirm = (importId: number) => {
        setActionError(null);
        setActionSuccess(null);
        setRefreshError(null);
        setPendingApprovalItem(null);
        setPendingAdjustmentItem(null);
        setIsConfirmingBatch(false);
        setPendingRevokeAdjustmentId(null);
        setPendingRevokeId(importId);
    };

    const confirmRevoke = async (importId: number) => {
        setRevokingImportId(importId);
        setActionError(null);
        setActionSuccess(null);
        setRefreshError(null);

        try {
            await revokeBrokerHoldingOpeningBalanceImport(memberId, portfolioId, importId);
            await reloadAfterImportChange();
            setPendingRevokeId(null);
            setActionSuccess("개시 잔고 반영을 취소했습니다.");
        } catch (reason) {
            setActionError(reason instanceof Error ? reason.message : "개시 잔고 반영을 취소하지 못했습니다.");
        } finally {
            setRevokingImportId(null);
        }
    };

    const openRevokeAdjustmentConfirm = (adjustmentId: number) => {
        setActionError(null);
        setActionSuccess(null);
        setRefreshError(null);
        setPendingApprovalItem(null);
        setPendingAdjustmentItem(null);
        setIsConfirmingBatch(false);
        setPendingRevokeId(null);
        setPendingRevokeAdjustmentId(adjustmentId);
    };

    const confirmRevokeAdjustment = async (adjustmentId: number) => {
        setRevokingAdjustmentId(adjustmentId);
        setActionError(null);
        setActionSuccess(null);
        setRefreshError(null);

        try {
            await revokeBrokerHoldingAdjustment(memberId, portfolioId, adjustmentId);
            await reloadAfterAdjustmentChange();
            setPendingRevokeAdjustmentId(null);
            setActionSuccess("수량 차이 조정 반영을 취소했습니다.");
        } catch (reason) {
            setActionError(reason instanceof Error ? reason.message : "수량 차이 조정 반영을 취소하지 못했습니다.");
        } finally {
            setRevokingAdjustmentId(null);
        }
    };

    const onlyInBrokerItems = (comparisonResource.data?.items ?? []).filter(
        (item) => item.comparison === "ONLY_IN_BROKER" && item.snapshotItemId !== null,
    );
    const hasOnlyInBroker = onlyInBrokerItems.length > 0;

    const comparisonItems = comparisonResource.data?.items ?? [];
    const matchedCount = comparisonItems.filter((i) => i.comparison === "MATCHED").length;
    const mismatchCount = comparisonItems.filter((i) => i.comparison === "QUANTITY_MISMATCH").length;
    const onlyInBrokerCount = onlyInBrokerItems.length;
    const onlyInTradeGuideCount = comparisonItems.filter((i) => i.comparison === "ONLY_IN_TRADE_GUIDE").length;

    if (viewMode === "comparison") {
        return (
            <section className="content-section broker-holding-snapshot-section">
                <div className="section-heading">
                    <div>
                        <p className="section-label">4. HOLDING SNAPSHOT & COMPARISON</p>
                        <h2>보유 종목 스냅샷 갱신 및 비교</h2>
                    </div>
                </div>
                <p className="section-description">
                    직접 갱신한 시점의 증권사 보유 종목을 저장해 Trade Guide 보유 종목과 읽기 전용으로 비교합니다. 실제 증권사 주문은 절대 내지 않습니다.
                </p>

                {comparisonResource.isLoading ? (
                    <p className="status-message" aria-live="polite">저장된 보유 종목 비교를 불러오는 중입니다.</p>
                ) : null}

                {!comparisonResource.isLoading && isSnapshotMissing ? (
                    <div className="empty-state empty-state-action broker-holding-snapshot-empty-state">
                        <p>저장된 보유 종목 스냅샷이 없습니다. 갱신하면 증권사 보유 종목을 저장하고 비교합니다.</p>
                        <button type="button" className="primary-button" onClick={() => void refresh()} disabled={isAnyActionPending}>
                            {isRefreshing ? "갱신 중..." : "보유 종목 갱신"}
                        </button>
                    </div>
                ) : null}

                {!comparisonResource.isLoading && !isSnapshotMissing && comparisonResource.error ? (
                    <RequestError message={comparisonResource.error.message} onRetry={comparisonResource.refresh} retryLabel="저장된 보유 종목 다시 시도"/>
                ) : null}

                {!comparisonResource.isLoading && comparisonResource.data ? (
                    <>
                        <div className="broker-holding-snapshot-summary">
                            <p className="broker-preview-caption">
                                {brokerProviderLabel[comparisonResource.data.provider]} · {comparisonResource.data.maskedAccountNumber} · {formatDateTime(comparisonResource.data.syncedAt)} 저장
                            </p>
                            <div className="broker-holding-summary-actions">
                                <button
                                    type="button"
                                    className="primary-button"
                                    onClick={() => void refresh()}
                                    disabled={isAnyActionPending}
                                >
                                    {isRefreshing ? "갱신 중..." : "보유 종목 갱신"}
                                </button>
                            </div>
                        </div>

                        {/* Comparison statistics bar */}
                        <div className="broker-comparison-stats-bar" role="region" aria-label="보유 종목 비교 요약">
                            <div className={`broker-stat-item ${matchedCount > 0 ? "highlight" : ""}`}>
                                <span className="stat-label">수량 일치</span>
                                <span className="stat-value text-positive">
                                    {matchedCount}건
                                </span>
                                <span className="stat-sub">원장 수량과 동일</span>
                            </div>
                            <div className={`broker-stat-item ${mismatchCount > 0 ? "warning-theme" : ""}`}>
                                <span className="stat-label">수량 차이</span>
                                <span className={`stat-value ${mismatchCount > 0 ? "text-warning" : ""}`}>
                                    {mismatchCount}건
                                </span>
                                <span className="stat-sub">수량 불일치 종목</span>
                            </div>
                            <div className={`broker-stat-item ${onlyInBrokerCount > 0 ? "accent-theme" : ""}`}>
                                <span className="stat-label">증권사에만 있음</span>
                                <span className={`stat-value ${onlyInBrokerCount > 0 ? "text-accent" : ""}`}>
                                    {onlyInBrokerCount}건
                                </span>
                                <span className="stat-sub">개시 잔고 대상</span>
                            </div>
                            <div className={`broker-stat-item ${onlyInTradeGuideCount > 0 ? "muted-theme" : ""}`}>
                                <span className="stat-label">Trade Guide에만 있음</span>
                                <span className="stat-value text-muted">
                                    {onlyInTradeGuideCount}건
                                </span>
                                <span className="stat-sub">증권사 미보유</span>
                            </div>
                        </div>

                        {hasOnlyInBroker ? (
                            <div className="broker-flow-callout-panel broker-callout-highlight" role="region" aria-label="개시 잔고 반영 안내">
                                <div className="broker-flow-callout-text">
                                    <span className="broker-flow-callout-badge">개시 잔고 반영 대상</span>
                                    <strong>증권사에만 존재하는 미반영 종목이 {onlyInBrokerItems.length}건 있습니다.</strong>
                                    <p>내부 매매 원장에 개시 잔고로 반영하여 보유 수량을 일치시킬 수 있습니다. 5단계 '개시 잔고 반영 및 이력'에서 확인 후 안전하게 등록하세요.</p>
                                </div>
                                <button
                                    type="button"
                                    className="primary-button"
                                    onClick={() => onGoToStep?.(5, "opening-balance")}
                                >
                                    5단계: 개시 잔고 반영 및 이력으로 이동 →
                                </button>
                            </div>
                        ) : null}

                        {comparisonResource.data.items.length === 0 ? (
                            <p className="empty-state">비교할 보유 종목이 없습니다.</p>
                        ) : (
                            <ul className="broker-preview">
                                {comparisonResource.data.items.map((item) => {
                                    const isOnlyInBroker = item.comparison === "ONLY_IN_BROKER";
                                    const isQuantityMismatch = item.comparison === "QUANTITY_MISMATCH";
                                    const brokerQty = item.brokerQuantity ?? 0;
                                    const tgQty = item.tradeGuideQuantity ?? 0;
                                    const deltaQuantity = brokerQty - tgQty;
                                    const isPositiveMismatch = isQuantityMismatch && deltaQuantity > 0 && item.snapshotItemId !== null;
                                    const isNegativeMismatch = isQuantityMismatch && deltaQuantity < 0 && item.snapshotItemId !== null;
                                    const isZeroMismatch = isQuantityMismatch && deltaQuantity === 0;
                                    const isAdjustmentAvailable = isPositiveMismatch || isNegativeMismatch;
                                    const isSelectedForAdjustment = pendingAdjustmentItem !== null && pendingAdjustmentItem.snapshotItemId === item.snapshotItemId;
                                    const showAdjustmentAction = isAdjustmentAvailable && !isSelectedForAdjustment;
                                    const hasActions = isOnlyInBroker || showAdjustmentAction;

                                    return (
                                        <li key={`${item.market}-${item.ticker}`}>
                                            <div className={`broker-preview-item-main${hasActions ? " has-actions" : ""}`}>
                                                <div className="broker-snapshot-item-identity">
                                                    <strong className="broker-snapshot-item-name">{item.displayName}</strong>
                                                    <div className="broker-snapshot-item-symbol">
                                                        <span className="market-badge">{item.market}</span>
                                                        <span className="broker-ticker">{item.ticker}</span>
                                                    </div>
                                                </div>
                                                <div className="broker-holding-comparison">
                                                    <span className="broker-holding-status">{comparisonLabel[item.comparison]}</span>
                                                    <span className="broker-holding-quantities">
                                                        증권사 {formatQuantity(item.brokerQuantity)} · Trade Guide {formatQuantity(item.tradeGuideQuantity)}
                                                    </span>
                                                </div>
                                                {isOnlyInBroker ? (
                                                    <div className="broker-holding-item-actions">
                                                        <button
                                                            type="button"
                                                            className="secondary-button"
                                                            onClick={() => onGoToStep?.(5, "opening-balance")}
                                                            aria-label={`${item.displayName} 개시 잔고 반영 및 이력으로 이동`}
                                                        >
                                                            개시 잔고로 반영 →
                                                        </button>
                                                    </div>
                                                ) : null}
                                                {showAdjustmentAction ? (
                                                    <div className="broker-holding-item-actions">
                                                        <button
                                                            type="button"
                                                            className="primary-button"
                                                            onClick={() => openAdjustmentConfirm(item)}
                                                            disabled={isAnyActionPending}
                                                            aria-label={`${item.displayName} ${isNegativeMismatch ? "초과분 매도 조정 반영" : "수량 차이 조정 반영"}`}
                                                        >
                                                            {isNegativeMismatch ? "초과분 매도 조정 반영" : "수량 차이 조정 반영"}
                                                        </button>
                                                    </div>
                                                ) : null}
                                            </div>

                                            {isSelectedForAdjustment ? (
                                                <div
                                                    className="broker-holding-confirm-panel"
                                                    aria-label={deltaQuantity < 0 ? "초과분 매도 조정 반영 확인" : "수량 차이 조정 반영 확인"}
                                                >
                                                    <h3>{deltaQuantity < 0 ? "초과분 매도 조정을 반영하시겠습니까?" : "수량 차이 조정을 반영하시겠습니까?"}</h3>
                                                    <dl className="broker-holding-confirm-details">
                                                        <div><dt>종목</dt><dd>{item.displayName}</dd></div>
                                                        <div><dt>시장 · 티커</dt><dd>{item.market} · {item.ticker}</dd></div>
                                                        <div>
                                                            <dt>조정 수량</dt>
                                                            <dd>
                                                                <strong>
                                                                    {deltaQuantity < 0
                                                                        ? `-${Math.abs(deltaQuantity).toLocaleString("en-US")}주`
                                                                        : `+${deltaQuantity.toLocaleString("en-US")}주`}
                                                                </strong>
                                                                <span className="broker-confirm-quantity-sub"> (증권사 {formatQuantity(item.brokerQuantity)} / 원장 {formatQuantity(item.tradeGuideQuantity)})</span>
                                                            </dd>
                                                        </div>
                                                        <div>
                                                            <dt>기준 단가</dt>
                                                            <dd>
                                                                {deltaQuantity < 0
                                                                    ? "원장 평균 매입가 적용 (실현손익 0)"
                                                                    : formatPrice(item.brokerAveragePurchasePrice)}
                                                            </dd>
                                                        </div>
                                                        <div><dt>저장 시각</dt><dd>{comparisonResource.data?.syncedAt ? formatDateTime(comparisonResource.data.syncedAt) : "미기록"}</dd></div>
                                                    </dl>
                                                    {deltaQuantity < 0 ? (
                                                        <p className="broker-holding-confirm-note">
                                                            실제 체결가를 알 수 없어 <strong>원장 평균 매입가를 그대로 사용해 실현손익 없이 처리</strong>합니다.
                                                        </p>
                                                    ) : null}
                                                    <p className="broker-holding-confirm-note">
                                                        이 작업은 Trade Guide 내부 잔고 조정(ADJUSTMENT) 기록만 생성하며, <strong>실제 증권사 주문은 절대 내지 않습니다.</strong>
                                                    </p>
                                                    <p className="broker-holding-confirm-note">
                                                        반영 후 5단계 이력에서 언제든 <strong>반영 취소(원복)</strong>할 수 있습니다.
                                                    </p>
                                                    <div className="form-actions">
                                                        <button
                                                            type="button"
                                                            className="primary-button"
                                                            onClick={() => void confirmAdjustment()}
                                                            disabled={approvingAdjustmentItemId !== null}
                                                        >
                                                            {approvingAdjustmentItemId !== null
                                                                ? "조정 반영 중..."
                                                                : deltaQuantity < 0
                                                                    ? "초과분 매도 조정 반영"
                                                                    : "수량 차이 조정 반영"}
                                                        </button>
                                                        <button
                                                            type="button"
                                                            className="secondary-button"
                                                            onClick={() => setPendingAdjustmentItem(null)}
                                                            disabled={approvingAdjustmentItemId !== null}
                                                        >
                                                            취소
                                                        </button>
                                                    </div>
                                                </div>
                                            ) : null}

                                            {isZeroMismatch ? (
                                                <div className="broker-mismatch-manual-explanation" role="note">
                                                    <p className="broker-mismatch-explanation-text">
                                                        증권사 수량과 Trade Guide 원장 수량이 이미 같습니다. 잔고 조정이 필요하지 않습니다.
                                                    </p>
                                                </div>
                                            ) : null}
                                        </li>
                                    );
                                })}
                            </ul>
                        )}

                        {comparisonResource.data.unsupportedMarketCount > 0 ? (
                            <p className="section-description broker-unsupported-note">
                                지원하지 않는 시장 종목 {comparisonResource.data.unsupportedMarketCount}건은 이 비교에서 제외했습니다.
                            </p>
                        ) : null}
                    </>
                ) : null}

                <div className="broker-holding-action-feedback" aria-live="polite">
                    {refreshError ? <p className="form-error-message" role="alert">{refreshError}</p> : null}
                    {actionError ? <p className="form-error-message" role="alert">{actionError}</p> : null}
                    {actionSuccess ? <p className="form-success-message" role="status">{actionSuccess}</p> : null}
                </div>

                <div className="broker-step-nav-footer">
                    <button
                        type="button"
                        className="secondary-button"
                        onClick={onPrevStep}
                    >
                        ← 이전: 3단계 계좌 선택
                    </button>
                    <button
                        type="button"
                        className="primary-button"
                        onClick={hasOnlyInBroker && onGoToStep ? () => onGoToStep(5, "opening-balance") : onNextStep}
                    >
                        {hasOnlyInBroker
                            ? "다음: 5단계 개시 잔고 반영 및 이력 검토 →"
                            : "다음: 5단계 원장 반영 및 주문 이력 검토 →"}
                    </button>
                </div>
            </section>
        );
    }

    if (viewMode === "opening-balance") {
        return (
            <div className="broker-opening-balance-view">
                <div
                    className={`broker-opening-balance-context-card${!hasOnlyInBroker ? " context-card-synced" : ""}`}
                    role="region"
                    aria-label="개시 잔고 반영 실행 맥락"
                >
                    <div className="context-card-header">
                        <span className={`context-card-badge ${hasOnlyInBroker ? "badge-pending" : "badge-synced"}`}>
                            {hasOnlyInBroker ? `반영 대기 ${onlyInBrokerItems.length}건` : "확인 완료"}
                        </span>
                        <h4>
                            {hasOnlyInBroker
                                ? `4단계 스냅샷 비교 연계: 증권사에만 존재하는 미반영 종목 ${onlyInBrokerItems.length}건`
                                : "4단계 스냅샷 비교 연계: 미반영 신규 종목 없음"}
                        </h4>
                    </div>
                    <p className="context-card-desc">
                        {hasOnlyInBroker
                            ? "4단계 스냅샷 비교에서 증권사에만 존재하는 것으로 확인된 종목들입니다. 아래 미반영 신규 종목 목록에서 개별 확인하거나 상단 '개시 잔고 일괄 반영' 버튼을 통해 내부 원장에 개시 잔고(OPENING_BALANCE)로 안전하게 등록할 수 있습니다."
                            : "최신 스냅샷 비교 결과, 모든 보유 종목이 원장과 일치하거나 이미 개시 잔고로 등록되었습니다. 필요 시 아래 반영 이력을 검토하거나 4단계에서 스냅샷을 다시 갱신할 수 있습니다."}
                    </p>
                </div>

                <div className="broker-holding-snapshot-summary">
                    <p className="broker-preview-caption">
                        {comparisonResource.data
                            ? `${brokerProviderLabel[comparisonResource.data.provider]} · ${comparisonResource.data.maskedAccountNumber} · ${formatDateTime(comparisonResource.data.syncedAt)} 저장 기준`
                            : "스냅샷 기준 개시 잔고 관리"}
                    </p>
                    <div className="broker-holding-summary-actions">
                        {hasOnlyInBroker && latestSnapshotId !== null ? (
                            <button
                                type="button"
                                className="primary-button"
                                onClick={openBatchConfirm}
                                disabled={isAnyActionPending}
                            >
                                개시 잔고 일괄 반영 ({onlyInBrokerItems.length}건)
                            </button>
                        ) : null}
                    </div>
                </div>

                <div className="broker-accounts-safety-banner" role="note">
                    <span className="safety-badge">안전 가이드</span>
                    <p>
                        개시 잔고 반영은 <strong>Trade Guide 내부 거래 기록만 생성</strong>하며, <strong>실제 증권사 주문은 내지 않습니다.</strong>
                    </p>
                </div>

                {isConfirmingBatch ? (
                    <div className="broker-holding-confirm-panel broker-batch-confirm-panel" aria-label="개시 잔고 일괄 반영 확인">
                        <h3>증권사 보유 종목 {onlyInBrokerItems.length}건을 개시 잔고로 일괄 반영하시겠습니까?</h3>
                        <div className="broker-batch-confirm-notice" role="note">
                            <p>
                                <strong>안내:</strong> 이 작업은 <strong>Trade Guide 내부 거래 기록(개시 잔고)만 만들고</strong>, <strong>실제 증권사 주문은 내지 않습니다.</strong>
                            </p>
                        </div>
                        <p className="broker-holding-confirm-note">
                            최신 저장 스냅샷에서 증권사에만 존재하는 종목({onlyInBrokerItems.length}건)을 개시 잔고 거래 기록으로 일괄 생성합니다.
                        </p>
                        <ul className="broker-batch-target-list">
                            {onlyInBrokerItems.map((item) => (
                                <li key={`${item.market}-${item.ticker}`}>
                                    <span className="market-badge">{item.market}</span>
                                    <strong>{item.ticker}</strong>
                                    <span className="batch-item-name">{item.displayName}</span>
                                    <span className="batch-item-figures">({formatQuantity(item.brokerQuantity)} · 평단가 {formatPrice(item.brokerAveragePurchasePrice)})</span>
                                </li>
                            ))}
                        </ul>
                        <div className="form-actions">
                            <button
                                type="button"
                                className="primary-button"
                                onClick={() => void confirmBatchApproval()}
                                disabled={isBatchApproving}
                            >
                                {isBatchApproving ? "일괄 반영 중..." : "확인 및 일괄 반영"}
                            </button>
                            <button
                                type="button"
                                className="secondary-button"
                                onClick={() => setIsConfirmingBatch(false)}
                                disabled={isBatchApproving}
                            >
                                취소
                            </button>
                        </div>
                    </div>
                ) : null}

                {batchResult ? (
                    <div className="broker-batch-result-panel" role="region" aria-label="일괄 개시 잔고 반영 결과">
                        <div className="broker-batch-result-header">
                            <h4>일괄 개시 잔고 반영 결과 (스냅샷 #{batchResult.snapshotId})</h4>
                            <button
                                type="button"
                                className="quiet-action"
                                onClick={() => setBatchResult(null)}
                                aria-label="결과 닫기"
                            >
                                닫기
                            </button>
                        </div>
                        <p className="broker-batch-result-note">
                            이 작업은 Trade Guide 거래 기록만 생성되었으며 실제 증권사 주문은 내지 않았습니다.
                        </p>
                        <div className="broker-batch-result-counts">
                            <span className="batch-approved-count">반영 완료: {batchResult.approvedCount}건</span>
                            <span className="batch-skipped-count">제외: {batchResult.skippedCount}건</span>
                        </div>
                        {batchResult.skipped.length > 0 ? (
                            <div className="broker-batch-skipped-section">
                                <h5>종목별 제외 사유</h5>
                                <ul className="broker-batch-skipped-list">
                                    {batchResult.skipped.map((skip, idx) => (
                                        <li key={`${skip.market}-${skip.ticker}-${idx}`}>
                                            <div className="skipped-item-identity">
                                                <span className="market-badge">{skip.market}</span>
                                                <strong>{skip.ticker}</strong>
                                                <span className="skipped-name">{skip.displayName}</span>
                                            </div>
                                            <span className="skipped-reason-badge">
                                                {skipReasonLabels[skip.reason] ?? skip.reason}
                                            </span>
                                        </li>
                                    ))}
                                </ul>
                            </div>
                        ) : null}
                    </div>
                ) : null}

                {hasOnlyInBroker ? (
                    <div className="broker-only-in-broker-section">
                        <div className="broker-only-header">
                            <h4>미반영 신규 종목 ({onlyInBrokerItems.length}건)</h4>
                            <p>증권사에만 존재하는 종목입니다. 개별 또는 상단 일괄 버튼으로 개시 잔고를 생성할 수 있습니다.</p>
                        </div>
                        <ul className="broker-preview">
                            {onlyInBrokerItems.map((item) => {
                                const isSelectedForApproval = pendingApprovalItem !== null && pendingApprovalItem.snapshotItemId === item.snapshotItemId;
                                const showApproveAction = !isSelectedForApproval;

                                return (
                                    <li key={`${item.market}-${item.ticker}`}>
                                        <div className={`broker-preview-item-main${showApproveAction ? " has-actions" : ""}`}>
                                            <div className="broker-snapshot-item-identity">
                                                <strong className="broker-snapshot-item-name">{item.displayName}</strong>
                                                <div className="broker-snapshot-item-symbol">
                                                    <span className="market-badge">{item.market}</span>
                                                    <span className="broker-ticker">{item.ticker}</span>
                                                </div>
                                            </div>
                                            <div className="broker-holding-comparison">
                                                <span className="broker-holding-status">{comparisonLabel[item.comparison]}</span>
                                                <span className="broker-holding-quantities">
                                                    증권사 {formatQuantity(item.brokerQuantity)} · 평단가 {formatPrice(item.brokerAveragePurchasePrice)}
                                                </span>
                                            </div>
                                            {showApproveAction ? (
                                                <div className="broker-holding-item-actions">
                                                    <button
                                                        type="button"
                                                        className="primary-button"
                                                        onClick={() => openApprovalConfirm(item)}
                                                        disabled={isAnyActionPending}
                                                    >
                                                        개시 잔고로 반영
                                                    </button>
                                                </div>
                                            ) : null}
                                        </div>
                                        {isSelectedForApproval ? (
                                            <div className="broker-holding-confirm-panel" aria-label="개시 잔고 반영 확인">
                                                <h3>개시 잔고로 반영하시겠습니까?</h3>
                                                <dl className="broker-holding-confirm-details">
                                                    <div><dt>종목</dt><dd>{item.displayName}</dd></div>
                                                    <div><dt>시장 · 티커</dt><dd>{item.market} · {item.ticker}</dd></div>
                                                    <div><dt>수량</dt><dd>{formatQuantity(item.brokerQuantity)}</dd></div>
                                                    <div><dt>평단가</dt><dd>{formatPrice(item.brokerAveragePurchasePrice)}</dd></div>
                                                    <div><dt>저장 시각</dt><dd>{comparisonResource.data?.syncedAt ? formatDateTime(comparisonResource.data.syncedAt) : "미기록"}</dd></div>
                                                </dl>
                                                <p className="broker-holding-confirm-note">이 반영은 실제 체결 내역이 아닌 개시 잔고 기록입니다.</p>
                                                <p className="broker-holding-confirm-note">이 작업은 Trade Guide 거래 기록만 만들고 실제 증권사 주문은 내지 않습니다.</p>
                                                <div className="form-actions">
                                                    <button
                                                        type="button"
                                                        className="primary-button"
                                                        onClick={() => void confirmApproval()}
                                                        disabled={approvingSnapshotItemId !== null}
                                                    >
                                                        {approvingSnapshotItemId !== null ? "반영 중..." : "개시 잔고로 반영"}
                                                    </button>
                                                    <button
                                                        type="button"
                                                        className="secondary-button"
                                                        onClick={() => setPendingApprovalItem(null)}
                                                        disabled={approvingSnapshotItemId !== null}
                                                    >
                                                        취소
                                                    </button>
                                                </div>
                                            </div>
                                        ) : null}
                                    </li>
                                );
                            })}
                        </ul>
                    </div>
                ) : (
                    <p className="empty-state">최신 스냅샷에 증권사에만 존재하는 미반영 신규 종목이 없습니다.</p>
                )}

                <div className="broker-holding-action-feedback" aria-live="polite">
                    {actionError ? <p className="form-error-message" role="alert">{actionError}</p> : null}
                    {actionSuccess ? <p className="form-success-message" role="status">{actionSuccess}</p> : null}
                </div>

                <div className="broker-history-tab-nav" role="tablist" aria-label="원장 반영 이력 구분">
                    <button
                        type="button"
                        role="tab"
                        id="tab-opening-balance-history"
                        aria-selected={historyTab === "opening-balance"}
                        className={`broker-history-tab-btn ${historyTab === "opening-balance" ? "active" : ""}`}
                        onClick={() => setHistoryTab("opening-balance")}
                    >
                        개시 잔고 반영 이력 {totalImports > 0 ? `(${totalImports}건)` : ""}
                    </button>
                    <button
                        type="button"
                        role="tab"
                        id="tab-adjustment-history"
                        aria-selected={historyTab === "adjustments"}
                        className={`broker-history-tab-btn ${historyTab === "adjustments" ? "active" : ""}`}
                        onClick={() => setHistoryTab("adjustments")}
                    >
                        수량 차이 조정 이력 {totalAdjustments > 0 ? `(${totalAdjustments}건)` : ""}
                    </button>
                </div>

                <div
                    id="broker-holding-imports"
                    className="broker-holding-import-history"
                    role="tabpanel"
                    aria-labelledby="tab-opening-balance-history"
                    hidden={historyTab !== "opening-balance"}
                >
                    <div className="broker-holding-import-header">
                        <h3>개시 잔고 반영 이력</h3>
                        {totalImports > 0 ? (
                            <span className="broker-holding-import-count">총 {totalImports}건 중 {imports.length}건 표시</span>
                        ) : null}
                    </div>

                    {isLoadingImports ? (
                        <p className="status-message" aria-live="polite">개시 잔고 반영 이력을 불러오는 중입니다.</p>
                    ) : null}

                    {importsError ? (
                        <RequestError
                            message={importsError}
                            onRetry={() => void loadImports(0, false)}
                            retryLabel="개시 잔고 이력 다시 시도"
                        />
                    ) : null}

                    {!isLoadingImports && !importsError && imports.length === 0 ? (
                        <p className="empty-state">반영된 개시 잔고 이력이 없습니다.</p>
                    ) : null}

                    {!isLoadingImports && imports.length > 0 ? (
                        <>
                            <ul className="broker-holding-import-list">
                                {imports.map((importRecord) => (
                                    <li
                                        key={importRecord.id}
                                        className={importRecord.status === "REVOKED" ? "broker-holding-import-revoked" : undefined}
                                    >
                                        <div>
                                            <div className="broker-snapshot-item-identity">
                                                <strong className="broker-snapshot-item-name">{importRecord.displayName}</strong>
                                                <div className="broker-snapshot-item-symbol">
                                                    <span className="market-badge">{importRecord.market}</span>
                                                    <span className="broker-ticker">{importRecord.ticker}</span>
                                                </div>
                                            </div>
                                            <span>
                                                {formatQuantity(importRecord.quantity)} · 평단가 {formatPrice(importRecord.averagePurchasePrice)} · 저장 {importRecord.snapshotSyncedAt ? formatDateTime(importRecord.snapshotSyncedAt) : "미기록"}
                                            </span>
                                            <span>
                                                {importRecord.status === "ACTIVE"
                                                    ? `승인 ${formatDateTime(importRecord.approvedAt)}`
                                                    : `취소됨 · 승인 ${formatDateTime(importRecord.approvedAt)}`}
                                            </span>
                                        </div>
                                        {importRecord.status === "ACTIVE" ? (
                                            pendingRevokeId === importRecord.id ? (
                                                <div className="broker-holding-confirm-panel broker-holding-revoke-confirm">
                                                    <p>이 개시 잔고 반영을 취소하시겠습니까?</p>
                                                    <p className="broker-holding-confirm-note">
                                                        매매 원장에서 해당 개시 잔고 기록이 삭제되며 포트폴리오 잔고가 재계산됩니다. 실제 증권사 주문이나 잔고에는 영향을 주지 않습니다.
                                                    </p>
                                                    <div className="form-actions">
                                                        <button
                                                            type="button"
                                                            className="primary-button danger-button"
                                                            onClick={() => void confirmRevoke(importRecord.id)}
                                                            disabled={revokingImportId !== null}
                                                        >
                                                            {revokingImportId === importRecord.id ? "취소 처리 중..." : "취소 확인"}
                                                        </button>
                                                        <button
                                                            type="button"
                                                            className="secondary-button"
                                                            onClick={() => setPendingRevokeId(null)}
                                                            disabled={revokingImportId !== null}
                                                        >
                                                            닫기
                                                        </button>
                                                    </div>
                                                </div>
                                            ) : (
                                                <button
                                                    type="button"
                                                    className="quiet-action"
                                                    onClick={() => openRevokeConfirm(importRecord.id)}
                                                    disabled={isAnyActionPending}
                                                >
                                                    반영 취소
                                                </button>
                                            )
                                        ) : (
                                            <span className="broker-holding-import-status">취소됨</span>
                                        )}
                                    </li>
                                ))}
                            </ul>
                            {hasNextImports ? (
                                <div className="broker-history-load-more">
                                    <button
                                        type="button"
                                        className="quiet-action load-more-button"
                                        onClick={() => void loadImports(importPage + 1, true)}
                                        disabled={isLoadingMoreImports}
                                    >
                                        {isLoadingMoreImports ? "불러오는 중..." : "더 보기"}
                                    </button>
                                </div>
                            ) : null}
                        </>
                    ) : null}
                </div>

                <div
                    id="broker-holding-adjustments"
                    className="broker-holding-adjustment-history"
                    role="tabpanel"
                    aria-labelledby="tab-adjustment-history"
                    hidden={historyTab !== "adjustments"}
                >
                    <div className="broker-holding-import-header">
                        <h3>수량 차이 조정 반영 이력</h3>
                        {totalAdjustments > 0 ? (
                            <span className="broker-holding-import-count">총 {totalAdjustments}건 중 {adjustments.length}건 표시</span>
                        ) : null}
                    </div>

                    {isLoadingAdjustments ? (
                        <p className="status-message" aria-live="polite">수량 차이 조정 이력을 불러오는 중입니다.</p>
                    ) : null}

                    {adjustmentsError ? (
                        <RequestError
                            message={adjustmentsError}
                            onRetry={() => void loadAdjustments(0, false)}
                            retryLabel="수량 차이 조정 이력 다시 시도"
                        />
                    ) : null}

                    {!isLoadingAdjustments && !adjustmentsError && adjustments.length === 0 ? (
                        <p className="empty-state">반영된 수량 차이 조정 이력이 없습니다.</p>
                    ) : null}

                    {!isLoadingAdjustments && adjustments.length > 0 ? (
                        <>
                            <ul className="broker-holding-import-list">
                                {adjustments.map((adjRecord) => {
                                    const isSellAdjustment = adjRecord.brokerQuantity < adjRecord.ledgerQuantityBefore;
                                    return (
                                        <li
                                            key={adjRecord.id}
                                            className={adjRecord.status === "REVOKED" ? "broker-holding-import-revoked" : undefined}
                                        >
                                            <div>
                                                <div className="broker-snapshot-item-identity">
                                                    <strong className="broker-snapshot-item-name">{adjRecord.displayName}</strong>
                                                    <div className="broker-snapshot-item-symbol">
                                                        <span className="market-badge">{adjRecord.market}</span>
                                                        <span className="broker-ticker">{adjRecord.ticker}</span>
                                                        <span className={`status-pill ${isSellAdjustment ? "pill-sell" : "pill-buy"}`}>
                                                            {isSellAdjustment ? "매도 조정" : "매수 조정"}
                                                        </span>
                                                    </div>
                                                </div>
                                                <span>
                                                    조정 수량: <strong>{isSellAdjustment ? `-${formatQuantity(adjRecord.deltaQuantity)}` : `+${formatQuantity(adjRecord.deltaQuantity)}`}</strong>
                                                    {" · "}
                                                    반영 전 원장 {formatQuantity(adjRecord.ledgerQuantityBefore)} → 증권사 {formatQuantity(adjRecord.brokerQuantity)}
                                                    {" · "}
                                                    기준 단가 {formatPrice(adjRecord.unitPrice)}
                                                </span>
                                                <span>
                                                    스냅샷 저장 {adjRecord.snapshotSyncedAt ? formatDateTime(adjRecord.snapshotSyncedAt) : "미기록"}
                                                    {" · "}
                                                    {adjRecord.status === "ACTIVE"
                                                        ? `승인 ${formatDateTime(adjRecord.approvedAt)}`
                                                        : `취소됨 · 승인 ${formatDateTime(adjRecord.approvedAt)}`}
                                                </span>
                                            </div>
                                            {adjRecord.status === "ACTIVE" ? (
                                                pendingRevokeAdjustmentId === adjRecord.id ? (
                                                    <div className="broker-holding-confirm-panel broker-holding-revoke-confirm">
                                                        <p>이 수량 차이 조정 반영을 취소하시겠습니까?</p>
                                                        <p className="broker-holding-confirm-note">
                                                            매매 원장에서 해당 잔고 조정 기록이 삭제되며 포트폴리오 잔고가 재계산됩니다. 실제 증권사 주문이나 잔고에는 영향을 주지 않습니다.
                                                        </p>
                                                        <div className="form-actions">
                                                            <button
                                                                type="button"
                                                                className="primary-button danger-button"
                                                                onClick={() => void confirmRevokeAdjustment(adjRecord.id)}
                                                                disabled={revokingAdjustmentId !== null}
                                                            >
                                                                {revokingAdjustmentId === adjRecord.id ? "취소 처리 중..." : "취소 확인"}
                                                            </button>
                                                            <button
                                                                type="button"
                                                                className="secondary-button"
                                                                onClick={() => setPendingRevokeAdjustmentId(null)}
                                                                disabled={revokingAdjustmentId !== null}
                                                            >
                                                                닫기
                                                            </button>
                                                        </div>
                                                    </div>
                                                ) : (
                                                    <button
                                                        type="button"
                                                        className="quiet-action"
                                                        onClick={() => openRevokeAdjustmentConfirm(adjRecord.id)}
                                                        disabled={isAnyActionPending}
                                                    >
                                                        반영 취소
                                                    </button>
                                                )
                                            ) : (
                                                <span className="broker-holding-import-status">취소됨</span>
                                            )}
                                        </li>
                                    );
                                })}
                            </ul>
                            {hasNextAdjustments ? (
                                <div className="broker-history-load-more">
                                    <button
                                        type="button"
                                        className="quiet-action load-more-button"
                                        onClick={() => void loadAdjustments(adjustmentPage + 1, true)}
                                        disabled={isLoadingMoreAdjustments}
                                    >
                                        {isLoadingMoreAdjustments ? "불러오는 중..." : "더 보기"}
                                    </button>
                                </div>
                            ) : null}
                        </>
                    ) : null}
                </div>
            </div>
        );
    }

    return (
        <section className="content-section broker-holding-snapshot-section">
            <div className="section-heading">
                <div>
                    <p className="section-label">3. HOLDING SNAPSHOT & 4. COMPARISON & 5. OPENING BALANCE</p>
                    <h2>보유 종목 스냅샷 및 개시 잔고</h2>
                </div>
            </div>
            <p className="section-description">
                직접 갱신한 시점의 증권사 보유 종목을 저장해 Trade Guide 보유 종목과 읽기 전용으로 비교합니다. 증권사에만 존재하는 종목은 개시 잔고로 단건 또는 일괄 반영할 수 있으며, 실제 증권사 주문은 절대 내지 않습니다.
            </p>

            {comparisonResource.isLoading ? (
                <p className="status-message" aria-live="polite">저장된 보유 종목 비교를 불러오는 중입니다.</p>
            ) : null}

            {!comparisonResource.isLoading && isSnapshotMissing ? (
                <div className="empty-state empty-state-action broker-holding-snapshot-empty-state">
                    <p>저장된 보유 종목 스냅샷이 없습니다. 갱신하면 증권사 보유 종목을 저장하고 비교합니다.</p>
                    <button type="button" className="quiet-action" onClick={() => void refresh()} disabled={isAnyActionPending}>
                        {isRefreshing ? "갱신 중..." : "보유 종목 갱신"}
                    </button>
                </div>
            ) : null}

            {!comparisonResource.isLoading && !isSnapshotMissing && comparisonResource.error ? (
                <RequestError message={comparisonResource.error.message} onRetry={comparisonResource.refresh} retryLabel="저장된 보유 종목 다시 시도"/>
            ) : null}

            {!comparisonResource.isLoading && comparisonResource.data ? (
                <>
                    <div className="broker-holding-snapshot-summary">
                        <p className="broker-preview-caption">
                            {brokerProviderLabel[comparisonResource.data.provider]} · {comparisonResource.data.maskedAccountNumber} · {formatDateTime(comparisonResource.data.syncedAt)} 저장
                        </p>
                        <div className="broker-holding-summary-actions">
                            {hasOnlyInBroker && latestSnapshotId !== null ? (
                                <button
                                    type="button"
                                    className="primary-button"
                                    onClick={openBatchConfirm}
                                    disabled={isAnyActionPending}
                                >
                                    개시 잔고 일괄 반영 ({onlyInBrokerItems.length}건)
                                </button>
                            ) : null}
                            <button
                                type="button"
                                className="secondary-button"
                                onClick={() => void refresh()}
                                disabled={isAnyActionPending}
                            >
                                {isRefreshing ? "갱신 중..." : "보유 종목 갱신"}
                            </button>
                        </div>
                    </div>

                    {hasOnlyInBroker ? (
                        <p className="broker-batch-note">
                            이 작업은 Trade Guide 거래 기록만 만들고 실제 증권사 주문은 내지 않습니다.
                        </p>
                    ) : null}

                    {isConfirmingBatch ? (
                        <div className="broker-holding-confirm-panel broker-batch-confirm-panel" aria-label="개시 잔고 일괄 반영 확인">
                            <h3>증권사 보유 종목 {onlyInBrokerItems.length}건을 개시 잔고로 일괄 반영하시겠습니까?</h3>
                            <div className="broker-batch-confirm-notice" role="note">
                                <p>
                                    <strong>안내:</strong> 이 작업은 <strong>Trade Guide 내부 거래 기록(개시 잔고)만 만들고</strong>, <strong>실제 증권사 주문은 내지 않습니다.</strong>
                                </p>
                            </div>
                            <p className="broker-holding-confirm-note">
                                최신 저장 스냅샷에서 증권사에만 존재하는 종목({onlyInBrokerItems.length}건)을 개시 잔고 거래 기록으로 일괄 생성합니다.
                            </p>
                            <ul className="broker-batch-target-list">
                                {onlyInBrokerItems.map((item) => (
                                    <li key={`${item.market}-${item.ticker}`}>
                                        <span className="market-badge">{item.market}</span>
                                        <strong>{item.ticker}</strong>
                                        <span className="batch-item-name">{item.displayName}</span>
                                        <span className="batch-item-figures">({formatQuantity(item.brokerQuantity)} · 평단가 {formatPrice(item.brokerAveragePurchasePrice)})</span>
                                    </li>
                                ))}
                            </ul>
                            <div className="form-actions">
                                <button
                                    type="button"
                                    className="primary-button"
                                    onClick={() => void confirmBatchApproval()}
                                    disabled={isBatchApproving}
                                >
                                    {isBatchApproving ? "일괄 반영 중..." : "확인 및 일괄 반영"}
                                </button>
                                <button
                                    type="button"
                                    className="secondary-button"
                                    onClick={() => setIsConfirmingBatch(false)}
                                    disabled={isBatchApproving}
                                >
                                    취소
                                </button>
                            </div>
                        </div>
                    ) : null}

                    {batchResult ? (
                        <div className="broker-batch-result-panel" role="region" aria-label="일괄 개시 잔고 반영 결과">
                            <div className="broker-batch-result-header">
                                <h4>일괄 개시 잔고 반영 결과 (스냅샷 #{batchResult.snapshotId})</h4>
                                <button
                                    type="button"
                                    className="quiet-action"
                                    onClick={() => setBatchResult(null)}
                                    aria-label="결과 닫기"
                                >
                                    닫기
                                </button>
                            </div>
                            <p className="broker-batch-result-note">
                                이 작업은 Trade Guide 거래 기록만 생성되었으며 실제 증권사 주문은 내지 않았습니다.
                            </p>
                            <div className="broker-batch-result-counts">
                                <span className="batch-approved-count">반영 완료: {batchResult.approvedCount}건</span>
                                <span className="batch-skipped-count">제외: {batchResult.skippedCount}건</span>
                            </div>
                            {batchResult.skipped.length > 0 ? (
                                <div className="broker-batch-skipped-section">
                                    <h5>종목별 제외 사유</h5>
                                    <ul className="broker-batch-skipped-list">
                                        {batchResult.skipped.map((skip, idx) => (
                                            <li key={`${skip.market}-${skip.ticker}-${idx}`}>
                                                <div className="skipped-item-identity">
                                                    <span className="market-badge">{skip.market}</span>
                                                    <strong>{skip.ticker}</strong>
                                                    <span className="skipped-name">{skip.displayName}</span>
                                                </div>
                                                <span className="skipped-reason-badge">
                                                    {skipReasonLabels[skip.reason] ?? skip.reason}
                                                </span>
                                            </li>
                                        ))}
                                    </ul>
                                </div>
                            ) : null}
                        </div>
                    ) : null}

                    {comparisonResource.data.items.length === 0 ? (
                        <p className="empty-state">비교할 보유 종목이 없습니다.</p>
                    ) : (
                        <ul className="broker-preview">
                            {comparisonResource.data.items.map((item) => {
                                const canApprove = item.comparison === "ONLY_IN_BROKER" && item.snapshotItemId !== null;
                                const isSelectedForApproval = pendingApprovalItem !== null && pendingApprovalItem.snapshotItemId === item.snapshotItemId;
                                const showApproveAction = canApprove && !isSelectedForApproval;

                                const isQuantityMismatch = item.comparison === "QUANTITY_MISMATCH";
                                const brokerQty = item.brokerQuantity ?? 0;
                                const tgQty = item.tradeGuideQuantity ?? 0;
                                const deltaQuantity = brokerQty - tgQty;
                                const isPositiveMismatch = isQuantityMismatch && deltaQuantity > 0 && item.snapshotItemId !== null;
                                const isNegativeMismatch = isQuantityMismatch && deltaQuantity < 0 && item.snapshotItemId !== null;
                                const isZeroMismatch = isQuantityMismatch && deltaQuantity === 0;
                                const isAdjustmentAvailable = isPositiveMismatch || isNegativeMismatch;
                                const isSelectedForAdjustment = pendingAdjustmentItem !== null && pendingAdjustmentItem.snapshotItemId === item.snapshotItemId;
                                const showAdjustmentAction = isAdjustmentAvailable && !isSelectedForAdjustment;

                                const hasActions = showApproveAction || showAdjustmentAction;
                                const itemClasses = [
                                    (isSelectedForApproval || isSelectedForAdjustment) ? "broker-preview-item-with-confirm" : "",
                                    hasActions ? "broker-preview-item-has-actions" : "",
                                ].filter(Boolean).join(" ") || undefined;

                                return (
                                    <li key={`${item.market}-${item.ticker}`} className={itemClasses}>
                                        <div className={`broker-preview-item-main${hasActions ? " has-actions" : ""}`}>
                                            <div className="broker-snapshot-item-identity">
                                                <strong className="broker-snapshot-item-name">{item.displayName}</strong>
                                                <div className="broker-snapshot-item-symbol">
                                                    <span className="market-badge">{item.market}</span>
                                                    <span className="broker-ticker">{item.ticker}</span>
                                                </div>
                                            </div>
                                            <div className="broker-holding-comparison">
                                                <span className="broker-holding-status">{comparisonLabel[item.comparison]}</span>
                                                <span className="broker-holding-quantities">
                                                    증권사 {formatQuantity(item.brokerQuantity)} · Trade Guide {formatQuantity(item.tradeGuideQuantity)}
                                                </span>
                                            </div>
                                            {showApproveAction ? (
                                                <div className="broker-holding-item-actions">
                                                    <button
                                                        type="button"
                                                        className="quiet-action"
                                                        onClick={() => openApprovalConfirm(item)}
                                                        disabled={isAnyActionPending}
                                                    >
                                                        개시 잔고로 반영
                                                    </button>
                                                </div>
                                            ) : null}
                                            {showAdjustmentAction ? (
                                                <div className="broker-holding-item-actions">
                                                    <button
                                                        type="button"
                                                        className="primary-button"
                                                        onClick={() => openAdjustmentConfirm(item)}
                                                        disabled={isAnyActionPending}
                                                        aria-label={`${item.displayName} ${isNegativeMismatch ? "초과분 매도 조정 반영" : "수량 차이 조정 반영"}`}
                                                    >
                                                        {isNegativeMismatch ? "초과분 매도 조정 반영" : "수량 차이 조정 반영"}
                                                    </button>
                                                </div>
                                            ) : null}
                                        </div>
                                        {isSelectedForApproval ? (
                                            <div className="broker-holding-confirm-panel" aria-label="개시 잔고 반영 확인">
                                                <h3>개시 잔고로 반영하시겠습니까?</h3>
                                                <dl className="broker-holding-confirm-details">
                                                    <div><dt>종목</dt><dd>{item.displayName}</dd></div>
                                                    <div><dt>시장 · 티커</dt><dd>{item.market} · {item.ticker}</dd></div>
                                                    <div><dt>수량</dt><dd>{formatQuantity(item.brokerQuantity)}</dd></div>
                                                    <div><dt>평단가</dt><dd>{formatPrice(item.brokerAveragePurchasePrice)}</dd></div>
                                                    <div><dt>저장 시각</dt><dd>{comparisonResource.data?.syncedAt ? formatDateTime(comparisonResource.data.syncedAt) : "미기록"}</dd></div>
                                                </dl>
                                                <p className="broker-holding-confirm-note">이 반영은 실제 체결 내역이 아닌 개시 잔고 기록입니다.</p>
                                                <p className="broker-holding-confirm-note">이 작업은 Trade Guide 거래 기록만 만들고 실제 증권사 주문은 내지 않습니다.</p>
                                                <p className="broker-holding-confirm-note">수량 차이는 자동으로 수정하지 않습니다.</p>
                                                <div className="form-actions">
                                                    <button
                                                        type="button"
                                                        className="primary-button"
                                                        onClick={() => void confirmApproval()}
                                                        disabled={approvingSnapshotItemId !== null}
                                                    >
                                                        {approvingSnapshotItemId !== null ? "반영 중..." : "개시 잔고로 반영"}
                                                    </button>
                                                    <button
                                                        type="button"
                                                        className="secondary-button"
                                                        onClick={() => setPendingApprovalItem(null)}
                                                        disabled={approvingSnapshotItemId !== null}
                                                    >
                                                        취소
                                                    </button>
                                                </div>
                                            </div>
                                        ) : null}

                                        {isSelectedForAdjustment ? (
                                            <div
                                                className="broker-holding-confirm-panel"
                                                aria-label={deltaQuantity < 0 ? "초과분 매도 조정 반영 확인" : "수량 차이 조정 반영 확인"}
                                            >
                                                <h3>{deltaQuantity < 0 ? "초과분 매도 조정을 반영하시겠습니까?" : "수량 차이 조정을 반영하시겠습니까?"}</h3>
                                                <dl className="broker-holding-confirm-details">
                                                    <div><dt>종목</dt><dd>{item.displayName}</dd></div>
                                                    <div><dt>시장 · 티커</dt><dd>{item.market} · {item.ticker}</dd></div>
                                                    <div>
                                                        <dt>조정 수량</dt>
                                                        <dd>
                                                            <strong>
                                                                {deltaQuantity < 0
                                                                    ? `-${Math.abs(deltaQuantity).toLocaleString("en-US")}주`
                                                                    : `+${deltaQuantity.toLocaleString("en-US")}주`}
                                                            </strong>
                                                            <span className="broker-confirm-quantity-sub"> (증권사 {formatQuantity(item.brokerQuantity)} / 원장 {formatQuantity(item.tradeGuideQuantity)})</span>
                                                        </dd>
                                                    </div>
                                                    <div>
                                                        <dt>기준 단가</dt>
                                                        <dd>
                                                            {deltaQuantity < 0
                                                                ? "원장 평균 매입가 적용 (실현손익 0)"
                                                                : formatPrice(item.brokerAveragePurchasePrice)}
                                                        </dd>
                                                    </div>
                                                    <div><dt>저장 시각</dt><dd>{comparisonResource.data?.syncedAt ? formatDateTime(comparisonResource.data.syncedAt) : "미기록"}</dd></div>
                                                </dl>
                                                {deltaQuantity < 0 ? (
                                                    <p className="broker-holding-confirm-note">
                                                        실제 체결가를 알 수 없어 <strong>원장 평균 매입가를 그대로 사용해 실현손익 없이 처리</strong>합니다.
                                                    </p>
                                                ) : null}
                                                <p className="broker-holding-confirm-note">
                                                    이 작업은 Trade Guide 내부 잔고 조정(ADJUSTMENT) 기록만 생성하며, <strong>실제 증권사 주문은 절대 내지 않습니다.</strong>
                                                </p>
                                                <p className="broker-holding-confirm-note">
                                                    반영 후 5단계 이력에서 언제든 <strong>반영 취소(원복)</strong>할 수 있습니다.
                                                </p>
                                                <div className="form-actions">
                                                    <button
                                                        type="button"
                                                        className="primary-button"
                                                        onClick={() => void confirmAdjustment()}
                                                        disabled={approvingAdjustmentItemId !== null}
                                                    >
                                                        {approvingAdjustmentItemId !== null
                                                            ? "조정 반영 중..."
                                                            : deltaQuantity < 0
                                                                ? "초과분 매도 조정 반영"
                                                                : "수량 차이 조정 반영"}
                                                    </button>
                                                    <button
                                                        type="button"
                                                        className="secondary-button"
                                                        onClick={() => setPendingAdjustmentItem(null)}
                                                        disabled={approvingAdjustmentItemId !== null}
                                                    >
                                                        취소
                                                    </button>
                                                </div>
                                            </div>
                                        ) : null}

                                        {isZeroMismatch ? (
                                            <div className="broker-mismatch-manual-explanation" role="note">
                                                <p className="broker-mismatch-explanation-text">
                                                    증권사 수량과 Trade Guide 원장 수량이 이미 같습니다. 잔고 조정이 필요하지 않습니다.
                                                </p>
                                            </div>
                                        ) : null}
                                    </li>
                                );
                            })}
                        </ul>
                    )}

                    {comparisonResource.data.unsupportedMarketCount > 0 ? (
                        <p className="section-description broker-unsupported-note">
                            지원하지 않는 시장 종목 {comparisonResource.data.unsupportedMarketCount}건은 이 비교에서 제외했습니다.
                        </p>
                    ) : null}
                </>
            ) : null}

            <div className="broker-holding-action-feedback" aria-live="polite">
                {refreshError ? <p className="form-error-message" role="alert">{refreshError}</p> : null}
                {actionError ? <p className="form-error-message" role="alert">{actionError}</p> : null}
                {actionSuccess ? <p className="form-success-message" role="status">{actionSuccess}</p> : null}
            </div>

            <div className="broker-history-tab-nav" role="tablist" aria-label="원장 반영 이력 구분">
                <button
                    type="button"
                    role="tab"
                    id="tab-all-opening-balance-history"
                    aria-selected={historyTab === "opening-balance"}
                    className={`broker-history-tab-btn ${historyTab === "opening-balance" ? "active" : ""}`}
                    onClick={() => setHistoryTab("opening-balance")}
                >
                    개시 잔고 반영 이력 {totalImports > 0 ? `(${totalImports}건)` : ""}
                </button>
                <button
                    type="button"
                    role="tab"
                    id="tab-all-adjustment-history"
                    aria-selected={historyTab === "adjustments"}
                    className={`broker-history-tab-btn ${historyTab === "adjustments" ? "active" : ""}`}
                    onClick={() => setHistoryTab("adjustments")}
                >
                    수량 차이 조정 이력 {totalAdjustments > 0 ? `(${totalAdjustments}건)` : ""}
                </button>
            </div>

            <div
                id="broker-holding-imports"
                className="broker-holding-import-history"
                role="tabpanel"
                aria-labelledby="tab-all-opening-balance-history"
                hidden={historyTab !== "opening-balance"}
            >
                <div className="broker-holding-import-header">
                    <h3>개시 잔고 반영 이력</h3>
                    {totalImports > 0 ? (
                        <span className="broker-holding-import-count">총 {totalImports}건 중 {imports.length}건 표시</span>
                    ) : null}
                </div>

                {isLoadingImports ? (
                    <p className="status-message" aria-live="polite">개시 잔고 반영 이력을 불러오는 중입니다.</p>
                ) : null}

                {importsError ? (
                    <RequestError
                        message={importsError}
                        onRetry={() => void loadImports(0, false)}
                        retryLabel="개시 잔고 이력 다시 시도"
                    />
                ) : null}

                {!isLoadingImports && !importsError && imports.length === 0 ? (
                    <p className="empty-state">반영된 개시 잔고 이력이 없습니다.</p>
                ) : null}

                {!isLoadingImports && imports.length > 0 ? (
                    <>
                        <ul className="broker-holding-import-list">
                            {imports.map((importRecord) => (
                                <li
                                    key={importRecord.id}
                                    className={importRecord.status === "REVOKED" ? "broker-holding-import-revoked" : undefined}
                                >
                                    <div>
                                        <div className="broker-snapshot-item-identity">
                                            <strong className="broker-snapshot-item-name">{importRecord.displayName}</strong>
                                            <div className="broker-snapshot-item-symbol">
                                                <span className="market-badge">{importRecord.market}</span>
                                                <span className="broker-ticker">{importRecord.ticker}</span>
                                            </div>
                                        </div>
                                        <span>
                                            {formatQuantity(importRecord.quantity)} · 평단가 {formatPrice(importRecord.averagePurchasePrice)} · 저장 {importRecord.snapshotSyncedAt ? formatDateTime(importRecord.snapshotSyncedAt) : "미기록"}
                                        </span>
                                        <span>
                                            {importRecord.status === "ACTIVE"
                                                ? `승인 ${formatDateTime(importRecord.approvedAt)}`
                                                : `취소됨 · 승인 ${formatDateTime(importRecord.approvedAt)}`}
                                        </span>
                                    </div>
                                    {importRecord.status === "ACTIVE" ? (
                                        pendingRevokeId === importRecord.id ? (
                                            <div className="broker-holding-confirm-panel broker-holding-revoke-confirm">
                                                <p>이 개시 잔고 반영을 취소하시겠습니까?</p>
                                                <p className="broker-holding-confirm-note">
                                                    매매 원장에서 해당 개시 잔고 기록이 삭제되며 포트폴리오 잔고가 재계산됩니다. 실제 증권사 주문이나 잔고에는 영향을 주지 않습니다.
                                                </p>
                                                <div className="form-actions">
                                                    <button
                                                        type="button"
                                                        className="primary-button danger-button"
                                                        onClick={() => void confirmRevoke(importRecord.id)}
                                                        disabled={revokingImportId !== null}
                                                    >
                                                        {revokingImportId === importRecord.id ? "취소 처리 중..." : "취소 확인"}
                                                    </button>
                                                    <button
                                                        type="button"
                                                        className="secondary-button"
                                                        onClick={() => setPendingRevokeId(null)}
                                                        disabled={revokingImportId !== null}
                                                    >
                                                        닫기
                                                    </button>
                                                </div>
                                            </div>
                                        ) : (
                                            <button
                                                type="button"
                                                className="quiet-action"
                                                onClick={() => openRevokeConfirm(importRecord.id)}
                                                disabled={isAnyActionPending}
                                            >
                                                반영 취소
                                            </button>
                                        )
                                    ) : (
                                        <span className="broker-holding-import-status">취소됨</span>
                                    )}
                                </li>
                            ))}
                        </ul>
                        {hasNextImports ? (
                            <div className="broker-history-load-more">
                                <button
                                    type="button"
                                    className="quiet-action load-more-button"
                                    onClick={() => void loadImports(importPage + 1, true)}
                                    disabled={isLoadingMoreImports}
                                >
                                    {isLoadingMoreImports ? "불러오는 중..." : "더 보기"}
                                </button>
                            </div>
                        ) : null}
                    </>
                ) : null}
            </div>

            <div
                id="broker-holding-adjustments"
                className="broker-holding-adjustment-history"
                role="tabpanel"
                aria-labelledby="tab-all-adjustment-history"
                hidden={historyTab !== "adjustments"}
            >
                <div className="broker-holding-import-header">
                    <h3>수량 차이 조정 반영 이력</h3>
                    {totalAdjustments > 0 ? (
                        <span className="broker-holding-import-count">총 {totalAdjustments}건 중 {adjustments.length}건 표시</span>
                    ) : null}
                </div>

                {isLoadingAdjustments ? (
                    <p className="status-message" aria-live="polite">수량 차이 조정 이력을 불러오는 중입니다.</p>
                ) : null}

                {adjustmentsError ? (
                    <RequestError
                        message={adjustmentsError}
                        onRetry={() => void loadAdjustments(0, false)}
                        retryLabel="수량 차이 조정 이력 다시 시도"
                    />
                ) : null}

                {!isLoadingAdjustments && !adjustmentsError && adjustments.length === 0 ? (
                    <p className="empty-state">반영된 수량 차이 조정 이력이 없습니다.</p>
                ) : null}

                {!isLoadingAdjustments && adjustments.length > 0 ? (
                    <>
                        <ul className="broker-holding-import-list">
                            {adjustments.map((adjRecord) => {
                                const isSellAdjustment = adjRecord.brokerQuantity < adjRecord.ledgerQuantityBefore;
                                return (
                                    <li
                                        key={adjRecord.id}
                                        className={adjRecord.status === "REVOKED" ? "broker-holding-import-revoked" : undefined}
                                    >
                                        <div>
                                            <div className="broker-snapshot-item-identity">
                                                <strong className="broker-snapshot-item-name">{adjRecord.displayName}</strong>
                                                <div className="broker-snapshot-item-symbol">
                                                    <span className="market-badge">{adjRecord.market}</span>
                                                    <span className="broker-ticker">{adjRecord.ticker}</span>
                                                    <span className={`status-pill ${isSellAdjustment ? "pill-sell" : "pill-buy"}`}>
                                                        {isSellAdjustment ? "매도 조정" : "매수 조정"}
                                                    </span>
                                                </div>
                                            </div>
                                            <span>
                                                조정 수량: <strong>{isSellAdjustment ? `-${formatQuantity(adjRecord.deltaQuantity)}` : `+${formatQuantity(adjRecord.deltaQuantity)}`}</strong>
                                                {" · "}
                                                반영 전 원장 {formatQuantity(adjRecord.ledgerQuantityBefore)} → 증권사 {formatQuantity(adjRecord.brokerQuantity)}
                                                {" · "}
                                                기준 단가 {formatPrice(adjRecord.unitPrice)}
                                            </span>
                                            <span>
                                                스냅샷 저장 {adjRecord.snapshotSyncedAt ? formatDateTime(adjRecord.snapshotSyncedAt) : "미기록"}
                                                {" · "}
                                                {adjRecord.status === "ACTIVE"
                                                    ? `승인 ${formatDateTime(adjRecord.approvedAt)}`
                                                    : `취소됨 · 승인 ${formatDateTime(adjRecord.approvedAt)}`}
                                            </span>
                                        </div>
                                        {adjRecord.status === "ACTIVE" ? (
                                            pendingRevokeAdjustmentId === adjRecord.id ? (
                                                <div className="broker-holding-confirm-panel broker-holding-revoke-confirm">
                                                    <p>이 수량 차이 조정 반영을 취소하시겠습니까?</p>
                                                    <p className="broker-holding-confirm-note">
                                                        매매 원장에서 해당 잔고 조정 기록이 삭제되며 포트폴리오 잔고가 재계산됩니다. 실제 증권사 주문이나 잔고에는 영향을 주지 않습니다.
                                                    </p>
                                                    <div className="form-actions">
                                                        <button
                                                            type="button"
                                                            className="primary-button danger-button"
                                                            onClick={() => void confirmRevokeAdjustment(adjRecord.id)}
                                                            disabled={revokingAdjustmentId !== null}
                                                        >
                                                            {revokingAdjustmentId === adjRecord.id ? "취소 처리 중..." : "취소 확인"}
                                                        </button>
                                                        <button
                                                            type="button"
                                                            className="secondary-button"
                                                            onClick={() => setPendingRevokeAdjustmentId(null)}
                                                            disabled={revokingAdjustmentId !== null}
                                                        >
                                                            닫기
                                                        </button>
                                                    </div>
                                                </div>
                                            ) : (
                                                <button
                                                    type="button"
                                                    className="quiet-action"
                                                    onClick={() => openRevokeAdjustmentConfirm(adjRecord.id)}
                                                    disabled={isAnyActionPending}
                                                >
                                                    반영 취소
                                                </button>
                                            )
                                        ) : (
                                            <span className="broker-holding-import-status">취소됨</span>
                                        )}
                                    </li>
                                );
                            })}
                        </ul>
                        {hasNextAdjustments ? (
                            <div className="broker-history-load-more">
                                <button
                                    type="button"
                                    className="quiet-action load-more-button"
                                    onClick={() => void loadAdjustments(adjustmentPage + 1, true)}
                                    disabled={isLoadingMoreAdjustments}
                                >
                                    {isLoadingMoreAdjustments ? "불러오는 중..." : "더 보기"}
                                </button>
                            </div>
                        ) : null}
                    </>
                ) : null}
            </div>
        </section>
    );
}
