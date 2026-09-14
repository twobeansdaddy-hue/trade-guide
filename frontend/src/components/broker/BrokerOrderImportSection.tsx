import {useCallback, useEffect, useState, type FormEvent} from "react";
import {
    approveBrokerOrderImportRun,
    createBrokerOrderImportPreview,
    getBrokerOrderImportItemOverrides,
    getBrokerOrderImportRunDetail,
    getBrokerOrderImportRunItems,
    getBrokerOrderImportRuns,
    overrideBrokerOrderImportItem,
    revokeBrokerOrderImportApproval,
} from "../../api/brokerOrderImportApi";
import {getBrokerHoldingOpeningBalanceImports} from "../../api/portfolioBrokerApi";
import {getApiErrorCode, getApiRetryAfterSeconds, hasApiStatus} from "../../api/apiError";
import RequestError from "../common/RequestError";
import {formatDateTime} from "../../utils/format";
import type {
    BrokerOrderApprovalResponse,
    BrokerOrderImportApprovalBlocker,
    BrokerOrderImportItem,
    BrokerOrderImportItemOverrideResponse,
    BrokerOrderImportRun,
    BrokerOrderImportRunDetail,
    BrokerOrderOverrideDecision,
    BrokerOrderReconciliationStatus,
    BrokerOrderSkipReason,
    BrokerOrderStagingStatus,
} from "../../types/brokerOrderImport";
import type {
    BrokerHistoryPage,
    PortfolioBrokerHoldingImport,
    PortfolioBrokerLink,
} from "../../types/portfolioBroker";

type BrokerOrderImportSectionProps = {
    memberId: number;
    portfolioId: number;
    linkedAccount?: PortfolioBrokerLink;
    onGoToStep?: (step: number) => void;
};

const reconciliationLabel: Record<BrokerOrderReconciliationStatus, string> = {
    MATCHED: "수량 일치",
    MISMATCHED: "수량 차이",
    NOT_AVAILABLE: "스냅샷 없음",
    REPLAY_FAILED: "재생 불가",
};

const stagingStatusLabel: Record<BrokerOrderStagingStatus, string> = {
    STAGED: "반영 후보",
    SKIPPED_NOT_FILLED: "미체결 제외",
    PENDING_SETTLEMENT: "부분 체결 보류",
    SKIPPED_CONTROL_RECORD: "제어 기록 제외",
    SKIPPED_UNSUPPORTED: "미지원 제외",
    ALREADY_IMPORTED: "이미 반영됨",
    MANUAL_OVERLAP_SUSPECTED: "수동 기록 중복 의심",
    DUPLICATE_SUSPECTED: "중복 식별자 의심",
};

const skipReasonLabel: Record<BrokerOrderSkipReason, string> = {
    NOT_FILLED: "체결 수량 0주",
    PARTIAL_FILL_PENDING: "부분 체결 보류 (당일 주문 미완료)",
    CONTROL_RECORD: "취소·정정 거부 제어 기록",
    MISSING_EXECUTION_TIME: "최종 체결 시각 누락",
    MISSING_AVERAGE_PRICE: "평균 체결가 누락",
    UNKNOWN_STATUS: "해석 불가능한 주문 상태",
    ALREADY_IMPORTED: "기존 매매 원장에 이미 반영됨",
    MANUAL_OVERLAP: "수동 입력 거래와 일치",
    DUPLICATE_FINGERPRINT: "주문 내용 지문 중복",
};

const approvalBlockerDescriptions: Record<
    BrokerOrderImportApprovalBlocker,
    (baselineAt: string | null, baselineExcludedCount: number, coveredOrderedTo?: string | null) => string
> = {
    RUN_NOT_STAGED: () => "주문 이력 조회가 아직 완료되지 않았거나 실패하여 원장에 반영할 수 없습니다.",
    RECONCILIATION_MISMATCHED: () =>
        "보유 종목 대조 결과 수량 차이가 발견되어 안전을 위해 원장 반영이 차단되었습니다. 4단계에서 최신 보유 종목을 갱신하거나 아래 대조 표의 수량 차이를 먼저 확인해 주세요.",
    RECONCILIATION_REPLAY_FAILED: () =>
        "과거 매매 순서 재구성 검증에 실패하여(조회 기간 이전 주문 누락으로 인한 초과 매도 감지) 승인이 안전하게 차단되었습니다. 조회 기간을 더 넓혀 누락된 이전 주문을 포함해 보세요.",
    NO_STAGED_ITEMS: () =>
        "조회 기간 내에 매매 원장에 반영할 수 있는 정상 체결 주문이 없습니다 (미체결 주문 또는 제어 기록 제외).",
    ALL_BEFORE_BASELINE: (baselineAt, baselineExcludedCount) =>
        baselineAt
            ? `반영 후보 주문이 모두 활성 개시 잔고 기준 시점(${formatDateTime(baselineAt)}) 이전에 체결되어 원장 중복 방지를 위해 제외되었습니다 (총 ${baselineExcludedCount}건 제외). 더 최근 주문 기간을 조회해 주세요.`
            : "모든 반영 후보 주문이 개시 잔고 기준 시점 이전에 체결되어 원장에 반영할 수 없습니다.",
    INCOMPLETE_COVERAGE_NOT_ACKNOWLEDGED: (_baselineAt, _excluded, coveredOrderedTo) =>
        `요청 구간 중 일부(${coveredOrderedTo ? `${coveredOrderedTo}까지` : "일부"}만 수집되었으며, 보유 수량 대조가 완료되지 않아 누락 가능성이 있습니다. 불완전한 상태로 원장에 반영하려면 아래 '불완전 이력 확인 및 승인 동의'에 체크해 주세요.`,
};

type ActionableGuidance = {
    title: string;
    message: string;
    suggestions: string[];
    actionType?:
        | "COOLDOWN"
        | "PRESET"
        | "GOTO_STEP"
        | "RETRY"
        | "ACKNOWLEDGE"
        | "EXPAND_RANGE_PAST"
        | "ADJUST_RANGE_RECENT";
    presetDays?: number;
    targetStep?: number;
    targetStepLabel?: string;
};

function ActionableGuidanceCard({
    guidance,
    cooldownSeconds,
    onDismiss,
    onApplyPreset,
    onGoToStep,
    onRetry,
    onFocusAcknowledge,
}: {
    guidance: ActionableGuidance;
    cooldownSeconds: number | null;
    onDismiss?: () => void;
    onApplyPreset?: (days: number) => void;
    onGoToStep?: (step: number) => void;
    onRetry?: () => void;
    onFocusAcknowledge?: () => void;
}) {
    return (
        <div className="actionable-guidance-card" role="alert">
            <div className="guidance-card-header">
                <span className="guidance-warning-icon" aria-hidden="true">⚠️</span>
                <strong>{guidance.title}</strong>
            </div>
            <p className="guidance-card-message">{guidance.message}</p>
            {guidance.suggestions && guidance.suggestions.length > 0 ? (
                <ul className="guidance-suggestions-list">
                    {guidance.suggestions.map((sug, idx) => (
                        <li key={idx}>{sug}</li>
                    ))}
                </ul>
            ) : null}
            <div className="guidance-card-actions">
                {guidance.actionType === "COOLDOWN" && onRetry ? (
                    <button
                        type="button"
                        className="primary-button actionable-quick-btn"
                        onClick={onRetry}
                        disabled={cooldownSeconds !== null && cooldownSeconds > 0}
                    >
                        {cooldownSeconds !== null && cooldownSeconds > 0
                            ? `${cooldownSeconds}초 후 재시도 가능`
                            : "지금 다시 시도"}
                    </button>
                ) : null}
                {guidance.actionType === "PRESET" && onApplyPreset ? (
                    <>
                        <button
                            type="button"
                            className="secondary-button actionable-quick-btn"
                            onClick={() => onApplyPreset(7)}
                        >
                            최근 7일로 변경 후 재조회
                        </button>
                        <button
                            type="button"
                            className="secondary-button actionable-quick-btn"
                            onClick={() => onApplyPreset(14)}
                        >
                            최근 14일로 변경 후 재조회
                        </button>
                    </>
                ) : null}
                {guidance.actionType === "ADJUST_RANGE_RECENT" && onApplyPreset ? (
                    <button
                        type="button"
                        className="secondary-button actionable-quick-btn"
                        onClick={() => onApplyPreset(7)}
                    >
                        최근 7일로 기간 변경
                    </button>
                ) : null}
                {guidance.actionType === "EXPAND_RANGE_PAST" && onApplyPreset ? (
                    <button
                        type="button"
                        className="secondary-button actionable-quick-btn"
                        onClick={() => onApplyPreset(90)}
                    >
                        조회 기간 90일로 확대
                    </button>
                ) : null}
                {guidance.actionType === "GOTO_STEP" && guidance.targetStep && onGoToStep ? (
                    <button
                        type="button"
                        className="primary-button actionable-quick-btn"
                        onClick={() => onGoToStep(guidance.targetStep!)}
                    >
                        {guidance.targetStepLabel || (guidance.targetStep === 4 ? "4단계: 보유 종목 비교로 이동" : `${guidance.targetStep}단계로 이동`)}
                    </button>
                ) : null}
                {guidance.actionType === "ACKNOWLEDGE" && onFocusAcknowledge ? (
                    <button
                        type="button"
                        className="primary-button actionable-quick-btn"
                        onClick={onFocusAcknowledge}
                    >
                        불완전 이력 확인란으로 이동
                    </button>
                ) : null}
                {guidance.actionType === "RETRY" && onRetry ? (
                    <button
                        type="button"
                        className="secondary-button actionable-quick-btn"
                        onClick={onRetry}
                    >
                        다시 시도
                    </button>
                ) : null}
                {onDismiss ? (
                    <button
                        type="button"
                        className="quiet-action guidance-dismiss-btn"
                        onClick={onDismiss}
                    >
                        닫기
                    </button>
                ) : null}
            </div>
        </div>
    );
}

function formatQuantity(quantity: number | null | undefined): string {
    if (quantity === null || quantity === undefined) return "없음";
    return `${Number(quantity).toLocaleString("en-US")}주`;
}

function formatPrice(price: number | null | undefined, currency = "USD"): string {
    if (price === null || price === undefined) return "미기록";
    return `$${Number(price).toLocaleString("en-US", {minimumFractionDigits: 2, maximumFractionDigits: 4})} ${currency}`;
}

function toIsoDateString(date: Date): string {
    const y = date.getFullYear();
    const m = String(date.getMonth() + 1).padStart(2, "0");
    const d = String(date.getDate()).padStart(2, "0");
    return `${y}-${m}-${d}`;
}

function formatExternalOrderId(id: string): string {
    if (!id) return "";
    const cleanId = id.startsWith("#") ? id.slice(1) : id;
    if (cleanId.length <= 16) return `#${cleanId}`;
    return `#${cleanId.slice(0, 6)}...${cleanId.slice(-6)}`;
}

const DEFAULT_PAGE_SIZE = 20;

