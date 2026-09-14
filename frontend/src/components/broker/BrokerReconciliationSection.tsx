import {useEffect, useState} from "react";
import {Link} from "react-router-dom";
import {
    createBrokerReconciliationRun,
    getBrokerReconciliationRunDetail,
    getBrokerReconciliationRuns,
} from "../../api/brokerReconciliationApi";
import {hasApiStatus} from "../../api/apiError";
import {formatDateTime} from "../../utils/format";
import RequestError from "../common/RequestError";
import type {
    BrokerReconciliationLine,
    BrokerReconciliationReasonCode,
    BrokerReconciliationRun,
    BrokerReconciliationRunDetail,
} from "../../types/brokerReconciliation";
import type {BrokerHoldingComparison} from "../../types/portfolioBroker";

type BrokerReconciliationSectionProps = {
    memberId: number;
    portfolioId: number;
};

const DEFAULT_PAGE_SIZE = 5;

const comparisonLabels: Record<BrokerHoldingComparison, string> = {
    MATCHED: "수량 일치",
    QUANTITY_MISMATCH: "수량 불일치",
    ONLY_IN_BROKER: "증권사에만 보유",
    ONLY_IN_TRADE_GUIDE: "원장에만 보유",
};

const comparisonPillClasses: Record<BrokerHoldingComparison, string> = {
    MATCHED: "pill-done",
    QUANTITY_MISMATCH: "pill-warning",
    ONLY_IN_BROKER: "pill-info",
    ONLY_IN_TRADE_GUIDE: "pill-neutral",
};

const reasonCodeLabels: Record<BrokerReconciliationReasonCode, string> = {
    UNAPPROVED_RUN_EXISTS: "미승인 주문 이력 존재",
    BASELINE_EXCLUDED_HISTORY: "개시 잔고 이전 체결",
    UNSETTLED_OR_PARTIAL_FILL: "미체결/부분 체결 보류",
    OUT_OF_PERIOD_HISTORY: "조회 구간 외 거래 가능성",
    LEDGER_MARKET_UNSUPPORTED: "원장 미지원 시장",
    UNEXPLAINED_DIFFERENCE: "원인 직접 확인 필요",
};

const reasonCodeDescriptions: Record<
    BrokerReconciliationReasonCode,
    {text: string; actionText?: string; actionLink?: string}
> = {
    UNAPPROVED_RUN_EXISTS: {
        text: "최근 주문 조회 건 중 원장에 반영되지 않은 체결 내역이 있습니다. 주문 이력을 승인하면 차이가 해소될 수 있습니다.",
        actionText: "주문 이력 검토로 이동",
        actionLink: "/broker-accounts#broker-order-imports",
    },
    BASELINE_EXCLUDED_HISTORY: {
        text: "활성 개시 잔고 기준 시점 이전에 체결된 주문으로, 이미 개시 잔고에 포함되어 원장 중복 방지를 위해 제외된 내역입니다.",
    },
    UNSETTLED_OR_PARTIAL_FILL: {
        text: "아직 전량 체결되지 않은 주문이 있습니다. 체결이 완료되면 수량이 달라질 수 있습니다.",
    },
    OUT_OF_PERIOD_HISTORY: {
        text: "성공한 주문 조회가 없거나, 마지막 조회 기간이 이번 스냅샷 시점보다 이전에 끝났습니다. 최근 기간을 다시 조회해 보세요.",
        actionText: "주문 기간 조회하기",
        actionLink: "/broker-accounts#broker-order-imports",
    },
    LEDGER_MARKET_UNSUPPORTED: {
        text: "현재 매매 원장에서 통화/시장 구조상 지원하지 않는 시장(국내 주식 등)의 종목입니다.",
    },
    UNEXPLAINED_DIFFERENCE: {
        text: "저장된 주문/잔고 이력으로 설명되지 않는 차이입니다. 직접 수동 매매 원장을 확인해 주세요.",
    },
};

function formatQuantity(qty: number | string | null | undefined): string {
    if (qty === null || qty === undefined) return "없음";
    const num = Number(qty);
    if (isNaN(num)) return String(qty);
    return `${num.toLocaleString("en-US")}주`;
}