export default function BrokerOrderImportSection({
    memberId,
    portfolioId,
    linkedAccount,
    onGoToStep,
}: BrokerOrderImportSectionProps) {
    const today = new Date();
    const sevenDaysAgo = new Date();
    sevenDaysAgo.setDate(today.getDate() - 7);

    const [orderedFrom, setOrderedFrom] = useState(toIsoDateString(sevenDaysAgo));
    const [orderedTo, setOrderedTo] = useState(toIsoDateString(today));

    const [runs, setRuns] = useState<BrokerOrderImportRun[] | null>(null);
    const [runsPage, setRunsPage] = useState(0);
    const [hasNextRuns, setHasNextRuns] = useState(false);
    const [totalRuns, setTotalRuns] = useState(0);
    const [isRunsLoading, setIsRunsLoading] = useState(true);
    const [isLoadingMoreRuns, setIsLoadingMoreRuns] = useState(false);
    const [runsError, setRunsError] = useState<string | null>(null);

    const [selectedRunId, setSelectedRunId] = useState<number | null>(null);
    const [runDetail, setRunDetail] = useState<BrokerOrderImportRunDetail | null>(null);
    const [isDetailLoading, setIsDetailLoading] = useState(false);
    const [detailError, setDetailError] = useState<string | null>(null);

    const [formError, setFormError] = useState<string | null>(null);
    const [formSuccess, setFormSuccess] = useState<string | null>(null);
    const [formGuidance, setFormGuidance] = useState<ActionableGuidance | null>(null);
    const [isSubmitting, setIsSubmitting] = useState(false);
    const [cooldownSeconds, setCooldownSeconds] = useState<number | null>(null);

    const [openingBalanceImports, setOpeningBalanceImports] = useState<PortfolioBrokerHoldingImport[] | null>(null);

    const [approvalsByRunId, setApprovalsByRunId] = useState<Record<number, BrokerOrderApprovalResponse>>({});
    const [isConfirmingApproval, setIsConfirmingApproval] = useState(false);
    const [isApproving, setIsApproving] = useState(false);
    const [isConfirmingRevoke, setIsConfirmingRevoke] = useState(false);
    const [isRevoking, setIsRevoking] = useState(false);
    const [acknowledgeIncomplete, setAcknowledgeIncomplete] = useState(false);
    const [actionError, setActionError] = useState<string | null>(null);
    const [actionSuccess, setActionSuccess] = useState<string | null>(null);
    const [approvalGuidance, setApprovalGuidance] = useState<ActionableGuidance | null>(null);
    const [blockedNotice, setBlockedNotice] = useState<{title: string; message: string} | null>(null);

    const [itemsPageData, setItemsPageData] = useState<BrokerHistoryPage<BrokerOrderImportItem> | null>(null);
    const [itemsPage, setItemsPage] = useState(0);
    const [isItemsLoading, setIsItemsLoading] = useState(false);
    const [itemsError, setItemsError] = useState<string | null>(null);
    const [statusFilter, setStatusFilter] = useState<string>("");
    const [symbolFilter, setSymbolFilter] = useState<string>("");
    const [symbolInput, setSymbolInput] = useState<string>("");

    const [itemOverrides, setItemOverrides] = useState<Record<number, BrokerOrderImportItemOverrideResponse>>({});
    const [, setIsOverridesLoading] = useState(false);
    const [overrideDrafts, setOverrideDrafts] = useState<Record<number, { decision: BrokerOrderOverrideDecision | ""; reason: string }>>({});
    const [overrideSubmitting, setOverrideSubmitting] = useState<Record<number, boolean>>({});
    const [overrideErrors, setOverrideErrors] = useState<Record<number, string | null>>({});
    const [overrideSuccesses, setOverrideSuccesses] = useState<Record<number, string | null>>({});

    useEffect(() => {
        if (cooldownSeconds === null || cooldownSeconds <= 0) return;
        const timer = setInterval(() => {
            setCooldownSeconds((prev) => {
                if (prev === null || prev <= 1) {
                    clearInterval(timer);
                    return null;
                }
                return prev - 1;
            });
        }, 1000);
        return () => clearInterval(timer);
    }, [cooldownSeconds]);

    const loadOpeningBalances = useCallback(async () => {
        try {
            const pageData = await getBrokerHoldingOpeningBalanceImports(memberId, portfolioId);
            setOpeningBalanceImports(pageData.items);
        } catch {
            setOpeningBalanceImports(null);
        }
    }, [memberId, portfolioId]);

    useEffect(() => {
        let isCurrentRequest = true;

        void getBrokerHoldingOpeningBalanceImports(memberId, portfolioId)
            .then((pageData) => {
                if (!isCurrentRequest) return;
                setOpeningBalanceImports(pageData.items);
            })
            .catch(() => {
                if (!isCurrentRequest) return;
                setOpeningBalanceImports(null);
            });

        return () => {
            isCurrentRequest = false;
        };
    }, [memberId, portfolioId]);

    const handleSelectRun = (runId: number) => {
        if (runId === selectedRunId) return;
        setSelectedRunId(runId);
        setItemsPage(0);
        setIsDetailLoading(true);
        setDetailError(null);
        setIsConfirmingApproval(false);
        setIsConfirmingRevoke(false);
        setAcknowledgeIncomplete(false);
        setActionError(null);
        setActionSuccess(null);
        setBlockedNotice(null);
        setApprovalGuidance(null);
        setOverrideDrafts({});
        setOverrideErrors({});
        setOverrideSuccesses({});
    };

    // Load runs history with pagination
    const loadRuns = useCallback(async (page = 0, append = false) => {
        if (append) {
            setIsLoadingMoreRuns(true);
        } else {
            setIsRunsLoading(true);
        }
        setRunsError(null);
        try {
            const data = await getBrokerOrderImportRuns(memberId, portfolioId, page, 20);
            setRuns((prev) => (append && prev ? [...prev, ...data.items] : data.items));
            setRunsPage(data.page);
            setHasNextRuns(data.hasNext);
            setTotalRuns(data.totalElements);
            if (!append && data.items.length > 0 && selectedRunId === null) {
                setSelectedRunId(data.items[0].id);
                setIsDetailLoading(true);
            }
        } catch (reason) {
            if (!append) setRuns(null);
            setRunsError(
                reason instanceof Error
                    ? reason.message
                    : "주문 이력 가져오기 목록을 불러오지 못했습니다.",
            );
        } finally {
            setIsRunsLoading(false);
            setIsLoadingMoreRuns(false);
        }
    }, [memberId, portfolioId, selectedRunId]);

    useEffect(() => {
        let isCurrent = true;
        void getBrokerOrderImportRuns(memberId, portfolioId, 0, 20)
            .then((pageData) => {
                if (!isCurrent) return;
                setRuns(pageData.items);
                setRunsPage(pageData.page);
                setHasNextRuns(pageData.hasNext);
                setTotalRuns(pageData.totalElements);
                setRunsError(null);
                if (pageData.items.length > 0) {
                    setSelectedRunId(pageData.items[0].id);
                }
            })
            .catch((reason: unknown) => {
                if (!isCurrent) return;
                setRuns([]);
                setRunsError(
                    reason instanceof Error
                        ? reason.message
                        : "주문 이력 미리보기 목록을 불러오지 못했습니다.",
                );
            })
            .finally(() => {
                if (isCurrent) {
                    setIsRunsLoading(false);
                }
            });

        return () => {
            isCurrent = false;
        };
    }, [memberId, portfolioId]);

    // Load run detail when selectedRunId changes
    const loadDetail = useCallback(async (runId: number) => {
        setIsDetailLoading(true);
        setDetailError(null);
        try {
            const detail = await getBrokerOrderImportRunDetail(memberId, portfolioId, runId);
            setRunDetail(detail);
        } catch (reason) {
            setRunDetail(null);
            setDetailError(
                reason instanceof Error
                    ? reason.message
                    : "주문 이력 상세를 불러오지 못했습니다.",
            );
        } finally {
            setIsDetailLoading(false);
        }
    }, [memberId, portfolioId]);

    useEffect(() => {
        if (selectedRunId === null) {
            return;
        }

        let isCurrentRequest = true;

        void getBrokerOrderImportRunDetail(memberId, portfolioId, selectedRunId)
            .then((detail) => {
                if (!isCurrentRequest) return;
                setRunDetail(detail);
                setDetailError(null);
            })
            .catch((reason: unknown) => {
                if (!isCurrentRequest) return;
                setRunDetail(null);
                setDetailError(
                    reason instanceof Error
                        ? reason.message
                        : "주문 이력 상세를 불러오지 못했습니다.",
                );
            })
            .finally(() => {
                if (isCurrentRequest) {
                    setIsDetailLoading(false);
                }
            });

        return () => {
            isCurrentRequest = false;
        };
    }, [memberId, portfolioId, selectedRunId]);

    const loadItems = useCallback(
        async (
            runId: number,
            page = 0,
            status?: string,
            symbol?: string,
        ) => {
            setIsItemsLoading(true);
            setItemsError(null);
            try {
                const data = await getBrokerOrderImportRunItems(
                    memberId,
                    portfolioId,
                    runId,
                    page,
                    DEFAULT_PAGE_SIZE,
                    status,
                    symbol,
                );
                setItemsPageData(data);
                setItemsPage(data.page);
            } catch (reason) {
                setItemsPageData(null);
                setItemsError(
                    reason instanceof Error
                        ? reason.message
                        : "주문 상세 항목을 불러오지 못했습니다.",
                );
            } finally {
                setIsItemsLoading(false);
            }
        },
        [memberId, portfolioId],
    );

    useEffect(() => {
        if (selectedRunId === null) {
            return;
        }

        let isCurrent = true;

        void getBrokerOrderImportRunItems(
            memberId,
            portfolioId,
            selectedRunId,
            itemsPage,
            DEFAULT_PAGE_SIZE,
            statusFilter || undefined,
            symbolFilter || undefined,
        )
            .then((data) => {
                if (!isCurrent) return;
                setItemsPageData(data);
                setItemsError(null);
            })
            .catch((reason: unknown) => {
                if (!isCurrent) return;
                setItemsPageData(null);
                setItemsError(
                    reason instanceof Error
                        ? reason.message
                        : "주문 상세 항목을 불러오지 못했습니다.",
                );
            })
            .finally(() => {
                if (isCurrent) {
                    setIsItemsLoading(false);
                }
            });

        return () => {
            isCurrent = false;
        };
    }, [memberId, portfolioId, selectedRunId, itemsPage, statusFilter, symbolFilter]);

    const loadOverrides = useCallback(
        async (runId: number) => {
            setIsOverridesLoading(true);
            try {
                const pageData = await getBrokerOrderImportItemOverrides(
                    memberId,
                    portfolioId,
                    runId,
                    0,
                    100,
                );
                const map: Record<number, BrokerOrderImportItemOverrideResponse> = {};
                for (const item of pageData.items) {
                    map[item.itemId] = item;
                }
                let currentPage = 0;
                let hasNext = pageData.hasNext;
                while (hasNext && currentPage < 10) {
                    currentPage += 1;
                    const nextPageData = await getBrokerOrderImportItemOverrides(
                        memberId,
                        portfolioId,
                        runId,
                        currentPage,
                        100,
                    );
                    for (const item of nextPageData.items) {
                        map[item.itemId] = item;
                    }
                    hasNext = nextPageData.hasNext;
                }
                setItemOverrides(map);
            } catch {
                // Keep existing overrides or empty
            } finally {
                setIsOverridesLoading(false);
            }
        },
        [memberId, portfolioId],
    );

    useEffect(() => {
        if (selectedRunId === null) {
            return;
        }

        let isCurrent = true;
        void getBrokerOrderImportItemOverrides(memberId, portfolioId, selectedRunId, 0, 100)
            .then((pageData) => {
                if (!isCurrent) return;
                const map: Record<number, BrokerOrderImportItemOverrideResponse> = {};
                for (const item of pageData.items) {
                    map[item.itemId] = item;
                }
                setItemOverrides(map);
            })
            .catch(() => {
                if (!isCurrent) return;
                setItemOverrides({});
            });

        return () => {
            isCurrent = false;
        };
    }, [memberId, portfolioId, selectedRunId]);

    const handleOverrideSubmit = async (item: BrokerOrderImportItem) => {
        if (selectedRunId === null || overrideSubmitting[item.id]) return;

        const draft = overrideDrafts[item.id];
        const decision = draft?.decision;
        const reason = (draft?.reason ?? "").trim();

        if (!decision) {
            setOverrideErrors((prev) => ({
                ...prev,
                [item.id]: "재판정 결정('반영 허용' 또는 '제외 유지')을 선택해 주세요.",
            }));
            return;
        }

        if (!reason) {
            setOverrideErrors((prev) => ({
                ...prev,
                [item.id]: "재판정 사유는 필수입니다. 공백만으로는 제출할 수 없습니다.",
            }));
            return;
        }

        if (reason.length > 500) {
            setOverrideErrors((prev) => ({
                ...prev,
                [item.id]: "재판정 사유는 500자 이하여야 합니다.",
            }));
            return;
        }

        setOverrideSubmitting((prev) => ({ ...prev, [item.id]: true }));
        setOverrideErrors((prev) => ({ ...prev, [item.id]: null }));
        setOverrideSuccesses((prev) => ({ ...prev, [item.id]: null }));

        try {
            const result = await overrideBrokerOrderImportItem(
                memberId,
                portfolioId,
                selectedRunId,
                item.id,
                { decision, reason },
            );

            setItemOverrides((prev) => ({ ...prev, [item.id]: result }));
            setOverrideDrafts((prev) => {
                const next = { ...prev };
                delete next[item.id];
                return next;
            });
            setOverrideSuccesses((prev) => ({
                ...prev,
                [item.id]:
                    result.decision === "ALLOW_LEDGER_WRITE"
                        ? "반영 허용으로 재판정되었습니다. 최종 원장 반영은 상단의 '원장 반영 승인' 버튼을 통해 완료됩니다."
                        : "제외 유지로 재판정되었습니다. 원장에 반영되지 않습니다.",
            }));

            await Promise.all([
                loadDetail(selectedRunId),
                loadItems(selectedRunId, itemsPage, statusFilter || undefined, symbolFilter || undefined),
                loadOverrides(selectedRunId),
            ]);
        } catch (reasonErr) {
            const errCode = getApiErrorCode(reasonErr);
            let msg = reasonErr instanceof Error ? reasonErr.message : "주문 항목 재판정에 실패했습니다.";

            if (hasApiStatus(reasonErr, 409)) {
                if (errCode === "ORDER_IMPORT_OVERRIDE_CONFLICT") {
                    msg = "이 주문 항목은 이미 다른 결정으로 재판정되었습니다. 결정을 바꾸려면 주문 이력을 새로 조회하세요.";
                } else if (errCode === "ORDER_IMPORT_ITEM_NOT_OVERRIDABLE") {
                    msg = "사람 판단이 필요한 의심 항목(수동 기록 중복 의심 또는 중복 식별자 의심)만 재판정할 수 있습니다.";
                }
            } else if (hasApiStatus(reasonErr, 404)) {
                msg = "해당 주문 항목을 찾을 수 없습니다.";
            } else if (hasApiStatus(reasonErr, 400)) {
                msg = reasonErr instanceof Error ? reasonErr.message : "입력한 재판정 정보가 올바르지 않습니다.";
            }

            setOverrideErrors((prev) => ({ ...prev, [item.id]: msg }));
        } finally {
            setOverrideSubmitting((prev) => ({ ...prev, [item.id]: false }));
        }
    };

    const handleStatusFilterChange = (newStatus: string) => {
        if (newStatus === statusFilter) return;
        setIsItemsLoading(true);
        setItemsError(null);
        setStatusFilter(newStatus);
        setItemsPage(0);
    };

    const handleSymbolSearch = (e: FormEvent) => {
        e.preventDefault();
        const trimmed = symbolInput.trim().toUpperCase();
        if (trimmed === symbolFilter) return;
        setIsItemsLoading(true);
        setItemsError(null);
        setSymbolFilter(trimmed);
        setItemsPage(0);
    };

    const handleResetFilters = () => {
        setIsItemsLoading(true);
        setItemsError(null);
        setStatusFilter("");
        setSymbolFilter("");
        setSymbolInput("");
        setItemsPage(0);
    };

    const handlePageChange = (newPage: number) => {
        if (selectedRunId === null || newPage === itemsPage || newPage < 0) return;
        if (itemsPageData && !itemsPageData.hasNext && newPage > itemsPage) return;
        setIsItemsLoading(true);
        setItemsError(null);
        setItemsPage(newPage);
    };


    const setPresetRange = (days: number) => {
        const end = new Date();
        const start = new Date();
        start.setDate(end.getDate() - days);
        setOrderedFrom(toIsoDateString(start));
        setOrderedTo(toIsoDateString(end));
        setFormError(null);
        setFormGuidance(null);
    };

    const handleContinueRemainingRange = () => {
        if (!runDetail?.run.coverage?.nextOrderedFrom) return;
        const nextFrom = runDetail.run.coverage.nextOrderedFrom;
        const reqTo = runDetail.run.requestedOrderedTo;
        setOrderedFrom(nextFrom);
        setOrderedTo(reqTo);
        setFormError(null);
        setFormGuidance(null);
        setFormSuccess(
            `잔여 미수집 구간(${nextFrom} ~ ${reqTo})이 조회 폼에 설정되었습니다. 상단의 '주문 이력 미리보기 조회' 버튼을 눌러 조회를 계속하세요.`,
        );
        const formEl = document.querySelector(".broker-order-import-form");
        if (formEl) {
            formEl.scrollIntoView({behavior: "smooth", block: "center"});
        }
    };

    const handleFocusAcknowledge = () => {
        const el = document.getElementById("acknowledge-incomplete-coverage-checkbox");
        if (el) {
            el.scrollIntoView({behavior: "smooth", block: "center"});
            el.focus();
        }
    };

    const submitCurrentForm = () => {
        setFormGuidance(null);
        const formEl = document.querySelector(".broker-order-import-form") as HTMLFormElement | null;
        if (formEl) {
            formEl.requestSubmit();
        }
    };

    const handleCreatePreview = async (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        setFormError(null);
        setFormSuccess(null);
        setFormGuidance(null);

        if (!orderedFrom || !orderedTo) {
            setFormError("주문 조회 시작일과 종료일을 모두 입력해 주세요.");
            return;
        }

        if (orderedFrom > orderedTo) {
            setFormError("주문 조회 시작일은 종료일보다 늦을 수 없습니다.");
            return;
        }

        setIsSubmitting(true);
        try {
            const createdDetail = await createBrokerOrderImportPreview(memberId, portfolioId, {
                orderedFrom,
                orderedTo,
            });
            setRunDetail(createdDetail);
            setSelectedRunId(createdDetail.run.id);
            setItemsPage(0);
            setStatusFilter("");
            setSymbolFilter("");
            setSymbolInput("");
            setAcknowledgeIncomplete(false);
            setFormGuidance(null);
            setItemOverrides({});
            setOverrideDrafts({});
            setOverrideErrors({});
            setOverrideSuccesses({});
            setRuns((current) => [createdDetail.run, ...(current?.filter((r) => r.id !== createdDetail.run.id) ?? [])]);
            setTotalRuns((prev) => prev + 1);
            if (createdDetail.run.coverage && !createdDetail.run.coverage.fullyCovered) {
                setFormSuccess(
                    `${createdDetail.run.requestedOrderedFrom} ~ ${createdDetail.run.requestedOrderedTo} 중 ${createdDetail.run.coverage.coveredOrderedTo}까지 부분 수집되었습니다. (잔여 구간: ${createdDetail.run.coverage.nextOrderedFrom} ~ ${createdDetail.run.requestedOrderedTo})`,
                );
            } else {
                setFormSuccess(
                    `${createdDetail.run.requestedOrderedFrom} ~ ${createdDetail.run.requestedOrderedTo} 구간의 주문 이력 미리보기가 생성되었습니다. (매매 원장은 변경되지 않았습니다)`,
                );
            }
        } catch (reason) {
            const errCode = getApiErrorCode(reason);
            const is429 = hasApiStatus(reason, 429) || errCode === "BROKER_CALL_COOLDOWN";
            const is422 = hasApiStatus(reason, 422);
            const is409 = hasApiStatus(reason, 409);

            if (is429) {
                const sec = getApiRetryAfterSeconds(reason, 3);
                setCooldownSeconds(sec);
                setFormGuidance({
                    title: "연속 조회 대기 (쿨다운 제한)",
                    message: reason instanceof Error ? reason.message : "증권사 연속 호출 제한이 적용 중입니다.",
                    suggestions: [
                        `${sec}초 후 연속 호출 제한이 자동으로 해제됩니다.`,
                        "대기 시간이 끝난 후 아래 '지금 다시 시도' 버튼을 눌러주세요.",
                    ],
                    actionType: "COOLDOWN",
                });
            } else if (is422 || errCode === "PAGE_LIMIT_EXCEEDED" || errCode === "CURSOR_REPEATED") {
                setFormGuidance({
                    title: errCode === "CURSOR_REPEATED" ? "증권사 조회 위치 반복 오류" : "조회 건수 한도 초과 (기간 축소 권장)",
                    message: reason instanceof Error ? reason.message : "조회 구간의 주문 건수가 너무 많습니다.",
                    suggestions: [
                        "증권사 API는 한 번에 가져올 수 있는 최대 페이지 수가 제한되어 있습니다.",
                        "조회 기간을 7일 또는 14일 등 더 짧은 기간으로 나누어 조회해 보세요.",
                    ],
                    actionType: "PRESET",
                    presetDays: 7,
                });
            } else if (is409 && errCode === "BROKER_CALL_IN_PROGRESS") {
                setFormGuidance({
                    title: "조회 진행 중 충돌",
                    message: reason instanceof Error ? reason.message : "같은 포트폴리오의 주문 조회가 이미 진행 중입니다.",
                    suggestions: [
                        "서버에서 이전 조회가 완료될 때까지 잠시 기다려 주세요.",
                    ],
                    actionType: "RETRY",
                });
            } else if (is409 && errCode === "BROKER_CONNECTION_REVERIFICATION_REQUIRED") {
                setFormGuidance({
                    title: "증권사 연결 재검증 필요",
                    message: "증권사 연결 자격 증명이 만료되었거나 재검증이 필요합니다.",
                    suggestions: [
                        "2단계 '연결 확인'으로 이동하여 연결 상태를 다시 검증해 주세요.",
                    ],
                    actionType: "GOTO_STEP",
                    targetStep: 2,
                    targetStepLabel: "2단계: 연결 확인으로 이동",
                });
            } else {
                setFormError(
                    reason instanceof Error
                        ? reason.message
                        : "주문 이력 미리보기를 생성하지 못했습니다.",
                );
            }
        } finally {
            setIsSubmitting(false);
        }
    };

    const isWarningItem = (item: BrokerOrderImportItem) =>
        item.stagingStatus === "MANUAL_OVERLAP_SUSPECTED" ||
        item.stagingStatus === "DUPLICATE_SUSPECTED" ||
        item.amountMismatch;

    const counts = runDetail?.run.counts;
    const warningCount = counts
        ? counts.manualOverlapSuspectedCount + counts.duplicateSuspectedCount + counts.amountMismatchCount
        : 0;
    const excludedCount = counts
        ? counts.notFilledCount +
          counts.pendingSettlementCount +
          counts.controlRecordCount +
          counts.alreadyImportedCount +
          counts.unsupportedMarketCount +
          counts.unsupportedCurrencyCount
        : 0;

    const activeOpeningBalances = (openingBalanceImports ?? [])
        .filter((imp) => imp.status === "ACTIVE")
        .sort((a, b) => new Date(b.approvedAt).getTime() - new Date(a.approvedAt).getTime());
    const activeBaselineAt = activeOpeningBalances.length > 0 ? activeOpeningBalances[0].approvedAt : null;

    const currentApproval = selectedRunId !== null ? approvalsByRunId[selectedRunId] ?? null : null;
    const effectiveBaselineAt = currentApproval?.baselineAt ?? runDetail?.approval.baselineAt ?? activeBaselineAt;

    const stagedCount = runDetail?.approval.stagedCount ?? (currentApproval ? currentApproval.eligibleCount + currentApproval.baselineExcludedCount : 0);
    const eligibleCount = currentApproval ? currentApproval.eligibleCount : (runDetail?.approval.eligibleCount ?? 0);
    const baselineExcludedCount = currentApproval
        ? currentApproval.baselineExcludedCount
        : (runDetail?.approval.baselineExcludedCount ?? 0);
    const writableCount = currentApproval ? currentApproval.writtenCount : (runDetail?.approval.writableCount ?? 0);
    const alreadyLinkedCount = currentApproval ? currentApproval.alreadyLinkedCount : (runDetail?.approval.alreadyLinkedCount ?? 0);

    const overrideAllowedCount = currentApproval?.overrideAllowedCount ?? runDetail?.approval.overrideAllowedCount ?? 0;
    const overrideKeptExcludedCount = runDetail?.approval.overrideKeptExcludedCount ?? 0;
    const unresolvedSuspectedCount = runDetail?.approval.unresolvedSuspectedCount ??
        Math.max(0, (runDetail?.approval.suspectedCount ?? 0) - overrideAllowedCount - overrideKeptExcludedCount);

    const isReconciliationMatched = runDetail?.run.reconciliationStatus === "MATCHED";
    const isPartialCoverage = Boolean(runDetail?.run.coverage && !runDetail.run.coverage.fullyCovered);
    const coveredOrderedTo = runDetail?.run.coverage?.coveredOrderedTo;
    const nextOrderedFrom = runDetail?.run.coverage?.nextOrderedFrom;
    const requestedOrderedFrom = runDetail?.run.requestedOrderedFrom;
    const requestedOrderedTo = runDetail?.run.requestedOrderedTo;

    const isAcknowledgementBlocker =
        runDetail?.approval.blocker === "INCOMPLETE_COVERAGE_NOT_ACKNOWLEDGED" ||
        Boolean(runDetail?.approval.coverageAcknowledgementRequired && !runDetail?.approval.approvable);

    const canApprove = Boolean(
        runDetail &&
        !currentApproval &&
        (runDetail.approval.approvable || (isAcknowledgementBlocker && acknowledgeIncomplete))
    );

    const isAnyActionPending = isSubmitting || isApproving || isRevoking;

    const handleConfirmApprove = async () => {
        if (!runDetail || isApproving) return;

        setIsApproving(true);
        setActionError(null);
        setActionSuccess(null);
        setBlockedNotice(null);
        setApprovalGuidance(null);

        try {
            const result = await approveBrokerOrderImportRun(
                memberId,
                portfolioId,
                runDetail.run.id,
                acknowledgeIncomplete,
            );
            setApprovalsByRunId((prev) => ({ ...prev, [runDetail.run.id]: result }));
            setIsConfirmingApproval(false);
            setActionSuccess(
                result.writtenCount > 0
                    ? `주문 이력 ${result.writtenCount}건을 매매 원장에 안전하게 반영했습니다.${
                          result.coverageAcknowledged ? " (불완전 이력 확인 완료)" : ""
                      }`
                    : `이미 연계된 주문 ${result.alreadyLinkedCount}건이 확인되어 추가 작성 없이 완료되었습니다.`,
            );
            await Promise.all([
                loadDetail(runDetail.run.id),
                loadRuns(),
                loadOpeningBalances(),
                loadItems(runDetail.run.id, itemsPage, statusFilter || undefined, symbolFilter || undefined),
                loadOverrides(runDetail.run.id),
            ]);
        } catch (reason) {
            setIsConfirmingApproval(false);
            const errCode = getApiErrorCode(reason);
            const is409 = hasApiStatus(reason, 409);
            const is429 = hasApiStatus(reason, 429) || errCode === "BROKER_CALL_COOLDOWN";

            if (is409) {
                if (errCode === "ORDER_IMPORT_COVERAGE_ACKNOWLEDGEMENT_REQUIRED") {
                    setApprovalGuidance({
                        title: "불완전 이력 확인 필요 (원장 반영 차단)",
                        message:
                            reason instanceof Error
                                ? reason.message
                                : "요청 구간 중 일부만 수집된 불완전한 상태입니다. 불완전 이력 확인에 동의해야 합니다.",
                        suggestions: [
                            "아래 '불완전 이력 확인 및 승인 동의' 체크박스를 체크한 후 다시 승인을 진행해 주세요.",
                            "또는 상단 '남은 구간 계속 조회'를 눌러 잔여 구간을 먼저 수집할 수 있습니다.",
                        ],
                        actionType: "ACKNOWLEDGE",
                    });
                } else if (errCode === "RECONCILIATION_MISMATCH") {
                    setApprovalGuidance({
                        title: "보유 종목 대조 불일치 (원장 반영 차단)",
                        message:
                            reason instanceof Error
                                ? reason.message
                                : "보유 종목 대조 결과 수량 불일치로 안전을 위해 원장 반영이 차단되었습니다.",
                        suggestions: [
                            "4단계 '보유 종목 비교'에서 최신 증권사 스냅샷을 갱신해 보세요.",
                            "하단 '보유 종목 대조' 표에서 수량 차이가 발생한 종목을 점검하세요.",
                        ],
                        actionType: "GOTO_STEP",
                        targetStep: 4,
                        targetStepLabel: "4단계: 보유 종목 비교로 이동",
                    });
                } else if (errCode === "BASELINE_EXCLUDED") {
                    setApprovalGuidance({
                        title: "개시 잔고 이전 체결 제외 (반영 대상 없음)",
                        message:
                            reason instanceof Error
                                ? reason.message
                                : "모든 주문이 활성 개시 잔고 기준 시점 이전에 체결되어 원장 중복 방지를 위해 제외되었습니다.",
                        suggestions: [
                            `${
                                effectiveBaselineAt
                                    ? `활성 개시 잔고 시점(${formatDateTime(effectiveBaselineAt)})`
                                    : "활성 개시 잔고 시점"
                            } 이후의 최근 체결 주문 기간을 조회해 주세요.`,
                        ],
                        actionType: "ADJUST_RANGE_RECENT",
                    });
                } else if (errCode === "REPLAY_VALIDATION_FAILED") {
                    setApprovalGuidance({
                        title: "원장 재생 검증 실패 (초과 매도 감지)",
                        message:
                            reason instanceof Error
                                ? reason.message
                                : "과거 매매 순서 재구성 중 보유 수량 부족(초과 매도)이 발생했습니다.",
                        suggestions: [
                            "과거 누락된 매수 주문을 포함할 수 있도록 조회 시작일을 더 이전(예: 최근 90일 이상)으로 넓혀 다시 조회해 주세요.",
                        ],
                        actionType: "EXPAND_RANGE_PAST",
                    });
                } else {
                    setBlockedNotice({
                        title: "원장 반영 차단 (안전 조건 미충족)",
                        message:
                            reason instanceof Error
                                ? reason.message
                                : "개시 잔고 기준 이후 체결 주문 부재, 대조 불일치 또는 재생 검증 실패로 원장 반영이 안전하게 차단되었습니다.",
                    });
                }
            } else if (is429) {
                const sec = getApiRetryAfterSeconds(reason, 3);
                setCooldownSeconds(sec);
                setApprovalGuidance({
                    title: "연속 승인 대기 (쿨다운 제한)",
                    message: reason instanceof Error ? reason.message : "증권사 연속 호출 제한이 적용 중입니다.",
                    suggestions: [
                        `${sec}초 후 연속 호출 제한이 해제됩니다. 잠시 후 다시 시도해 주세요.`,
                    ],
                    actionType: "COOLDOWN",
                });
            } else {
                setActionError(
                    reason instanceof Error
                        ? reason.message
                        : "주문 이력 반영 승인에 실패했습니다.",
                );
            }
        } finally {
            setIsApproving(false);
        }
    };

    const handleConfirmRevoke = async () => {
        if (!runDetail || isRevoking) return;

        setIsRevoking(true);
        setActionError(null);
        setActionSuccess(null);

        try {
            await revokeBrokerOrderImportApproval(memberId, portfolioId, runDetail.run.id);
            setApprovalsByRunId((prev) => {
                const next = { ...prev };
                delete next[runDetail.run.id];
                return next;
            });
            setIsConfirmingRevoke(false);
            setActionSuccess("주문 이력 반영 승인을 취소했습니다. 매매 원장에서 거래 기록이 삭제되었습니다.");
            await Promise.all([
                loadDetail(runDetail.run.id),
                loadRuns(),
                loadOpeningBalances(),
                loadItems(runDetail.run.id, itemsPage, statusFilter || undefined, symbolFilter || undefined),
                loadOverrides(runDetail.run.id),
            ]);
        } catch (reason) {
            setActionError(
                reason instanceof Error
                    ? reason.message
                    : "주문 이력 반영 승인을 취소하지 못했습니다.",
            );
        } finally {
            setIsRevoking(false);
        }
    };

    return (
        <section id="broker-order-imports" className="content-section broker-order-import-section">
            <div className="section-heading">
                <div>
                    <p className="section-label">6. ORDER IMPORT PREVIEW</p>
                    <h2>증권사 주문 이력 미리보기 및 검토</h2>
                </div>
            </div>

            {/* Read-only safe banner */}
            <div className="broker-order-import-disclaimer" role="note">
                <div className="broker-order-import-disclaimer-header">
                    <span className="broker-order-import-disclaimer-badge">주문 이력 검토 및 승인 가이드</span>
                    {linkedAccount ? (
                        <span className="broker-order-import-account-chip">
                            연결 계좌: {linkedAccount.displayName} ({linkedAccount.maskedAccountNumber})
                        </span>
                    ) : null}
                </div>
                <p>
                    증권사 주문 이력을 조회하여 반영 후보 건수와 보유 종목 대조 상태를 미리 검토합니다.{" "}
                    <strong>기본 미리보기는 매매 원장(거래 기록)을 절대 변경하지 않습니다.</strong>
                </p>
                <p className="broker-order-import-disclaimer-sub">
                    원장 반영은 사용자가 명시적으로 승인한 경우에만 실행되며, Trade Guide 내부 매매 기록만 작성할 뿐 증권사로 실제 매매 주문을 절대 전송하지 않습니다.
                </p>
            </div>


            {/* Date range picker form */}
            <form className="broker-order-import-form" onSubmit={handleCreatePreview}>
                <div className="broker-order-import-form-header">
                    <h3>새 주문 이력 미리보기 생성</h3>
                    <div className="broker-order-import-presets" role="group" aria-label="조회 기간 프리셋">
                        <button
                            type="button"
                            className="quiet-action preset-button"
                            onClick={() => setPresetRange(7)}
                            disabled={isSubmitting}
                        >
                            최근 7일
                        </button>
                        <button
                            type="button"
                            className="quiet-action preset-button"
                            onClick={() => setPresetRange(14)}
                            disabled={isSubmitting}
                        >
                            최근 14일
                        </button>
                        <button
                            type="button"
                            className="quiet-action preset-button"
                            onClick={() => setPresetRange(30)}
                            disabled={isSubmitting}
                        >
                            최근 30일
                        </button>
                        <button
                            type="button"
                            className="quiet-action preset-button"
                            onClick={() => setPresetRange(90)}
                            disabled={isSubmitting}
                        >
                            최근 90일
                        </button>
                    </div>
                </div>

                <div className="form-grid broker-order-import-date-grid">
                    <label htmlFor="order-import-from">
                        <span>주문 조회 시작일</span>
                        <input
                            id="order-import-from"
                            type="date"
                            value={orderedFrom}
                            max={orderedTo || toIsoDateString(today)}
                            onChange={(e) => {
                                setOrderedFrom(e.target.value);
                                setFormError(null);
                            }}
                            required
                            disabled={isSubmitting}
                        />
                    </label>

                    <label htmlFor="order-import-to">
                        <span>주문 조회 종료일</span>
                        <input
                            id="order-import-to"
                            type="date"
                            value={orderedTo}
                            min={orderedFrom}
                            max={toIsoDateString(today)}
                            onChange={(e) => {
                                setOrderedTo(e.target.value);
                                setFormError(null);
                            }}
                            required
                            disabled={isSubmitting}
                        />
                    </label>
                </div>

                <p className="broker-order-import-hint">
                    증권사는 체결일이 아닌 <strong>주문일(KST)</strong> 기준으로 조회하므로, 미국 정규장 시차를 감안해 실제 조회 시 앞뒤 2일이 자동 확장되어 요청됩니다.
                </p>

                <div className="broker-order-import-form-feedback" aria-live="polite">
                    {formError ? <p className="form-error-message" role="alert">{formError}</p> : null}
                    {formSuccess ? <p className="form-success-message" role="status">{formSuccess}</p> : null}
                    {formGuidance ? (
                        <ActionableGuidanceCard
                            guidance={formGuidance}
                            cooldownSeconds={cooldownSeconds}
                            onDismiss={() => setFormGuidance(null)}
                            onApplyPreset={(days) => {
                                setPresetRange(days);
                                setFormGuidance(null);
                            }}
                            onGoToStep={onGoToStep}
                            onRetry={submitCurrentForm}
                        />
                    ) : null}
                </div>

                <div className="form-actions">
                    <button
                        type="submit"
                        className="primary-button"
                        disabled={isSubmitting || !orderedFrom || !orderedTo || (cooldownSeconds !== null && cooldownSeconds > 0)}
                    >
                        {isSubmitting
                            ? "주문 이력 조회 중..."
                            : cooldownSeconds !== null && cooldownSeconds > 0
                            ? `쿨다운 대기 (${cooldownSeconds}초)`
                            : "주문 이력 미리보기 조회"}
                    </button>
                </div>
            </form>

            {/* Prior Runs Navigation */}
            <div className="broker-order-import-history-block">
                <div className="broker-order-import-history-header">
                    <h3>이전 미리보기 실행 이력</h3>
                    {runs && runs.length > 0 ? (
                        <span className="broker-order-import-run-count">총 {totalRuns}건 중 {runs.length}건 표시</span>
                    ) : null}
                </div>

                {isRunsLoading ? (
                    <p className="status-message" aria-live="polite">이전 미리보기 이력을 불러오는 중입니다.</p>
                ) : null}

                {runsError ? (
                    <RequestError
                        message={runsError}
                        onRetry={() => void loadRuns(0, false)}
                        retryLabel="이력 목록 다시 시도"
                    />
                ) : null}

                {!isRunsLoading && !runsError && (!runs || runs.length === 0) ? (
                    <p className="empty-state">생성된 주문 이력 미리보기가 없습니다. 기간을 선택해 조회해 보세요.</p>
                ) : null}

                {!isRunsLoading && runs && runs.length > 0 ? (
                    <>
                        <div className="broker-order-import-runs-grid" role="tablist" aria-label="미리보기 실행 선택">
                            {runs.map((run) => {
                                const isSelected = run.id === selectedRunId;
                                const isMatched = run.reconciliationStatus === "MATCHED";
                                const isMismatched = run.reconciliationStatus === "MISMATCHED";
                                const isRunPartial = Boolean(run.coverage && !run.coverage.fullyCovered);

                                return (
                                    <button
                                        key={run.id}
                                        type="button"
                                        role="tab"
                                        aria-selected={isSelected}
                                        className={`broker-order-import-run-card ${isSelected ? "selected" : ""}`}
                                        onClick={() => handleSelectRun(run.id)}
                                    >
                                        <div className="broker-order-import-run-card-top">
                                            <span className="run-range">
                                                {isRunPartial && run.coverage?.coveredOrderedTo
                                                    ? `${run.requestedOrderedFrom} ~ ${run.coverage.coveredOrderedTo}`
                                                    : `${run.requestedOrderedFrom} ~ ${run.requestedOrderedTo}`}
                                            </span>
                                            {isRunPartial ? (
                                                <span className="status-badge run-status-partial">부분 수집</span>
                                            ) : (
                                                <span className={`status-badge run-status-${run.status.toLowerCase()}`}>
                                                    {run.status === "STAGED" ? "스테이징 완료" : run.status === "FAILED" ? "실패" : "진행 중"}
                                                </span>
                                            )}
                                        </div>
                                        <div className="broker-order-import-run-card-mid">
                                            <span>반영 후보 {run.counts.stagedCount}건</span>
                                            <span>/ 수집 {run.counts.fetchedCount}건</span>
                                            {isRunPartial ? (
                                                <span className="run-card-partial-note">
                                                    (요청: ~{run.requestedOrderedTo})
                                                </span>
                                            ) : null}
                                        </div>
                                        <div className="broker-order-import-run-card-bottom">
                                            <span
                                                className={`recon-chip ${
                                                    isMatched
                                                        ? "recon-matched"
                                                        : isMismatched
                                                        ? "recon-mismatched"
                                                        : "recon-other"
                                                }`}
                                            >
                                                {reconciliationLabel[run.reconciliationStatus]}
                                            </span>
                                            <time dateTime={run.startedAt}>{formatDateTime(run.startedAt)}</time>
                                        </div>
                                    </button>
                                );
                            })}
                        </div>
                        {hasNextRuns ? (
                            <div className="broker-history-load-more">
                                <button
                                    type="button"
                                    className="quiet-action load-more-button"
                                    onClick={() => void loadRuns(runsPage + 1, true)}
                                    disabled={isLoadingMoreRuns}
                                >
                                    {isLoadingMoreRuns ? "불러오는 중..." : "더 보기"}
                                </button>
                            </div>
                        ) : null}
                    </>
                ) : null}
            </div>

            {/* Selected Run Detail Inspection */}
            {isDetailLoading ? (
                <p className="status-message" aria-live="polite">선택한 미리보기 상세 정보를 불러오는 중입니다.</p>
            ) : null}

            {detailError ? (
                <RequestError
                    message={detailError}
                    onRetry={() => {
                        if (selectedRunId !== null) void loadDetail(selectedRunId);
                    }}
                    retryLabel="미리보기 상세 다시 시도"
                />
            ) : null}

            {!isDetailLoading && runDetail ? (
                <div className="broker-order-import-detail" aria-live="polite">
                    {/* Run Header Meta */}
                    <div className="broker-order-import-detail-header">
                        <div>
                            <h4>
                                주문 이력 상세 (실행 #{runDetail.run.id})
                            </h4>
                            <p className="broker-order-import-meta-text">
                                계좌: {runDetail.run.maskedAccountNumber} · 실행 시각: {formatDateTime(runDetail.run.startedAt)}
                            </p>
                            <p className="broker-order-import-meta-text">
                                요청 구간: <strong>{runDetail.run.requestedOrderedFrom} ~ {runDetail.run.requestedOrderedTo}</strong>
                                {" "}(시차 보정 실제 조회: {runDetail.run.queriedOrderedFrom} ~ {runDetail.run.queriedOrderedTo})
                            </p>
                            {isPartialCoverage && coveredOrderedTo ? (
                                <p className="broker-order-import-meta-text partial-coverage-meta">
                                    부분 수집 완료 구간: <strong>{requestedOrderedFrom} ~ {coveredOrderedTo}</strong> (잔여 미수집: {nextOrderedFrom ?? "미정"} ~ {requestedOrderedTo})
                                </p>
                            ) : null}
                        </div>
                        <div className="broker-order-import-header-badge-group">
                            {isPartialCoverage ? (
                                <span className="status-badge run-status-partial">부분 수집</span>
                            ) : null}
                            <span className={`status-badge run-status-${runDetail.run.status.toLowerCase()}`}>
                                {runDetail.run.status === "STAGED" ? "스테이징 완료" : runDetail.run.status}
                            </span>
                            <span
                                className={`recon-chip ${
                                    runDetail.run.reconciliationStatus === "MATCHED"
                                        ? "recon-matched"
                                        : runDetail.run.reconciliationStatus === "MISMATCHED"
                                        ? "recon-mismatched"
                                        : "recon-other"
                                }`}
                            >
                                대조: {reconciliationLabel[runDetail.run.reconciliationStatus]}
                            </span>
                        </div>
                    </div>

                    {/* Failure information if failed */}
                    {runDetail.run.status === "FAILED" ? (
                        <div className="broker-order-import-warning-box error-theme" role="alert">
                            <strong>조회 실패</strong>
                            <p>실패 사유 코드: {runDetail.run.failureCode || "알 수 없는 실패"}</p>
                            {runDetail.run.providerRequestId ? (
                                <p className="request-id">증권사 문의용 요청 ID: {runDetail.run.providerRequestId}</p>
                            ) : null}
                        </div>
                    ) : null}

                    {/* Staged Counts Metrics Grid */}
                    {counts ? (
                        <div className="broker-order-import-metrics-grid">
                            <div className="metric-card">
                                <span className="metric-label">수집된 주문</span>
                                <strong className="metric-value">{counts.fetchedCount}건</strong>
                                <span className="metric-sub">증권사 응답 기준</span>
                            </div>
                            <div className="metric-card highlight">
                                <span className="metric-label">반영 후보 (스테이징)</span>
                                <strong className="metric-value">{counts.stagedCount}건</strong>
                                <span className="metric-sub">체결 완료·완전성 충족</span>
                            </div>
                            <div className="metric-card">
                                <span className="metric-label">정상 제외 / 보류</span>
                                <strong className="metric-value">{excludedCount}건</strong>
                                <span className="metric-sub">미체결·기반영·보류</span>
                            </div>
                            <div className={`metric-card ${warningCount > 0 ? "warning-theme" : ""}`}>
                                <span className="metric-label">주의 / 중복 의심</span>
                                <strong className="metric-value">{warningCount}건</strong>
                                <span className="metric-sub">{warningCount > 0 ? "사용자 확인 권장" : "특이사항 없음"}</span>
                            </div>
                        </div>
                    ) : null}

                    {/* Opening-balance Baseline Card (Requirement 2) */}
                    <div className="broker-order-baseline-card" role="region" aria-label="개시 잔고 기준점 및 적격 주문 검토">
                        <div className="broker-order-baseline-header">
                            <h5 className="broker-order-baseline-title">개시 잔고 기준점 및 반영 적격 주문 검토</h5>
                            <span
                                className={`broker-order-baseline-badge ${
                                    effectiveBaselineAt ? "baseline-badge-active" : "baseline-badge-none"
                                }`}
                            >
                                {effectiveBaselineAt ? "활성 개시 잔고 기준 적용" : "개시 잔고 기준 없음"}
                            </span>
                        </div>
                        <p className="broker-order-baseline-explanation">
                            {effectiveBaselineAt ? (
                                <>
                                    가장 최근 활성 개시 잔고 승인 시점(<strong>{formatDateTime(effectiveBaselineAt)}</strong>) 이후에 체결된 주문만 매매 원장 반영 대상이 됩니다. 기준 시점 이전 체결분({baselineExcludedCount}건)은 잔고 중복 계상을 방지하기 위해 제외됩니다.
                                </>
                            ) : (
                                <>
                                    등록된 활성 개시 잔고가 없으므로 개시 잔고 시점 제한 없이 스테이징된 모든 체결 주문이 원장 반영 대상이 됩니다.
                                </>
                            )}
                        </p>
                        <div className="broker-order-baseline-metrics">
                            <div className="baseline-metric-item">
                                <span className="baseline-metric-label">반영 적격 주문</span>
                                <strong className={`baseline-metric-value ${eligibleCount > 0 ? "highlight-green" : ""}`}>
                                    {eligibleCount}건
                                </strong>
                            </div>
                            <div className="baseline-metric-item">
                                <span className="baseline-metric-label">개시 잔고 이전 제외</span>
                                <strong className="baseline-metric-value">{baselineExcludedCount}건</strong>
                            </div>
                            <div className="baseline-metric-item">
                                <span className="baseline-metric-label">총 스테이징 주문</span>
                                <strong className="baseline-metric-value">{stagedCount}건</strong>
                            </div>
                            <div className="baseline-metric-item">
                                <span className="baseline-metric-label">보유 수량 대조</span>
                                <strong className="baseline-metric-value">{reconciliationLabel[runDetail.run.reconciliationStatus]}</strong>
                            </div>
                        </div>
                        {((runDetail.approval.suspectedCount ?? 0) > 0 || overrideAllowedCount > 0 || overrideKeptExcludedCount > 0) ? (
                            <div className="broker-order-override-summary-bar">
                                <div className="override-summary-item">
                                    <span className="summary-label">미재판정 의심 (원장 제외)</span>
                                    <strong className={`summary-value ${unresolvedSuspectedCount > 0 ? "text-amber" : ""}`}>
                                        {unresolvedSuspectedCount}건
                                    </strong>
                                </div>
                                <div className="override-summary-item">
                                    <span className="summary-label">재판정 반영 허용</span>
                                    <strong className={`summary-value ${overrideAllowedCount > 0 ? "text-emerald" : ""}`}>
                                        {overrideAllowedCount}건
                                    </strong>
                                </div>
                                <div className="override-summary-item">
                                    <span className="summary-label">재판정 제외 유지</span>
                                    <strong className="summary-value">
                                        {overrideKeptExcludedCount}건
                                    </strong>
                                </div>
                            </div>
                        ) : null}
                        {unresolvedSuspectedCount > 0 ? (
                            <p className="broker-order-baseline-unresolved-note">
                                ※ 미재판정 의심 주문 <strong>{unresolvedSuspectedCount}건</strong>은 안전을 위해 원장에 반영되지 않고 제외됩니다. 원장에 반영하려면 아래 주문 상세 목록에서 '반영 허용'으로 재판정해 주세요.
                            </p>
                        ) : null}
                    </div>

                    {/* Partial Collection Coverage Card (Requirement 2 & 3) */}
                    {isPartialCoverage ? (
                        <div className="broker-order-coverage-card" role="region" aria-label="수집 구간 현황 및 잔여 구간 조회">
                            <div className="broker-order-coverage-header">
                                <div>
                                    <h5 className="broker-order-coverage-title">수집 구간 현황 (부분 수집)</h5>
                                    <p className="broker-order-coverage-desc">
                                        증권사 1회 최대 조회 한도로 인해 요청 구간 중 일부만 먼저 수집되었습니다.
                                    </p>
                                </div>
                                <span className="status-badge run-status-partial">부분 수집</span>
                            </div>
                            <div className="broker-order-coverage-ranges">
                                <div className="coverage-range-item completed">
                                    <span className="coverage-range-label">수집 완료 구간</span>
                                    <strong className="coverage-range-value">
                                        {requestedOrderedFrom} ~ {coveredOrderedTo ?? runDetail.run.queriedOrderedTo}
                                    </strong>
                                    <span className="coverage-range-note">스테이징 및 대조 완료 ({counts?.stagedCount ?? 0}건)</span>
                                </div>
                                <div className="coverage-range-item remaining">
                                    <span className="coverage-range-label">잔여 미수집 구간</span>
                                    <strong className="coverage-range-value">
                                        {nextOrderedFrom ?? "미정"} ~ {requestedOrderedTo}
                                    </strong>
                                    <span className="coverage-range-note">미수집 주문 존재 가능성</span>
                                </div>
                            </div>
                            {nextOrderedFrom ? (
                                <div className="broker-order-coverage-actions">
                                    <button
                                        type="button"
                                        className="secondary-button continue-range-button"
                                        onClick={handleContinueRemainingRange}
                                    >
                                        남은 구간 계속 조회 ({nextOrderedFrom} ~ {requestedOrderedTo})
                                    </button>
                                </div>
                            ) : null}
                        </div>
                    ) : null}

                    {/* Incomplete Coverage Acknowledgement Card (Requirement 3) */}
                    {!currentApproval && (isPartialCoverage || isAcknowledgementBlocker) ? (
                        <div
                            className={`broker-order-acknowledge-card ${acknowledgeIncomplete ? "acknowledged" : ""}`}
                            role="region"
                            aria-label="불완전 수집 이력 승인 동의"
                        >
                            <div className="broker-order-acknowledge-header">
                                <div className="acknowledge-title-group">
                                    <span className="acknowledge-icon" aria-hidden="true">⚠️</span>
                                    <h5 className="broker-order-acknowledge-title">불완전 수집 이력 매매 원장 반영 동의</h5>
                                </div>
                                <span className={`acknowledge-status-badge ${acknowledgeIncomplete ? "badge-agreed" : "badge-required"}`}>
                                    {acknowledgeIncomplete ? "동의 완료" : "필수 동의 필요"}
                                </span>
                            </div>
                            <p className="broker-order-acknowledge-text">
                                현재 수집된 주문은 요청하신 전체 구간({requestedOrderedFrom} ~ {requestedOrderedTo}) 중 <strong>{requestedOrderedFrom} ~ {coveredOrderedTo ?? "일부"}</strong>까지만 포함되어 있습니다. 잔여 구간({nextOrderedFrom ?? "미정"} ~ {requestedOrderedTo})의 주문이 아직 수집되지 않았으므로, 원장 반영 시 일부 거래 내역이 누락될 수 있습니다.
                            </p>
                            <label
                                className="broker-order-acknowledge-checkbox-label"
                                htmlFor="acknowledge-incomplete-coverage-checkbox"
                            >
                                <input
                                    id="acknowledge-incomplete-coverage-checkbox"
                                    type="checkbox"
                                    checked={acknowledgeIncomplete}
                                    onChange={(e) => setAcknowledgeIncomplete(e.target.checked)}
                                    disabled={isAnyActionPending}
                                />
                                <span>
                                    <strong>잔여 구간이 미수집된 불완전 주문 이력임을 확인하였으며, 현재 수집된 적격 주문만 매매 원장에 우선 반영하는 것에 동의합니다.</strong>
                                </span>
                            </label>
                        </div>
                    ) : null}

                    {/* Reversible Audit Status Card (Requirement 5) */}
                    {currentApproval ? (
                        <div className="broker-order-audit-status-card" role="region" aria-label="반영 승인 감사 상태">
                            <div className="audit-status-header">
                                <div className="audit-status-title">
                                    <span className="audit-badge">매매 원장 반영 완료</span>
                                    {currentApproval.coverageAcknowledged ? (
                                        <span className="audit-badge audit-badge-partial">불완전 이력 동의 반영</span>
                                    ) : null}
                                    <span className="audit-reversible-badge">취소 가능 (Reversible)</span>
                                </div>
                                <time className="audit-time" dateTime={currentApproval.approvedAt}>
                                    승인 시각: {formatDateTime(currentApproval.approvedAt)}
                                </time>
                            </div>
                            <div className="audit-status-grid">
                                <div className="audit-stat">
                                    <span className="stat-label">새로 작성된 거래</span>
                                    <strong className="stat-value">{currentApproval.writtenCount}건</strong>
                                </div>
                                <div className="audit-stat">
                                    <span className="stat-label">기존 연계 주문</span>
                                    <strong className="stat-value">{currentApproval.alreadyLinkedCount}건</strong>
                                </div>
                                <div className="audit-stat">
                                    <span className="stat-label">반영 적격 주문</span>
                                    <strong className="stat-value">{currentApproval.eligibleCount}건</strong>
                                </div>
                                {currentApproval.baselineExcludedCount > 0 ? (
                                    <div className="audit-stat">
                                        <span className="stat-label">개시 잔고 제외</span>
                                        <strong className="stat-value">{currentApproval.baselineExcludedCount}건</strong>
                                    </div>
                                ) : null}
                                {currentApproval.overrideAllowedCount !== undefined && currentApproval.overrideAllowedCount > 0 ? (
                                    <div className="audit-stat">
                                        <span className="stat-label">재판정 반영 허용</span>
                                        <strong className="stat-value">{currentApproval.overrideAllowedCount}건</strong>
                                    </div>
                                ) : null}
                                {currentApproval.coverageAcknowledged ? (
                                    <div className="audit-stat">
                                        <span className="stat-label">수집 상태</span>
                                        <strong className="stat-value">불완전 이력 동의</strong>
                                    </div>
                                ) : null}
                            </div>
                            <p className="audit-explanation">
                                매매 원장에 거래 기록이 작성되어 포트폴리오 잔고에 정상 반영되었습니다. 증권사 계좌에는 영향을 주지 않으며, 필요 시 아래 '반영 취소'를 통해 거래 기록을 안전하게 삭제하고 원상 복구할 수 있습니다.
                                {currentApproval.coverageAcknowledged
                                    ? " (이 실행은 잔여 미수집 구간이 존재하는 불완전 이력 승인 동의 하에 반영되었습니다. 필요 시 잔여 구간을 추가로 조회할 수 있습니다.)"
                                    : ""}
                            </p>
                            {!isConfirmingRevoke ? (
                                <div className="audit-actions">
                                    <button
                                        type="button"
                                        className="quiet-action audit-revoke-button"
                                        onClick={() => {
                                            setIsConfirmingRevoke(true);
                                            setActionError(null);
                                            setActionSuccess(null);
                                            setBlockedNotice(null);
                                        }}
                                        disabled={isAnyActionPending}
                                    >
                                        반영 취소 (원상 복구)
                                    </button>
                                </div>
                            ) : (
                                <div className="broker-order-revoke-confirm-panel" aria-label="반영 취소 확인">
                                    <h4>주문 이력 반영 승인을 취소하시겠습니까?</h4>
                                    <p className="revoke-warning-text">
                                        이 실행(#{runDetail.run.id})으로 매매 원장에 작성된 거래 기록({currentApproval.writtenCount}건)이 삭제되고 포트폴리오 잔고가 다시 계산됩니다. 증권사 계좌의 실제 주문 내역은 변경되지 않습니다.
                                    </p>
                                    <div className="form-actions">
                                        <button
                                            type="button"
                                            className="primary-button danger-button"
                                            onClick={() => void handleConfirmRevoke()}
                                            disabled={isRevoking}
                                        >
                                            {isRevoking ? "취소 처리 중..." : "반영 취소 확인"}
                                        </button>
                                        <button
                                            type="button"
                                            className="secondary-button"
                                            onClick={() => setIsConfirmingRevoke(false)}
                                            disabled={isRevoking}
                                        >
                                            닫기
                                        </button>
                                    </div>
                                </div>
                            )}
                        </div>
                    ) : null}

                    {/* Safe Blocked Notice (Requirement 409 safe blocked state) */}
                    {blockedNotice ? (
                        <div className="broker-order-blocked-card" role="alert">
                            <strong>{blockedNotice.title}</strong>
                            <p>{blockedNotice.message}</p>
                        </div>
                    ) : null}

                    {/* In-context Approval Confirmation Panel (Requirement 4) */}
                    {!currentApproval && isConfirmingApproval ? (
                        <div className="broker-order-confirm-panel" aria-label="주문 이력 매매 원장 반영 확인">
                            <h4>주문 이력을 매매 원장에 반영하시겠습니까?</h4>
                            <div className="broker-order-confirm-banner" role="note">
                                <p>
                                    <strong>안전 확인:</strong> 이 승인은 <strong>Trade Guide 내부 매매 원장(거래 기록)에만 반영</strong>되며, <strong>증권사 계좌로 실제 매매 주문을 절대 전송하지 않습니다.</strong>
                                </p>
                            </div>
                            {isPartialCoverage ? (
                                <div className="broker-order-confirm-banner warning" role="note">
                                    <p>
                                        <strong>부분 수집 확인:</strong> 요청 구간 중 {requestedOrderedFrom} ~ {coveredOrderedTo ?? "일부"}까지만 반영됩니다. (잔여 미수집: {nextOrderedFrom ?? "미정"} ~ {requestedOrderedTo})
                                    </p>
                                </div>
                            ) : null}
                            {unresolvedSuspectedCount > 0 ? (
                                <div className="broker-order-confirm-banner warning" role="note">
                                    <p>
                                        <strong>미재판정 의심 주문 안내:</strong> 미재판정 상태인 의심 주문 <strong>{unresolvedSuspectedCount}건</strong>은 매매 원장에 반영되지 않고 제외됩니다. 이 주문들을 원장에 포함하려면 승인 전에 아래 주문 상세 목록에서 '반영 허용'으로 재판정해 주세요.
                                    </p>
                                </div>
                            ) : null}
                            <dl className="broker-order-confirm-details">
                                <div>
                                    <dt>실행 번호</dt>
                                    <dd>#{runDetail.run.id}</dd>
                                </div>
                                <div>
                                    <dt>조회 구간</dt>
                                    <dd>
                                        {runDetail.run.requestedOrderedFrom} ~ {runDetail.run.requestedOrderedTo}
                                        {isPartialCoverage ? ` (수집 완료: ~${coveredOrderedTo ?? "일부"})` : ""}
                                    </dd>
                                </div>
                                {isPartialCoverage ? (
                                    <div>
                                        <dt>불완전 이력 동의</dt>
                                        <dd>{acknowledgeIncomplete ? "확인 및 동의 완료" : "미동의"}</dd>
                                    </div>
                                ) : null}
                                <div>
                                    <dt>개시 잔고 기준</dt>
                                    <dd>{effectiveBaselineAt ? `${formatDateTime(effectiveBaselineAt)} 이후 체결분` : "기준점 없음 (전체 대상)"}</dd>
                                </div>
                                <div>
                                    <dt>반영 대상 적격 주문</dt>
                                    <dd>
                                        <strong>{eligibleCount}건</strong> (총 스테이징 {stagedCount}건 중)
                                        {overrideAllowedCount > 0 ? (
                                            <span className="confirm-sub-note"> (재판정 반영 허용 {overrideAllowedCount}건 포함)</span>
                                        ) : null}
                                    </dd>
                                </div>
                                {overrideAllowedCount > 0 ? (
                                    <div>
                                        <dt>재판정 반영 허용</dt>
                                        <dd><strong>{overrideAllowedCount}건</strong> (반영 대상 포함)</dd>
                                    </div>
                                ) : null}
                                {overrideKeptExcludedCount > 0 ? (
                                    <div>
                                        <dt>재판정 제외 유지</dt>
                                        <dd>{overrideKeptExcludedCount}건 (원장 제외)</dd>
                                    </div>
                                ) : null}
                                {unresolvedSuspectedCount > 0 ? (
                                    <div>
                                        <dt>미재판정 의심 (원장 미반영)</dt>
                                        <dd><strong className="text-warning">{unresolvedSuspectedCount}건</strong></dd>
                                    </div>
                                ) : null}
                                {alreadyLinkedCount > 0 ? (
                                    <div>
                                        <dt>기존 연계 주문 (중복 작성 제외)</dt>
                                        <dd>{alreadyLinkedCount}건 (새로 작성 예정: <strong>{writableCount}건</strong>)</dd>
                                    </div>
                                ) : null}
                                {baselineExcludedCount > 0 ? (
                                    <div>
                                        <dt>개시 잔고 이전 제외</dt>
                                        <dd>{baselineExcludedCount}건</dd>
                                    </div>
                                ) : null}
                                <div>
                                    <dt>보유 대조 검증</dt>
                                    <dd>수량 일치 (MATCHED 검증 통과)</dd>
                                </div>
                            </dl>
                            <div className="form-actions">
                                <button
                                    type="button"
                                    className="primary-button"
                                    onClick={() => void handleConfirmApprove()}
                                    disabled={isApproving}
                                >
                                    {isApproving ? "원장 반영 중..." : "확인 및 매매 원장 반영"}
                                </button>
                                <button
                                    type="button"
                                    className="secondary-button"
                                    onClick={() => setIsConfirmingApproval(false)}
                                    disabled={isApproving}
                                >
                                    취소
                                </button>
                            </div>
                        </div>
                    ) : null}

                    {/* Approval Trigger Banner / Safe Blocked Notice (Requirements 1 & 3) */}
                    {!currentApproval && !isConfirmingApproval && (
                        canApprove ? (
                            <div className="broker-order-approval-block ready" role="region" aria-label="원장 반영 승인 안내">
                                <div className="approval-block-content">
                                    <div className="approval-block-text">
                                        <strong>원장 반영 승인 준비 완료</strong>
                                        <p>
                                            수량 대조가 일치하며, 개시 잔고 기준 이후 체결된 적격 주문 {eligibleCount}건
                                            {alreadyLinkedCount > 0 ? ` (신규 작성 ${writableCount}건, 기연계 ${alreadyLinkedCount}건)` : ""}
                                            을 매매 원장에 반영할 수 있습니다.
                                            {overrideAllowedCount > 0 ? ` (재판정 반영 허용 ${overrideAllowedCount}건 포함)` : ""}
                                            {unresolvedSuspectedCount > 0
                                                ? ` ※ 미재판정 의심 주문 ${unresolvedSuspectedCount}건은 원장에 반영되지 않고 제외됩니다.`
                                                : ""}
                                            {acknowledgeIncomplete ? " (불완전 수집 이력 반영 동의됨)" : ""}
                                        </p>
                                    </div>
                                    <button
                                        type="button"
                                        className="primary-button"
                                        onClick={() => {
                                            setIsConfirmingApproval(true);
                                            setActionError(null);
                                            setActionSuccess(null);
                                            setBlockedNotice(null);
                                        }}
                                        disabled={isAnyActionPending}
                                    >
                                        원장 반영 승인 검토
                                    </button>
                                </div>
                            </div>
                        ) : (
                            <div className="broker-order-approval-block blocked" role="region" aria-label="원장 반영 불가 안내">
                                <div className="approval-block-content">
                                    <div className="approval-block-text">
                                        <strong>원장 반영 승인 비활성 (안전 차단)</strong>
                                        <p>
                                            {runDetail.approval.blocker && approvalBlockerDescriptions[runDetail.approval.blocker]
                                                ? approvalBlockerDescriptions[runDetail.approval.blocker](
                                                      effectiveBaselineAt,
                                                      baselineExcludedCount,
                                                      coveredOrderedTo,
                                                  )
                                                : (runDetail.run.status !== "STAGED"
                                                      ? (runDetail.run.status === "FAILED"
                                                            ? "조회 실패 실행이므로 원장에 반영할 수 없습니다."
                                                            : "조회가 아직 완료되지 않았습니다.")
                                                      : !isReconciliationMatched
                                                      ? "보유 종목 대조를 통과하지 못해 원장 반영이 차단되었습니다."
                                                      : "현재 상태에서는 원장에 반영할 수 없습니다.")}
                                        </p>
                                    </div>
                                    {isAcknowledgementBlocker && !acknowledgeIncomplete ? (
                                        <button
                                            type="button"
                                            className="secondary-button acknowledge-focus-button"
                                            onClick={handleFocusAcknowledge}
                                        >
                                            동의 항목으로 이동
                                        </button>
                                    ) : null}
                                </div>
                            </div>
                        )
                    )}

                    {/* Action Feedback Messages */}
                    <div className="broker-order-import-action-feedback" aria-live="polite">
                        {actionError ? <p className="form-error-message" role="alert">{actionError}</p> : null}
                        {actionSuccess ? <p className="form-success-message" role="status">{actionSuccess}</p> : null}
                        {approvalGuidance ? (
                            <ActionableGuidanceCard
                                guidance={approvalGuidance}
                                cooldownSeconds={cooldownSeconds}
                                onDismiss={() => setApprovalGuidance(null)}
                                onApplyPreset={(days) => {
                                    setPresetRange(days);
                                    setApprovalGuidance(null);
                                    const formEl = document.querySelector(".broker-order-import-form");
                                    if (formEl) formEl.scrollIntoView({behavior: "smooth", block: "center"});
                                }}
                                onGoToStep={onGoToStep}
                                onRetry={() => void handleConfirmApprove()}
                                onFocusAcknowledge={handleFocusAcknowledge}
                            />
                        ) : null}
                    </div>

                    {/* Warnings Section (manual-overlap & duplicate-suspected warnings) */}
                    {counts && (counts.manualOverlapSuspectedCount > 0 || counts.duplicateSuspectedCount > 0 || counts.amountMismatchCount > 0 || counts.pendingSettlementCount > 0) ? (
                        <div className="broker-order-import-warnings-container" role="region" aria-label="주의 및 경고 안내">
                            {counts.manualOverlapSuspectedCount > 0 ? (
                                <div className="broker-order-import-warning-box warning-theme">
                                    <div className="warning-title">
                                        <span aria-hidden="true">⚠️</span>
                                        <strong>수동 기록 중복 의심 ({counts.manualOverlapSuspectedCount}건)</strong>
                                    </div>
                                    <p>
                                        사용자가 직접 입력한 거래 기록과 동일한 날짜·종목·수량의 주문이 발견되었습니다. 자동 병합이나 삭제는 발생하지 않으며, 아래 주문 상세 목록에서 개별 주문을 확인한 뒤 '반영 허용' 또는 '제외 유지'로 재판정할 수 있습니다.
                                    </p>
                                </div>
                            ) : null}

                            {counts.duplicateSuspectedCount > 0 ? (
                                <div className="broker-order-import-warning-box danger-theme">
                                    <div className="warning-title">
                                        <span aria-hidden="true">⚠️</span>
                                        <strong>주문 식별자 중복 의심 ({counts.duplicateSuspectedCount}건)</strong>
                                    </div>
                                    <p>
                                        주문 식별자는 다르나 주문 내용 지문(종목·수량·가격·시각)이 이미 알고 있는 주문과 일치합니다. 아래 주문 상세 목록에서 개별 주문을 확인한 뒤 '반영 허용' 또는 '제외 유지'로 재판정할 수 있습니다.
                                    </p>
                                </div>
                            ) : null}

                            {counts.amountMismatchCount > 0 ? (
                                <div className="broker-order-import-warning-box info-theme">
                                    <div className="warning-title">
                                        <span aria-hidden="true">ℹ️</span>
                                        <strong>체결 금액 괴리 감지 ({counts.amountMismatchCount}건)</strong>
                                    </div>
                                    <p>
                                        평균 체결가 × 수량과 증권사가 보고한 체결 총액 사이에 단수 차이가 있습니다.
                                    </p>
                                </div>
                            ) : null}

                            {counts.pendingSettlementCount > 0 ? (
                                <div className="broker-order-import-warning-box info-theme">
                                    <div className="warning-title">
                                        <span aria-hidden="true">ℹ️</span>
                                        <strong>부분 체결 보류 ({counts.pendingSettlementCount}건)</strong>
                                    </div>
                                    <p>
                                        아직 추가 체결이 가능한 부분 체결 주문입니다. 당일 세션 종료 후 전량 체결 여부를 확인하고 다시 조회하세요.
                                    </p>
                                </div>
                            ) : null}
                        </div>
                    ) : null}

                    {/* Reconciliation Differences Section */}
                    <div className="broker-order-import-reconciliation-section">
                        <div className="reconciliation-heading">
                            <div>
                                <h5>보유 종목 대조 (Reconciliation)</h5>
                                <p className="section-description">
                                    기존 매매 원장에 스테이징 주문을 시뮬레이션 적용한 재구성 보유 수량과 증권사 최신 스냅샷 수량을 비교합니다.
                                </p>
                            </div>
                            {runDetail.run.reconciliationSnapshotSyncedAt ? (
                                <span className="reconciliation-synced-at">
                                    스냅샷 기준 시각: {formatDateTime(runDetail.run.reconciliationSnapshotSyncedAt)}
                                </span>
                            ) : null}
                        </div>

                        {/* Reconciliation Status Alert */}
                        <div
                            className={`reconciliation-status-card status-${runDetail.run.reconciliationStatus.toLowerCase()}`}
                        >
                            <strong>상태: {reconciliationLabel[runDetail.run.reconciliationStatus]}</strong>
                            {runDetail.run.reconciliationStatus === "MATCHED" ? (
                                <p>모든 종목의 재구성 수량과 증권사 보유 스냅샷 수량이 완벽히 일치합니다.</p>
                            ) : runDetail.run.reconciliationStatus === "MISMATCHED" ? (
                                <p>
                                    재구성 수량과 증권사 스냅샷 사이에 차이가 있습니다. 앞선 이력 누락, 앱 외 거래, 또는 중복 여부를 점검해야 합니다.
                                </p>
                            ) : runDetail.run.reconciliationStatus === "NOT_AVAILABLE" ? (
                                <p>대조할 저장된 증권사 보유 종목 스냅샷이 없습니다. 상단 '저장된 보유 종목 비교'에서 먼저 보유 종목을 갱신해 주세요.</p>
                            ) : (
                                <p>
                                    이력 앞부분이 잘려 과거 매도 시점에 보유 수량보다 많은 매도가 발생했습니다. 임의의 가상 매수를 만들지 않고 안전하게 중단되었습니다.
                                </p>
                            )}
                        </div>

                        {/* Reconciliation Comparison Table */}
                        {runDetail.reconciliation && runDetail.reconciliation.length > 0 ? (
                            <div className="reconciliation-table-wrapper">
                                <table className="reconciliation-table">
                                    <thead>
                                        <tr>
                                            <th scope="col">종목</th>
                                            <th scope="col" className="text-right">시뮬레이션 재구성 수량</th>
                                            <th scope="col" className="text-right">증권사 스냅샷 수량</th>
                                            <th scope="col" className="text-right">수량 차이</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {runDetail.reconciliation.map((line) => {
                                            const diff = Number(line.quantityDifference);
                                            const hasDiff = diff !== 0;

                                            return (
                                                <tr
                                                    key={`${line.market}-${line.ticker}`}
                                                    className={hasDiff ? "reconciliation-row-mismatch" : undefined}
                                                >
                                                    <td>
                                                        <div className="table-symbol-cell">
                                                            <span className="market-badge">{line.market}</span>
                                                            <strong>{line.ticker}</strong>
                                                        </div>
                                                    </td>
                                                    <td className="text-right">{formatQuantity(line.reconstructedQuantity)}</td>
                                                    <td className="text-right">{formatQuantity(line.snapshotQuantity)}</td>
                                                    <td className="text-right">
                                                        <span
                                                            className={`diff-chip ${
                                                                hasDiff ? (diff > 0 ? "diff-positive" : "diff-negative") : "diff-zero"
                                                            }`}
                                                        >
                                                            {diff > 0 ? `+${diff}주` : diff < 0 ? `${diff}주` : "0주 (일치)"}
                                                        </span>
                                                    </td>
                                                </tr>
                                            );
                                        })}
                                    </tbody>
                                </table>
                            </div>
                        ) : (
                            <p className="empty-state">대조할 종목이 없습니다.</p>
                        )}
                    </div>

                    {/* Staged Items List Section */}
                    <div className="broker-order-import-items-section">
                        <div className="items-section-header">
                            <div>
                                <h5>
                                    주문 상세 목록
                                    {itemsPageData ? ` (총 ${itemsPageData.totalElements.toLocaleString()}건)` : ""}
                                </h5>
                                <p className="section-description">
                                    조회된 개별 주문의 체결 수량, 체결가 및 스테이징 분류 사유를 확인합니다.
                                </p>
                            </div>

                            {/* Filter Controls: Primary chips, detailed status select, symbol search */}
                            <div className="items-section-controls">
                                <div className="filter-chip-group" role="tablist" aria-label="주문 목록 빠른 상태 필터">
                                    <button
                                        type="button"
                                        role="tab"
                                        aria-selected={statusFilter === ""}
                                        className={`filter-chip ${statusFilter === "" ? "active" : ""}`}
                                        onClick={() => handleStatusFilterChange("")}
                                    >
                                        전체 ({runDetail.run.counts ? runDetail.run.counts.fetchedCount : (itemsPageData?.totalElements ?? 0)})
                                    </button>
                                    <button
                                        type="button"
                                        role="tab"
                                        aria-selected={statusFilter === "STAGED"}
                                        className={`filter-chip ${statusFilter === "STAGED" ? "active" : ""}`}
                                        onClick={() => handleStatusFilterChange("STAGED")}
                                    >
                                        반영 후보 ({counts?.stagedCount ?? 0})
                                    </button>
                                    <button
                                        type="button"
                                        role="tab"
                                        aria-selected={statusFilter === "MANUAL_OVERLAP_SUSPECTED"}
                                        className={`filter-chip ${statusFilter === "MANUAL_OVERLAP_SUSPECTED" ? "active" : ""}`}
                                        onClick={() => handleStatusFilterChange("MANUAL_OVERLAP_SUSPECTED")}
                                    >
                                        수동 중복 ({counts?.manualOverlapSuspectedCount ?? 0})
                                    </button>
                                    <button
                                        type="button"
                                        role="tab"
                                        aria-selected={statusFilter === "DUPLICATE_SUSPECTED"}
                                        className={`filter-chip ${statusFilter === "DUPLICATE_SUSPECTED" ? "active" : ""}`}
                                        onClick={() => handleStatusFilterChange("DUPLICATE_SUSPECTED")}
                                    >
                                        식별자 중복 ({counts?.duplicateSuspectedCount ?? 0})
                                    </button>
                                    <button
                                        type="button"
                                        role="tab"
                                        aria-selected={statusFilter === "SKIPPED_NOT_FILLED"}
                                        className={`filter-chip ${statusFilter === "SKIPPED_NOT_FILLED" ? "active" : ""}`}
                                        onClick={() => handleStatusFilterChange("SKIPPED_NOT_FILLED")}
                                    >
                                        미체결 ({counts?.notFilledCount ?? 0})
                                    </button>
                                    <button
                                        type="button"
                                        role="tab"
                                        aria-selected={statusFilter === "PENDING_SETTLEMENT"}
                                        className={`filter-chip ${statusFilter === "PENDING_SETTLEMENT" ? "active" : ""}`}
                                        onClick={() => handleStatusFilterChange("PENDING_SETTLEMENT")}
                                    >
                                        보류 ({counts?.pendingSettlementCount ?? 0})
                                    </button>
                                </div>

                                <div className="items-search-row">
                                    <div className="status-select-wrap">
                                        <label htmlFor="detailed-status-select" className="sr-only">상세 상태 선택</label>
                                        <select
                                            id="detailed-status-select"
                                            className="broker-status-dropdown"
                                            value={statusFilter}
                                            onChange={(e) => handleStatusFilterChange(e.target.value)}
                                            aria-label="주문 상태 상세 선택"
                                        >
                                            <option value="">상태 전체 보기</option>
                                            <option value="STAGED">반영 후보 (STAGED)</option>
                                            <option value="MANUAL_OVERLAP_SUSPECTED">수동 기록 중복 의심 (MANUAL_OVERLAP_SUSPECTED)</option>
                                            <option value="DUPLICATE_SUSPECTED">중복 식별자 의심 (DUPLICATE_SUSPECTED)</option>
                                            <option value="SKIPPED_NOT_FILLED">미체결 제외 (SKIPPED_NOT_FILLED)</option>
                                            <option value="PENDING_SETTLEMENT">부분 체결 보류 (PENDING_SETTLEMENT)</option>
                                            <option value="ALREADY_IMPORTED">이미 반영됨 (ALREADY_IMPORTED)</option>
                                            <option value="SKIPPED_CONTROL_RECORD">제어 기록 제외 (SKIPPED_CONTROL_RECORD)</option>
                                            <option value="SKIPPED_UNSUPPORTED">미지원 제외 (SKIPPED_UNSUPPORTED)</option>
                                        </select>
                                    </div>

                                    <form className="items-symbol-search-form" onSubmit={handleSymbolSearch} role="search">
                                        <label htmlFor="broker-order-symbol-input" className="sr-only">종목(티커) 검색</label>
                                        <input
                                            id="broker-order-symbol-input"
                                            type="text"
                                            className="symbol-search-input"
                                            placeholder="종목(티커) 검색 (예: AAPL)"
                                            value={symbolInput}
                                            onChange={(e) => setSymbolInput(e.target.value)}
                                        />
                                        <button type="submit" className="secondary-button symbol-search-btn">
                                            검색
                                        </button>
                                        {(statusFilter || symbolFilter) ? (
                                            <button
                                                type="button"
                                                className="quiet-action filter-reset-btn"
                                                onClick={handleResetFilters}
                                            >
                                                필터 초기화
                                            </button>
                                        ) : null}
                                    </form>
                                </div>
                            </div>
                        </div>

                        {/* Loading State */}
                        {isItemsLoading && !itemsPageData ? (
                            <p className="status-message" aria-live="polite">주문 상세 항목을 불러오는 중입니다...</p>
                        ) : null}

                        {/* Error State */}
                        {itemsError ? (
                            <RequestError
                                message={itemsError}
                                retryLabel="주문 상세 다시 불러오기"
                                onRetry={() =>
                                    void loadItems(
                                        runDetail.run.id,
                                        itemsPage,
                                        statusFilter || undefined,
                                        symbolFilter || undefined,
                                    )
                                }
                            />
                        ) : null}

                        {/* Empty State */}
                        {!isItemsLoading && !itemsError && itemsPageData && itemsPageData.items.length === 0 ? (
                            <div className="empty-state broker-order-items-empty">
                                <p>조건에 일치하는 주문 상세 항목이 없습니다.</p>
                                {(statusFilter || symbolFilter) ? (
                                    <p className="section-description">
                                        적용된 필터: {statusFilter ? `상태(${stagingStatusLabel[statusFilter as BrokerOrderStagingStatus] || statusFilter})` : ""}{statusFilter && symbolFilter ? ", " : ""}{symbolFilter ? `종목(${symbolFilter})` : ""}
                                    </p>
                                ) : (
                                    <p className="section-description">이 조회 구간에는 수집된 주문 내역이 없습니다.</p>
                                )}
                                {(statusFilter || symbolFilter) ? (
                                    <button
                                        type="button"
                                        className="secondary-button filter-reset-empty-btn"
                                        onClick={handleResetFilters}
                                    >
                                        필터 초기화
                                    </button>
                                ) : null}
                            </div>
                        ) : null}

                        {/* List and Pagination State */}
                        {itemsPageData && itemsPageData.items.length > 0 ? (
                            <>
                                {isItemsLoading ? (
                                    <p className="status-message items-subtle-loading" aria-live="polite">
                                        항목을 새로 불러오는 중입니다...
                                    </p>
                                ) : null}
                                <ul className="broker-order-import-items-list" aria-label="주문 목록">
                                    {itemsPageData.items.map((item) => {
                                        const isBuy = item.orderSide === "BUY";
                                        const isWarning = isWarningItem(item);
                                        const isSuspected =
                                            item.stagingStatus === "MANUAL_OVERLAP_SUSPECTED" ||
                                            item.stagingStatus === "DUPLICATE_SUSPECTED";
                                        const existingOverride = itemOverrides[item.id];
                                        const isAllowedByOverride = existingOverride?.decision === "ALLOW_LEDGER_WRITE";
                                        const isExcludedByOverride = existingOverride?.decision === "KEEP_EXCLUDED";

                                        const effectiveStaging = isAllowedByOverride
                                            ? "STAGED"
                                            : item.stagingStatus;

                                        const isEligible =
                                            effectiveStaging === "STAGED" &&
                                            (!effectiveBaselineAt ||
                                                (item.filledAt &&
                                                    new Date(item.filledAt).getTime() >
                                                        new Date(effectiveBaselineAt).getTime()));
                                        const isBaselineExcluded =
                                            effectiveStaging === "STAGED" && !isEligible;

                                        return (
                                            <li
                                                key={item.id}
                                                className={`broker-order-item-card ${
                                                    isAllowedByOverride
                                                        ? "item-override-allowed"
                                                        : isExcludedByOverride
                                                        ? "item-override-excluded"
                                                        : isWarning
                                                        ? "item-warning"
                                                        : item.stagingStatus === "STAGED"
                                                        ? "item-staged"
                                                        : ""
                                                }`}
                                            >
                                                <div className="broker-order-item-main">
                                                    {/* Left: Identity and Badges */}
                                                    <div className="broker-order-item-identity">
                                                        <div className="identity-row">
                                                            <span className="market-badge">{item.market}</span>
                                                            <strong className="broker-order-item-ticker">{item.ticker}</strong>
                                                            <span className="broker-order-item-name">{item.displayName}</span>
                                                        </div>
                                                        <div className="badge-row">
                                                            <span
                                                                className={`order-side-badge ${isBuy ? "side-buy" : "side-sell"}`}
                                                            >
                                                                {isBuy ? "매수" : "매도"}
                                                            </span>
                                                            <span className="status-badge staging-badge">
                                                                {stagingStatusLabel[item.stagingStatus] || item.stagingStatus}
                                                            </span>
                                                            {existingOverride ? (
                                                                <span
                                                                    className={`status-badge override-decision-chip ${
                                                                        isAllowedByOverride ? "chip-allowed" : "chip-excluded"
                                                                    }`}
                                                                >
                                                                    {isAllowedByOverride ? "재판정: 반영 허용" : "재판정: 제외 유지"}
                                                                </span>
                                                            ) : null}
                                                            {isEligible ? (
                                                                <span className="eligible-chip">
                                                                    {isAllowedByOverride ? "반영 적격 (재판정)" : "반영 적격"}
                                                                </span>
                                                            ) : null}
                                                            {isBaselineExcluded ? (
                                                                <span className="muted-chip">개시 잔고 이전 (원장 제외)</span>
                                                            ) : null}
                                                            {item.skipReasonCode && !isAllowedByOverride ? (
                                                                <span className="skip-reason-badge">
                                                                    {skipReasonLabel[item.skipReasonCode] || item.skipReasonCode}
                                                                </span>
                                                            ) : null}
                                                            {item.amountMismatch ? (
                                                                <span className="warning-chip">금액 괴리</span>
                                                            ) : null}
                                                            {item.feeUnknown ? (
                                                                <span className="info-chip">수수료 미상</span>
                                                            ) : null}
                                                            {item.buyTax ? (
                                                                <span className="info-chip">매수 세금</span>
                                                            ) : null}
                                                        </div>
                                                    </div>

                                                    {/* Right: Quantity and Amount */}
                                                    <div className="broker-order-item-figures">
                                                        <div className="figures-row">
                                                            <span className="figure-label">체결/주문</span>
                                                            <strong className="figure-value">
                                                                {formatQuantity(item.filledQuantity)}
                                                                <span className="figure-sub"> / {formatQuantity(item.orderedQuantity)}</span>
                                                            </strong>
                                                        </div>
                                                        <div className="figures-row">
                                                            <span className="figure-label">평균 체결가</span>
                                                            <strong className="figure-value">
                                                                {formatPrice(item.averageFilledPrice, item.currencyCode)}
                                                            </strong>
                                                        </div>
                                                    </div>
                                                </div>

                                                {/* Item Override Section for suspected items (Requirement 2) */}
                                                {isSuspected ? (
                                                    existingOverride ? (
                                                        <div
                                                            className={`broker-order-override-audit-card ${
                                                                isAllowedByOverride ? "decision-allowed" : "decision-excluded"
                                                            }`}
                                                            role="region"
                                                            aria-label="재판정 감사 기록"
                                                        >
                                                            <div className="override-audit-header">
                                                                <div className="override-audit-title-group">
                                                                    <span
                                                                        className={`status-badge override-decision-badge ${
                                                                            isAllowedByOverride ? "badge-allowed" : "badge-excluded"
                                                                        }`}
                                                                    >
                                                                        {isAllowedByOverride ? "재판정 완료: 반영 허용" : "재판정 완료: 제외 유지"}
                                                                    </span>
                                                                    <span className="override-audit-source">사용자 직접 판단 (감사 기록 보존)</span>
                                                                </div>
                                                                <time className="override-audit-time" dateTime={existingOverride.createdAt}>
                                                                    판정 시각: {formatDateTime(existingOverride.createdAt)}
                                                                </time>
                                                            </div>
                                                            <div className="override-audit-body">
                                                                <div className="override-audit-reason-row">
                                                                    <span className="override-audit-reason-label">판단 근거:</span>
                                                                    <p className="override-audit-reason-text">{existingOverride.reason}</p>
                                                                </div>
                                                                <p className="override-audit-notice">
                                                                    {isAllowedByOverride ? (
                                                                        <>
                                                                            기존 수동 기록과 중복이 아님/중복 식별자를 확인하여 원장 반영 후보로 전환되었습니다. <strong>최종 원장 반영은 상단의 '원장 반영 승인' 버튼을 눌러야 완료됩니다.</strong>
                                                                        </>
                                                                    ) : (
                                                                        <>
                                                                            의심 사유를 확인하여 매매 원장에 반영하지 않고 제외를 유지합니다.
                                                                        </>
                                                                    )}
                                                                </p>
                                                            </div>
                                                        </div>
                                                    ) : currentApproval ? (
                                                        <div className="broker-order-override-audit-card locked" role="region" aria-label="재판정 불가 안내">
                                                            <p className="override-audit-notice">
                                                                이 실행은 이미 매매 원장에 반영 승인되어 더 이상 재판정을 등록할 수 없습니다.
                                                            </p>
                                                        </div>
                                                    ) : (
                                                        <div className="broker-order-item-override-panel" role="region" aria-label="의심 항목 재판정">
                                                            <div className="override-panel-header">
                                                                <span className="override-panel-icon" aria-hidden="true">⚖️</span>
                                                                <div className="override-panel-title-group">
                                                                    <strong className="override-panel-title">의심 항목 사용자 재판정</strong>
                                                                    <span className="override-panel-subtitle">
                                                                        {item.stagingStatus === "MANUAL_OVERLAP_SUSPECTED"
                                                                            ? "기존 수동 기록과 일치하여 중복 의심으로 분류된 주문입니다."
                                                                            : "동일한 체결 정보(지문)의 주문이 존재하여 중복 의심으로 분류된 주문입니다."}
                                                                    </span>
                                                                </div>
                                                            </div>

                                                            <div className="override-decision-options" role="radiogroup" aria-label="재판정 결정 선택">
                                                                <label
                                                                    className={`override-decision-option ${
                                                                        overrideDrafts[item.id]?.decision === "ALLOW_LEDGER_WRITE"
                                                                            ? "selected option-allowed"
                                                                            : ""
                                                                    }`}
                                                                >
                                                                    <input
                                                                        type="radio"
                                                                        name={`override-decision-${item.id}`}
                                                                        value="ALLOW_LEDGER_WRITE"
                                                                        checked={overrideDrafts[item.id]?.decision === "ALLOW_LEDGER_WRITE"}
                                                                        onChange={() => {
                                                                            setOverrideDrafts((prev) => ({
                                                                                ...prev,
                                                                                [item.id]: {
                                                                                    ...(prev[item.id] ?? { reason: "" }),
                                                                                    decision: "ALLOW_LEDGER_WRITE",
                                                                                },
                                                                            }));
                                                                            setOverrideErrors((prev) => ({ ...prev, [item.id]: null }));
                                                                        }}
                                                                        disabled={Boolean(overrideSubmitting[item.id])}
                                                                    />
                                                                    <div className="option-text-group">
                                                                        <span className="option-title">반영 허용 (원장 반영 후보로 전환)</span>
                                                                        <span className="option-desc">
                                                                            기존 수동 기록과 중복이 아님 / 중복 식별자를 확인했다는 사용자의 명시적 판단입니다.
                                                                            (최종 원장 반영은 상단 승인 버튼을 눌러야 합니다)
                                                                        </span>
                                                                    </div>
                                                                </label>

                                                                <label
                                                                    className={`override-decision-option ${
                                                                        overrideDrafts[item.id]?.decision === "KEEP_EXCLUDED"
                                                                            ? "selected option-excluded"
                                                                            : ""
                                                                    }`}
                                                                >
                                                                    <input
                                                                        type="radio"
                                                                        name={`override-decision-${item.id}`}
                                                                        value="KEEP_EXCLUDED"
                                                                        checked={overrideDrafts[item.id]?.decision === "KEEP_EXCLUDED"}
                                                                        onChange={() => {
                                                                            setOverrideDrafts((prev) => ({
                                                                                ...prev,
                                                                                [item.id]: {
                                                                                    ...(prev[item.id] ?? { reason: "" }),
                                                                                    decision: "KEEP_EXCLUDED",
                                                                                },
                                                                            }));
                                                                            setOverrideErrors((prev) => ({ ...prev, [item.id]: null }));
                                                                        }}
                                                                        disabled={Boolean(overrideSubmitting[item.id])}
                                                                    />
                                                                    <div className="option-text-group">
                                                                        <span className="option-title">제외 유지 (원장 미반영 유지)</span>
                                                                        <span className="option-desc">
                                                                            의심 사유를 유지하며 현재처럼 원장에 반영하지 않습니다.
                                                                        </span>
                                                                    </div>
                                                                </label>
                                                            </div>

                                                            <div className="override-reason-field">
                                                                <div className="override-reason-header">
                                                                    <label htmlFor={`override-reason-${item.id}`} className="override-reason-label">
                                                                        재판정 사유 <span className="required-star" aria-hidden="true">*</span>
                                                                    </label>
                                                                    <span
                                                                        className={`override-char-count ${
                                                                            (overrideDrafts[item.id]?.reason?.trim()?.length ?? 0) > 500 ? "exceeded" : ""
                                                                        }`}
                                                                    >
                                                                        {(overrideDrafts[item.id]?.reason?.trim()?.length ?? 0)} / 500자
                                                                    </span>
                                                                </div>
                                                                <textarea
                                                                    id={`override-reason-${item.id}`}
                                                                    className="override-reason-textarea"
                                                                    placeholder="재판정 판단 근거를 구체적으로 입력하세요 (예: 증권사 체결 확인 결과 수기 입력과 별개 주문임)"
                                                                    rows={2}
                                                                    maxLength={500}
                                                                    value={overrideDrafts[item.id]?.reason ?? ""}
                                                                    onChange={(e) => {
                                                                        const val = e.target.value;
                                                                        setOverrideDrafts((prev) => ({
                                                                            ...prev,
                                                                            [item.id]: { ...(prev[item.id] ?? { decision: "" }), reason: val },
                                                                        }));
                                                                        setOverrideErrors((prev) => ({ ...prev, [item.id]: null }));
                                                                    }}
                                                                    disabled={Boolean(overrideSubmitting[item.id])}
                                                                    required
                                                                />
                                                                <p className="override-reason-hint">
                                                                    사유 입력은 필수이며(공백만 제출 불가, 최대 500자), 감사 기록으로 보존됩니다.
                                                                </p>
                                                            </div>

                                                            {overrideErrors[item.id] ? (
                                                                <p className="form-error-message override-feedback" role="alert">
                                                                    {overrideErrors[item.id]}
                                                                </p>
                                                            ) : null}
                                                            {overrideSuccesses[item.id] ? (
                                                                <p className="form-success-message override-feedback" role="status">
                                                                    {overrideSuccesses[item.id]}
                                                                </p>
                                                            ) : null}

                                                            <div className="override-actions">
                                                                <button
                                                                    type="button"
                                                                    className="primary-button override-submit-btn"
                                                                    onClick={() => void handleOverrideSubmit(item)}
                                                                    disabled={
                                                                        Boolean(overrideSubmitting[item.id]) ||
                                                                        !overrideDrafts[item.id]?.decision ||
                                                                        !(overrideDrafts[item.id]?.reason?.trim()) ||
                                                                        (overrideDrafts[item.id]?.reason?.trim().length ?? 0) > 500
                                                                    }
                                                                >
                                                                    {overrideSubmitting[item.id]
                                                                        ? "재판정 등록 중..."
                                                                        : overrideDrafts[item.id]?.decision === "ALLOW_LEDGER_WRITE"
                                                                        ? "반영 허용으로 재판정 등록"
                                                                        : overrideDrafts[item.id]?.decision === "KEEP_EXCLUDED"
                                                                        ? "제외 유지로 재판정 등록"
                                                                        : "재판정 등록"}
                                                                </button>
                                                            </div>
                                                        </div>
                                                    )
                                                ) : null}

                                                {/* Footer: Timestamps, Commission, Order ID */}
                                                <div className="broker-order-item-footer">
                                                    <div className="footer-meta">
                                                        <span>주문: {formatDateTime(item.orderedAt)}</span>
                                                        {item.filledAt ? (
                                                            <span>체결: {formatDateTime(item.filledAt)}</span>
                                                        ) : (
                                                            <span className="muted-text">체결 시각 없음</span>
                                                        )}
                                                        {item.commission !== null ? (
                                                            <span>수수료: ${Number(item.commission).toLocaleString("en-US", {minimumFractionDigits: 2})}</span>
                                                        ) : null}
                                                        {item.tax !== null && Number(item.tax) > 0 ? (
                                                            <span>세금: ${Number(item.tax).toLocaleString("en-US", {minimumFractionDigits: 2})}</span>
                                                        ) : null}
                                                    </div>
                                                    <div className="footer-id">
                                                        <span
                                                            className="external-order-id"
                                                            title={`증권사 주문 ID: ${item.externalOrderId}`}
                                                            aria-label={`증권사 주문 ID: ${item.externalOrderId}`}
                                                        >
                                                            {formatExternalOrderId(item.externalOrderId)}
                                                        </span>
                                                    </div>
                                                </div>
                                            </li>
                                        );
                                    })}
                                </ul>

                                {/* Server Pagination Controls */}
                                <nav className="broker-order-items-pagination" aria-label="주문 목록 페이지 탐색">
                                    <button
                                        type="button"
                                        className="secondary-button pagination-btn"
                                        onClick={() => handlePageChange(itemsPage - 1)}
                                        disabled={itemsPage === 0 || isItemsLoading}
                                        aria-label="이전 페이지"
                                    >
                                        이전
                                    </button>
                                    <div className="pagination-info" aria-live="polite">
                                        <span className="pagination-page-number">
                                            <strong>{itemsPage + 1}</strong> / {Math.max(1, Math.ceil(itemsPageData.totalElements / DEFAULT_PAGE_SIZE))} 페이지
                                        </span>
                                        <span className="pagination-total-count">
                                            (총 {itemsPageData.totalElements.toLocaleString()}건)
                                        </span>
                                    </div>
                                    <button
                                        type="button"
                                        className="secondary-button pagination-btn"
                                        onClick={() => handlePageChange(itemsPage + 1)}
                                        disabled={!itemsPageData.hasNext || isItemsLoading}
                                        aria-label="다음 페이지"
                                    >
                                        다음
                                    </button>
                                </nav>
                            </>
                        ) : null}
                    </div>
                </div>
            ) : null}
        </section>
    );
}