function calculateQuantityDifference(
    brokerQty: number | string | null | undefined,
    tradeGuideQty: number | string | null | undefined,
): string {
    if (brokerQty === null || brokerQty === undefined) {
        if (tradeGuideQty === null || tradeGuideQty === undefined) return "0주";
        return `-${Number(tradeGuideQty).toLocaleString("en-US")}주`;
    }
    if (tradeGuideQty === null || tradeGuideQty === undefined) {
        return `+${Number(brokerQty).toLocaleString("en-US")}주`;
    }
    const diff = Number(brokerQty) - Number(tradeGuideQty);
    if (diff > 0) return `+${diff.toLocaleString("en-US")}주`;
    if (diff < 0) return `${diff.toLocaleString("en-US")}주`;
    return "0주";
}

export default function BrokerReconciliationSection({
    memberId,
    portfolioId,
}: BrokerReconciliationSectionProps) {
    // Paged runs list
    const [runs, setRuns] = useState<BrokerReconciliationRun[] | null>(null);
    const [runsPage, setRunsPage] = useState(0);
    const [hasNextRuns, setHasNextRuns] = useState(false);
    const [totalRuns, setTotalRuns] = useState(0);
    const [isRunsLoading, setIsRunsLoading] = useState(true);
    const [runsError, setRunsError] = useState<string | null>(null);
    const [runsRevision, setRunsRevision] = useState(0);

    // Selected run detail
    const [selectedRunId, setSelectedRunId] = useState<number | null>(null);
    const [selectedRunDetail, setSelectedRunDetail] = useState<BrokerReconciliationRunDetail | null>(null);
    const [isDetailLoading, setIsDetailLoading] = useState(false);
    const [detailError, setDetailError] = useState<string | null>(null);

    // Run creation states
    const [isCreating, setIsCreating] = useState(false);
    const [createError, setCreateError] = useState<{message: string; isMissingSnapshot?: boolean} | null>(null);
    const [createSuccess, setCreateSuccess] = useState<string | null>(null);

    // Filter for lines in detail view
    const [lineFilter, setLineFilter] = useState<"ALL" | "DIFFERENCES" | "MATCHED">("ALL");

    // Fetch runs on mount, page change, or revision
    useEffect(() => {
        let isCurrent = true;

        void getBrokerReconciliationRuns(memberId, portfolioId, runsPage, DEFAULT_PAGE_SIZE)
            .then((pageData) => {
                if (!isCurrent) return;
                setRuns(pageData.items);
                setRunsPage(pageData.page);
                setTotalRuns(pageData.totalElements);
                setHasNextRuns(pageData.hasNext);
                setRunsError(null);

                // If no run is selected yet and we have items, select the first run
                if (selectedRunId === null && pageData.items.length > 0) {
                    setSelectedRunId(pageData.items[0].id);
                    setIsDetailLoading(true);
                }
            })
            .catch((err: unknown) => {
                if (!isCurrent) return;
                if (hasApiStatus(err, 404)) {
                    setRuns([]);
                    setRunsError(null);
                    return;
                }
                setRuns([]);
                setRunsError(err instanceof Error ? err.message : "정합성 점검 이력을 불러오지 못했습니다.");
            })
            .finally(() => {
                if (isCurrent) {
                    setIsRunsLoading(false);
                }
            });

        return () => {
            isCurrent = false;
        };
    }, [memberId, portfolioId, runsPage, runsRevision, selectedRunId]);

    // Fetch detail whenever selectedRunId changes
    useEffect(() => {
        if (selectedRunId === null) {
            return;
        }

        let isCurrent = true;

        void getBrokerReconciliationRunDetail(memberId, portfolioId, selectedRunId)
            .then((detail) => {
                if (!isCurrent) return;
                setSelectedRunDetail(detail);
                setDetailError(null);
            })
            .catch((err: unknown) => {
                if (!isCurrent) return;
                setSelectedRunDetail(null);
                setDetailError(
                    err instanceof Error ? err.message : "정합성 점검 상세 결과를 불러오지 못했습니다.",
                );
            })
            .finally(() => {
                if (isCurrent) {
                    setIsDetailLoading(false);
                }
            });

        return () => {
            isCurrent = false;
        };
    }, [memberId, portfolioId, selectedRunId]);

    // Create a new reconciliation run
    const handleCreateRun = async () => {
        setIsCreating(true);
        setCreateError(null);
        setCreateSuccess(null);

        try {
            const detail = await createBrokerReconciliationRun(memberId, portfolioId);
            setSelectedRunId(detail.run.id);
            setSelectedRunDetail(detail);
            setCreateSuccess(
                `새 원장 정합성 점검이 완료되었습니다. (실행 ID: #${detail.run.id}, 대조 결과: ${
                    detail.run.overallStatus === "MATCHED" ? "수량 일치" : "차이 발견"
                })`,
            );
            setIsRunsLoading(true);
            if (runsPage === 0) {
                setRunsRevision((r) => r + 1);
            } else {
                setRunsPage(0);
            }
        } catch (err: unknown) {
            const isMissing =
                hasApiStatus(err, 404) ||
                (err instanceof Error && err.message.includes("스냅샷"));
            setCreateError({
                message:
                    err instanceof Error ? err.message : "정합성 점검을 실행하지 못했습니다.",
                isMissingSnapshot: isMissing,
            });
        } finally {
            setIsCreating(false);
        }
    };

    const handleSelectRun = (runId: number) => {
        if (runId !== selectedRunId) {
            setIsDetailLoading(true);
            setSelectedRunId(runId);
        }
    };

    const handlePageChange = (newPage: number) => {
        setIsRunsLoading(true);
        setRunsPage(newPage);
    };

    // Filter lines
    const filteredLines = (selectedRunDetail?.lines ?? []).filter((line: BrokerReconciliationLine) => {
        if (lineFilter === "MATCHED") return line.comparison === "MATCHED";
        if (lineFilter === "DIFFERENCES") return line.comparison !== "MATCHED";
        return true;
    });

    const activeRun = selectedRunDetail?.run;
    const totalPages = Math.max(1, Math.ceil(totalRuns / DEFAULT_PAGE_SIZE));

    return (
        <section
            id="broker-reconciliation"
            className="content-section broker-reconciliation-section"
            role="region"
            aria-label="원장 정합성 점검"
        >
            <div className="section-heading">
                <div>
                    <p className="section-label">RECONCILIATION & AUDIT</p>
                    <h2>원장 정합성 점검 (스냅샷 대비 대조)</h2>
                </div>
                <div className="section-actions">
                    <button
                        type="button"
                        className="primary-button"
                        onClick={() => void handleCreateRun()}
                        disabled={isCreating}
                        aria-busy={isCreating}
                    >
                        {isCreating ? "점검 실행 중..." : "새 점검 실행"}
                    </button>
                </div>
            </div>

            <p className="section-description">
                저장된 최신 증권사 보유 종목 스냅샷과 Trade Guide의 현재 매매 원장을 대조하여
                수량 일치 여부와 차이 원인 후보를 점검합니다.
            </p>

            {/* Read-only safety banner */}
            <div className="broker-reconciliation-safety-card" role="note">
                <div className="safety-card-icon" aria-hidden="true">
                    🛡️
                </div>
                <div className="safety-card-content">
                    <strong>읽기 전용 점검 보증</strong>
                    <p>
                        이 점검은 이미 저장된 최신 증권사 스냅샷과 내부 매매 원장만을 비교합니다.{" "}
                        <strong>증권사 API를 추가로 호출하지 않으며, 매매 원장이나 보유 종목을 절대 변경하지 않습니다.</strong>{" "}
                        최신 증권사 잔고와 대조하려면 먼저 연동 계좌에서 스냅샷을 갱신해 주세요.
                    </p>
                </div>
            </div>

            {/* Action Feedback Messages */}
            {createSuccess && (
                <div className="form-success-message" role="status">
                    {createSuccess}
                </div>
            )}

            {createError && (
                <div className="form-error-message broker-actionable-error" role="alert">
                    <p>{createError.message}</p>
                    {createError.isMissingSnapshot && (
                        <div className="error-action-row">
                            <span>보유 종목 스냅샷이 없으면 원장과 대조할 수 없습니다.</span>
                            <Link to="/broker-accounts#step-4" className="quiet-action">
                                연동 계좌에서 스냅샷 갱신하기 →
                            </Link>
                        </div>
                    )}
                </div>
            )}

            {/* Selected Run Details View */}
            {isDetailLoading ? (
                <div className="status-message" aria-live="polite">
                    점검 상세 결과를 불러오는 중입니다...
                </div>
            ) : detailError ? (
                <RequestError
                    message={detailError}
                    onRetry={() => {
                        if (selectedRunId !== null) {
                            setIsDetailLoading(true);
                            setSelectedRunId(selectedRunId);
                        }
                    }}
                    retryLabel="상세 결과 다시 시도"
                />
            ) : selectedRunDetail && activeRun ? (
                <div className="reconciliation-detail-card">
                    <div className="detail-header">
                        <div className="detail-meta">
                            <span className="run-id-badge">실행 #{activeRun.id}</span>
                            <span
                                className={`status-pill ${
                                    activeRun.overallStatus === "MATCHED" ? "pill-done" : "pill-warning"
                                }`}
                            >
                                {activeRun.overallStatus === "MATCHED"
                                    ? "✓ 모든 종목 수량 일치"
                                    : "⚠ 차이 발견"}
                            </span>
                        </div>
                        <div className="detail-timestamps">
                            <span>
                                <strong>점검 실행:</strong> {formatDateTime(activeRun.executedAt)}
                            </span>
                            <span className="meta-divider" aria-hidden="true">
                                ·
                            </span>
                            <span>
                                <strong>대조 기준 스냅샷:</strong> {formatDateTime(activeRun.snapshotSyncedAt)}
                            </span>
                            <span className="meta-divider" aria-hidden="true">
                                ·
                            </span>
                            <span>
                                <strong>계좌:</strong>{" "}
                                {activeRun.provider === "TOSS_SECURITIES" ? "토스증권" : activeRun.provider} (
                                {activeRun.maskedAccountNumber})
                            </span>
                        </div>
                    </div>

                    {/* Stats Metric Bar */}
                    <div className="broker-comparison-stats-bar reconciliation-stats-bar">
                        <div
                            className={`broker-stat-item ${
                                activeRun.matchedCount > 0 ? "highlight" : "muted-theme"
                            }`}
                        >
                            <span className="stat-label">수량 일치</span>
                            <strong className="stat-value">{activeRun.matchedCount}</strong>
                            <span className="stat-sub">종목</span>
                        </div>
                        <div
                            className={`broker-stat-item ${
                                activeRun.quantityMismatchCount > 0 ? "warning-theme" : "muted-theme"
                            }`}
                        >
                            <span className="stat-label">수량 차이</span>
                            <strong className="stat-value">{activeRun.quantityMismatchCount}</strong>
                            <span className="stat-sub">종목</span>
                        </div>
                        <div
                            className={`broker-stat-item ${
                                activeRun.onlyInBrokerCount > 0 ? "accent-theme" : "muted-theme"
                            }`}
                        >
                            <span className="stat-label">증권사에만 보유</span>
                            <strong className="stat-value">{activeRun.onlyInBrokerCount}</strong>
                            <span className="stat-sub">종목</span>
                        </div>
                        <div
                            className={`broker-stat-item ${
                                activeRun.onlyInTradeGuideCount > 0 ? "warning-theme" : "muted-theme"
                            }`}
                        >
                            <span className="stat-label">원장에만 보유</span>
                            <strong className="stat-value">{activeRun.onlyInTradeGuideCount}</strong>
                            <span className="stat-sub">종목</span>
                        </div>
                    </div>

                    {/* Lines Breakdown Section */}
                    <div className="reconciliation-lines-container">
                        <div className="lines-toolbar">
                            <h3>종목별 대조 현황 ({selectedRunDetail.lines.length}개 종목)</h3>
                            <div className="lines-filter-group" role="group" aria-label="종목 대조 필터">
                                <button
                                    type="button"
                                    className={`filter-btn ${lineFilter === "ALL" ? "active" : ""}`}
                                    onClick={() => setLineFilter("ALL")}
                                >
                                    전체 ({selectedRunDetail.lines.length})
                                </button>
                                <button
                                    type="button"
                                    className={`filter-btn ${lineFilter === "DIFFERENCES" ? "active" : ""}`}
                                    onClick={() => setLineFilter("DIFFERENCES")}
                                >
                                    차이 발생 (
                                    {selectedRunDetail.lines.filter(
                                        (l) => l.comparison !== "MATCHED",
                                    ).length}
                                    )
                                </button>
                                <button
                                    type="button"
                                    className={`filter-btn ${lineFilter === "MATCHED" ? "active" : ""}`}
                                    onClick={() => setLineFilter("MATCHED")}
                                >
                                    일치 (
                                    {selectedRunDetail.lines.filter(
                                        (l) => l.comparison === "MATCHED",
                                    ).length}
                                    )
                                </button>
                            </div>
                        </div>

                        {filteredLines.length === 0 ? (
                            <div className="reconciliation-empty-lines">
                                <p>해당 조건에 일치하는 종목이 없습니다.</p>
                            </div>
                        ) : (
                            <div className="reconciliation-table-wrapper">
                                <table className="reconciliation-lines-table">
                                    <caption className="sr-only">
                                        종목별 증권사 수량 및 원장 수량 대조 결과 표
                                    </caption>
                                    <thead>
                                        <tr>
                                            <th scope="col">종목</th>
                                            <th scope="col">대조 결과</th>
                                            <th scope="col" className="text-right">
                                                증권사 수량
                                            </th>
                                            <th scope="col" className="text-right">
                                                원장 수량
                                            </th>
                                            <th scope="col" className="text-right">
                                                수량 차이
                                            </th>
                                            <th scope="col">차이 원인 후보 및 권장 행동</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {filteredLines.map((line: BrokerReconciliationLine) => {
                                            const diffText = calculateQuantityDifference(
                                                line.brokerQuantity,
                                                line.tradeGuideQuantity,
                                            );
                                            const isMatched = line.comparison === "MATCHED";

                                            return (
                                                <tr
                                                    key={`${line.market}-${line.ticker}`}
                                                    className={!isMatched ? "row-difference" : ""}
                                                >
                                                    <td className="cell-asset">
                                                        <div className="asset-identity">
                                                            <div className="asset-top">
                                                                <span className="asset-market-badge">
                                                                    {line.market}
                                                                </span>
                                                                <strong className="asset-ticker">
                                                                    {line.ticker}
                                                                </strong>
                                                            </div>
                                                            <span className="asset-name">
                                                                {line.displayName}
                                                            </span>
                                                        </div>
                                                    </td>
                                                    <td className="cell-comparison">
                                                        <span
                                                            className={`status-pill ${
                                                                comparisonPillClasses[line.comparison]
                                                            }`}
                                                        >
                                                            {comparisonLabels[line.comparison]}
                                                        </span>
                                                    </td>
                                                    <td className="cell-quantity text-right">
                                                        {formatQuantity(line.brokerQuantity)}
                                                    </td>
                                                    <td className="cell-quantity text-right">
                                                        {formatQuantity(line.tradeGuideQuantity)}
                                                    </td>
                                                    <td
                                                        className={`cell-diff text-right ${
                                                            isMatched
                                                                ? "diff-zero"
                                                                : diffText.startsWith("+")
                                                                  ? "diff-positive"
                                                                  : "diff-negative"
                                                        }`}
                                                    >
                                                        {diffText}
                                                    </td>
                                                    <td className="cell-reasons">
                                                        {isMatched && line.reasonCandidates.length === 0 ? (
                                                            <span className="text-muted-notice">
                                                                특이사항 없음 (정합성 일치)
                                                            </span>
                                                        ) : line.reasonCandidates.length === 0 ? (
                                                            <span className="text-muted-notice">
                                                                사유 미지정
                                                            </span>
                                                        ) : (
                                                            <div className="reason-candidates-list">
                                                                {line.reasonCandidates.map((code) => {
                                                                    const desc = reasonCodeDescriptions[code];
                                                                    return (
                                                                        <div
                                                                            key={code}
                                                                            className="reason-candidate-item"
                                                                        >
                                                                            <span className="reason-tag">
                                                                                {reasonCodeLabels[code]}
                                                                            </span>
                                                                            <p className="reason-text">
                                                                                {desc.text}
                                                                            </p>
                                                                            {desc.actionText && desc.actionLink && (
                                                                                <Link
                                                                                    to={desc.actionLink}
                                                                                    className="reason-action-link"
                                                                                >
                                                                                    {desc.actionText} →
                                                                                </Link>
                                                                            )}
                                                                        </div>
                                                                    );
                                                                })}
                                                            </div>
                                                        )}
                                                    </td>
                                                </tr>
                                            );
                                        })}
                                    </tbody>
                                </table>
                            </div>
                        )}
                    </div>
                </div>
            ) : null}

            {/* Runs History Paged List */}
            <div className="reconciliation-history-section">
                <div className="history-heading">
                    <div>
                        <p className="section-label">EXECUTION HISTORY</p>
                        <h3>정합성 점검 이력</h3>
                    </div>
                    {totalRuns > 0 && (
                        <span className="history-count-badge">총 {totalRuns}회 실행</span>
                    )}
                </div>

                {isRunsLoading ? (
                    <div className="status-message" aria-live="polite">
                        점검 이력을 불러오는 중입니다...
                    </div>
                ) : runsError ? (
                    <RequestError
                        message={runsError}
                        onRetry={() => {
                            setIsRunsLoading(true);
                            setRunsRevision((r) => r + 1);
                        }}
                        retryLabel="이력 다시 시도"
                    />
                ) : !runs || runs.length === 0 ? (
                    <div className="empty-state empty-reconciliation">
                        <p>아직 실행된 원장 정합성 점검 이력이 없습니다.</p>
                        <p className="empty-hint">
                            상단의 [새 점검 실행] 버튼을 눌러 저장된 증권사 스냅샷과 내부 원장의 수량을 대조해 보세요.
                        </p>
                    </div>
                ) : (
                    <>
                        <ul className="reconciliation-runs-list" role="list">
                            {runs.map((run: BrokerReconciliationRun) => {
                                const isSelected = run.id === selectedRunId;
                                const isMatched = run.overallStatus === "MATCHED";

                                return (
                                    <li
                                        key={run.id}
                                        className={`reconciliation-run-card ${isSelected ? "selected" : ""}`}
                                    >
                                        <button
                                            type="button"
                                            className="run-card-btn"
                                            onClick={() => handleSelectRun(run.id)}
                                            aria-current={isSelected ? "true" : undefined}
                                            aria-label={`점검 실행 #${run.id} 결과 상세 보기`}
                                        >
                                            <div className="run-card-main">
                                                <div className="run-card-header">
                                                    <span className="run-card-id">#{run.id}</span>
                                                    <span
                                                        className={`status-pill ${
                                                            isMatched ? "pill-done" : "pill-warning"
                                                        }`}
                                                    >
                                                        {isMatched ? "✓ 수량 일치" : "⚠ 차이 발견"}
                                                    </span>
                                                    {isSelected && (
                                                        <span className="current-view-badge">
                                                            현재 상세 표시 중
                                                        </span>
                                                    )}
                                                </div>
                                                <div className="run-card-meta">
                                                    <span>
                                                        <strong>실행:</strong> {formatDateTime(run.executedAt)}
                                                    </span>
                                                    <span className="meta-divider" aria-hidden="true">
                                                        ·
                                                    </span>
                                                    <span>
                                                        <strong>스냅샷 기준:</strong>{" "}
                                                        {formatDateTime(run.snapshotSyncedAt)}
                                                    </span>
                                                </div>
                                                <div className="run-card-counts">
                                                    <span className="count-tag matched">
                                                        일치: {run.matchedCount}건
                                                    </span>
                                                    {run.quantityMismatchCount > 0 && (
                                                        <span className="count-tag mismatch">
                                                            수량 불일치: {run.quantityMismatchCount}건
                                                        </span>
                                                    )}
                                                    {run.onlyInBrokerCount > 0 && (
                                                        <span className="count-tag broker-only">
                                                            증권사에만: {run.onlyInBrokerCount}건
                                                        </span>
                                                    )}
                                                    {run.onlyInTradeGuideCount > 0 && (
                                                        <span className="count-tag tradeguide-only">
                                                            원장에만: {run.onlyInTradeGuideCount}건
                                                        </span>
                                                    )}
                                                </div>
                                            </div>
                                            <div className="run-card-arrow" aria-hidden="true">
                                                →
                                            </div>
                                        </button>
                                    </li>
                                );
                            })}
                        </ul>

                        {/* Server Pagination Controls */}
                        <nav
                            className="broker-order-items-pagination reconciliation-pagination"
                            aria-label="정합성 점검 이력 페이지 탐색"
                        >
                            <button
                                type="button"
                                className="secondary-button pagination-btn"
                                onClick={() => handlePageChange(Math.max(0, runsPage - 1))}
                                disabled={runsPage === 0 || isRunsLoading}
                                aria-label="이전 페이지"
                            >
                                이전
                            </button>
                            <div className="pagination-info" aria-live="polite">
                                <span className="pagination-page-number">
                                    <strong>{runsPage + 1}</strong> / {totalPages} 페이지
                                </span>
                                <span className="pagination-total-count">
                                    (총 {totalRuns.toLocaleString()}건)
                                </span>
                            </div>
                            <button
                                type="button"
                                className="secondary-button pagination-btn"
                                onClick={() => handlePageChange(runsPage + 1)}
                                disabled={!hasNextRuns || isRunsLoading}
                                aria-label="다음 페이지"
                            >
                                다음
                            </button>
                        </nav>
                    </>
                )}
            </div>
        </section>
    );
}
