import {Link} from "react-router-dom";
import RequestError from "../common/RequestError";
import {formatUsd} from "../../utils/format";
import type {
    AssetTradePlanPreview,
    PlannedTradeAction,
    PlannedTradeActionType,
    TradePlanPreviewBatch,
    TradePlanPreviewConstraint,
} from "../../types/tradePlanPreview";

type Props = {
    memberId: number;
    portfolioId: number;
    resource: {
        data: TradePlanPreviewBatch | null;
        error: Error | null;
        isLoading: boolean;
        refresh: () => void;
    };
};

function formatPlanPrice(value: number | null | undefined, currency = "USD"): string {
    if (value == null) return "-";
    if (currency === "USD") return formatUsd(value);
    return `${value.toLocaleString()} ${currency}`;
}

function scrollToHeldAssetProfiles(event: React.MouseEvent<HTMLAnchorElement>) {
    event.preventDefault();
    const element = document.getElementById("held-asset-strategy-profiles");
    if (element) {
        element.scrollIntoView({behavior: "smooth", block: "start"});
        window.history.replaceState(null, "", "#held-asset-strategy-profiles");
    }
}

export default function TradePlanPreviewSection({resource}: Props) {
    const batch = resource.data;
    const candidatePlans = batch?.candidatePlans ?? [];
    const heldAssetPlans = batch?.heldAssetPlans ?? [];
    const unavailableAssets = batch?.unavailableAssets ?? [];

    const profileMissingAssets = unavailableAssets.filter(
        (asset) =>
            asset.reason === "ASSET_PROFILE_NOT_FOUND" ||
            (asset.reason && asset.reason.includes("PROFILE")),
    );
    const otherUnavailableAssets = unavailableAssets.filter(
        (asset) =>
            asset.reason !== "ASSET_PROFILE_NOT_FOUND" &&
            (!asset.reason || !asset.reason.includes("PROFILE")),
    );

    const hasAnyPlans = candidatePlans.length > 0 || heldAssetPlans.length > 0;

    return (
        <section
            className="content-section trade-plan-preview-section"
            aria-labelledby="trade-plan-preview-title"
        >
            <div className="section-heading">
                <div className="section-title-wrap">
                    <p className="section-label">TRADE PLAN PREVIEW</p>
                    <div className="section-title-row">
                        <h2 id="trade-plan-preview-title">매매 계획 초안</h2>
                        <span className="trade-plan-scope-badge">검토 전용 · 자동 주문 없음</span>
                    </div>
                </div>
            </div>

            <p className="section-description">
                전략 기준 가격과 위험 한도를 바탕으로 산출한 검토용 매매 계획입니다.
                기준가는 실시간 호가가 아닌 전략 기준 가격이며, 증권사 주문을 자동 생성하거나 전송하지 않습니다.
                모든 수치는 실제 주문 등록 전 최종 사용자 확인이 필요합니다.
            </p>

            <div className="trade-plan-disclaimer" role="note" aria-label="매매 계획 초안 검토 원칙">
                <p>
                    <strong>검토 원칙:</strong> 본 계획 초안은 사전 기술적 분석에 따른 참고 수치이며,
                    실제 체결가·체결 수량과 다를 수 있습니다. 증권사 예약/실시간 주문은 사용자가 직접 검토 후 실행해야 합니다.
                </p>
            </div>

            {resource.isLoading && !batch ? (
                <p className="status-message" aria-live="polite">
                    검토용 매매 계획을 불러오는 중입니다.
                </p>
            ) : null}

            {resource.error && !batch ? (
                <RequestError
                    message={resource.error.message}
                    onRetry={resource.refresh}
                    retryLabel="매매 계획 다시 시도"
                />
            ) : null}

            {profileMissingAssets.length > 0 ? (
                <div className="trade-plan-profile-notice" role="status" aria-label="전략 프로필 설정 필요">
                    <div className="trade-plan-profile-notice-text">
                        <span className="trade-plan-profile-badge">전략 프로필 설정 필요</span>
                        <p>
                            {profileMissingAssets.map((asset) => asset.ticker).join(", ")}
                            ({profileMissingAssets.length}개 종목)은 전략 프로필이 설정되지 않아 계획 계산에서 제외되었습니다.
                        </p>
                    </div>
                    <a
                        href="#held-asset-strategy-profiles"
                        className="quiet-action trade-plan-profile-link"
                        onClick={scrollToHeldAssetProfiles}
                    >
                        보유 종목 설정으로 이동
                    </a>
                </div>
            ) : null}

            {otherUnavailableAssets.length > 0 ? (
                <p className="trade-plan-other-notice">
                    {otherUnavailableAssets.length}개 종목은 시세 데이터 제한 등으로 이번 계획 산출에 포함되지 않았습니다.
                </p>
            ) : null}

            {batch && !hasAnyPlans ? (
                <p className="empty-state">현재 검토 가능한 매매 계획 초안이 없습니다.</p>
            ) : null}

            {candidatePlans.length > 0 ? (
                <div className="trade-plan-group">
                    <h3 className="trade-plan-group-title">
                        Track A 후보 매수 계획 ({candidatePlans.length})
                    </h3>
                    <ul className="trade-plan-list" aria-label="Track A 후보 매수 계획 목록">
                        {candidatePlans.map((plan) => (
                            <TradePlanCard
                                key={`candidate-${plan.market}-${plan.ticker}`}
                                plan={plan}
                                isHeld={false}
                            />
                        ))}
                    </ul>
                </div>
            ) : null}

            {heldAssetPlans.length > 0 ? (
                <div className="trade-plan-group">
                    <h3 className="trade-plan-group-title">
                        보유 종목 점검 계획 ({heldAssetPlans.length})
                    </h3>
                    <ul className="trade-plan-list" aria-label="보유 종목 점검 계획 목록">
                        {heldAssetPlans.map((plan) => (
                            <TradePlanCard
                                key={`held-${plan.market}-${plan.ticker}`}
                                plan={plan}
                                isHeld={true}
                            />
                        ))}
                    </ul>
                </div>
            ) : null}
        </section>
    );
}

function TradePlanCard({
    plan,
    isHeld,
}: {
    plan: AssetTradePlanPreview;
    isHeld: boolean;
}) {
    const isBuy = plan.status === "BUY";
    const isStopLossExit = plan.status === "STOP_LOSS_EXIT_REVIEW";
    const isSellReview = plan.status === "SELL_REVIEW";
    const isHold = plan.status === "HOLD";
    const isPossibleAddReview = plan.status === "POSSIBLE_ADD_REVIEW";
    const isWatch = plan.status === "WATCH";
    const isNotReady = plan.status === "NOT_READY";

    const actionBadgeClass = isBuy
        ? "buy"
        : isPossibleAddReview
            ? "add"
            : isStopLossExit || isSellReview
                ? "sell"
                : isHold
                    ? "hold"
                    : isWatch
                        ? "watch"
                        : "not-ready";

    const actionLabel = isBuy
        ? "매수 검토"
        : isStopLossExit
            ? "손절 검토"
            : isSellReview
                ? "매도 검토"
                : isHold
                    ? "보유"
                    : isPossibleAddReview
                        ? "추가 매수 검토"
                        : isWatch
                            ? "관찰"
                            : "계획 산출 대기";

    const hasCompleteAddPlan =
        plan.referencePrice != null && plan.quantity != null && plan.amount != null;

    return (
        <li className="trade-plan-card">
            <div className="trade-plan-card-header">
                <div className="trade-plan-asset">
                    <span className="market-badge">{plan.market}</span>
                    <strong className="trade-plan-ticker">{plan.ticker}</strong>
                    <span className="trade-plan-scope-tag">
                        {isHeld ? "보유 종목" : "Track A 후보"}
                    </span>
                </div>
                <span className={`action-badge ${actionBadgeClass}`}>
                    {actionLabel}
                </span>
            </div>

            <p className="trade-plan-reason">{plan.reason}</p>

            {isStopLossExit ? (
                <div className="trade-plan-stop-loss-alert" role="alert">
                    <div className="trade-plan-stop-loss-title">
                        현재 보유 수량 전량
                        {plan.quantity != null ? `(${plan.quantity}주)` : ""} 손절 검토
                    </div>
                    <p className="trade-plan-stop-loss-desc">
                        전략 기준 가격({formatPlanPrice(plan.referencePrice, plan.currency)})이
                        설정된 손절가({formatPlanPrice(plan.stopLossPrice, plan.currency)}) 이하로 하락했습니다.
                        증권사 계좌에서 실제 보유 수량 전량의 손절 매도를 직접 검토해 주세요.
                    </p>
                </div>
            ) : null}

            {isBuy ? (
                <>
                    <dl className="trade-plan-details">
                        <div>
                            <dt>기준 가격</dt>
                            <dd>{formatPlanPrice(plan.referencePrice, plan.currency)}</dd>
                        </div>
                        <div>
                            <dt>사용자 손절가</dt>
                            <dd>{formatPlanPrice(plan.stopLossPrice, plan.currency)}</dd>
                        </div>
                        <div>
                            <dt>최대 검토 금액</dt>
                            <dd>{formatPlanPrice(plan.amount, plan.currency)}</dd>
                        </div>
                        <div>
                            <dt>추정 수량</dt>
                            <dd>{plan.quantity != null ? `${plan.quantity}주` : "-"}</dd>
                        </div>
                        <div>
                            <dt>예상 최대 손실</dt>
                            <dd className="trade-plan-loss-value">
                                {plan.estimatedMaxLoss != null
                                    ? `-${formatPlanPrice(plan.estimatedMaxLoss, plan.currency)}`
                                    : "-"}
                            </dd>
                        </div>
                    </dl>

                    {plan.constraints && plan.constraints.length > 0 ? (
                        <div className="trade-plan-constraints-box">
                            <div className="trade-plan-constraints-tags">
                                {plan.constraints.map((constraint) => (
                                    <span
                                        key={constraint}
                                        className={`trade-plan-constraint-badge ${
                                            constraint === "AVAILABLE_CASH_NOT_SYNCED"
                                                ? "cash-not-synced"
                                                : "exposure-cap"
                                        }`}
                                    >
                                        {formatConstraintLabel(constraint)}
                                    </span>
                                ))}
                            </div>
                            {plan.constraints.includes("AVAILABLE_CASH_NOT_SYNCED") ? (
                                <p className="trade-plan-constraint-note">
                                    증권사 가용 현금 잔고가 자동 연동되지 않아 이 금액의 실제 매수 가능 여부는 계좌에서 직접 확인해야 합니다.
                                </p>
                            ) : null}
                        </div>
                    ) : null}
                </>
            ) : null}

            {isPossibleAddReview ? (
                <>
                    <dl className="trade-plan-details">
                        <div>
                            <dt>기준 가격</dt>
                            <dd>{formatPlanPrice(plan.referencePrice, plan.currency)}</dd>
                        </div>
                        <div>
                            <dt>사용자 손절가</dt>
                            <dd>{formatPlanPrice(plan.stopLossPrice, plan.currency)}</dd>
                        </div>
                        <div>
                            <dt>최대 검토 금액</dt>
                            <dd>
                                {hasCompleteAddPlan
                                    ? formatPlanPrice(plan.amount, plan.currency)
                                    : "수치 없음 (추가 산출 대기)"}
                            </dd>
                        </div>
                        <div>
                            <dt>추정 추가 수량</dt>
                            <dd>
                                {hasCompleteAddPlan
                                    ? `${plan.quantity}주`
                                    : "수치 없음 (추가 산출 대기)"}
                            </dd>
                        </div>
                        {plan.estimatedMaxLoss != null ? (
                            <div>
                                <dt>예상 최대 손실</dt>
                                <dd className="trade-plan-loss-value">
                                    -{formatPlanPrice(plan.estimatedMaxLoss, plan.currency)}
                                </dd>
                            </div>
                        ) : null}
                    </dl>

                    {plan.constraints && plan.constraints.length > 0 ? (
                        <div className="trade-plan-constraints-box">
                            <div className="trade-plan-constraints-tags">
                                {plan.constraints.map((constraint) => (
                                    <span
                                        key={constraint}
                                        className={`trade-plan-constraint-badge ${
                                            constraint === "AVAILABLE_CASH_NOT_SYNCED"
                                                ? "cash-not-synced"
                                                : "exposure-cap"
                                        }`}
                                    >
                                        {formatConstraintLabel(constraint)}
                                    </span>
                                ))}
                            </div>
                            {plan.constraints.includes("AVAILABLE_CASH_NOT_SYNCED") ? (
                                <p className="trade-plan-constraint-note">
                                    증권사 가용 현금 잔고가 자동 연동되지 않아 이 금액의 실제 매수 가능 여부는 계좌에서 직접 확인해야 합니다.
                                </p>
                            ) : null}
                        </div>
                    ) : null}
                </>
            ) : null}

            {isStopLossExit ? (
                <dl className="trade-plan-details">
                    <div>
                        <dt>기준 가격</dt>
                        <dd>{formatPlanPrice(plan.referencePrice, plan.currency)}</dd>
                    </div>
                    <div>
                        <dt>손절 기준가</dt>
                        <dd>{formatPlanPrice(plan.stopLossPrice, plan.currency)}</dd>
                    </div>
                    <div>
                        <dt>검토 대상 수량</dt>
                        <dd>전량 {plan.quantity != null ? `(${plan.quantity}주)` : ""}</dd>
                    </div>
                </dl>
            ) : null}

            {isSellReview || isHold ? (
                <dl className="trade-plan-details">
                    <div>
                        <dt>기준 가격</dt>
                        <dd>{formatPlanPrice(plan.referencePrice, plan.currency)}</dd>
                    </div>
                    {plan.stopLossPrice != null ? (
                        <div>
                            <dt>손절 기준가</dt>
                            <dd>{formatPlanPrice(plan.stopLossPrice, plan.currency)}</dd>
                        </div>
                    ) : null}
                </dl>
            ) : null}

            {isWatch ? (
                <dl className="trade-plan-details">
                    <div>
                        <dt>기준 가격</dt>
                        <dd>{formatPlanPrice(plan.referencePrice, plan.currency)}</dd>
                    </div>
                </dl>
            ) : null}

            {isNotReady ? (
                <div className="trade-plan-not-ready-block">
                    <dl className="trade-plan-details">
                        <div>
                            <dt>기준 가격</dt>
                            <dd>{formatPlanPrice(plan.referencePrice, plan.currency)}</dd>
                        </div>
                        <div>
                            <dt>산출 대기 사유</dt>
                            <dd>
                                {plan.notReadyReason === "MISSING_RISK_POLICY"
                                    ? "위험 한도 미설정"
                                    : plan.notReadyReason === "MISSING_STOP_LOSS"
                                        ? "손절 기준 미설정"
                                        : "설정 필요"}
                            </dd>
                        </div>
                    </dl>

                    <div className="trade-plan-not-ready-action">
                        {plan.notReadyReason === "MISSING_RISK_POLICY" ? (
                            <Link to="/settings#risk-policy" className="quiet-action">
                                포트폴리오 위험 한도 설정으로 이동
                            </Link>
                        ) : plan.notReadyReason === "MISSING_STOP_LOSS" ? (
                            <Link to="/settings#risk-policy" className="quiet-action">
                                포트폴리오 손절 기준 비율 설정으로 이동
                            </Link>
                        ) : null}
                    </div>
                </div>
            ) : null}

            {isHeld && plan.plannedActions && plan.plannedActions.length > 0 ? (
                <div className="trade-plan-actions-container">
                    <div className="trade-plan-actions-header">
                        <h4 className="trade-plan-actions-title">예약 주문 검토 단계</h4>
                        <span className="trade-plan-actions-count">
                            총 {plan.plannedActions.length}단계
                        </span>
                    </div>
                    <ol
                        className="trade-plan-actions-list"
                        aria-label={`${plan.ticker} 예약 주문 검토 단계`}
                    >
                        {plan.plannedActions.map((action, idx) => (
                            <PlannedTradeActionItem
                                key={`${action.type}-${idx}`}
                                action={action}
                                stepIndex={idx + 1}
                                currency={plan.currency}
                            />
                        ))}
                    </ol>
                </div>
            ) : null}
        </li>
    );
}

function getPlannedActionTypeLabel(type: PlannedTradeActionType): string {
    switch (type) {
        case "PROTECTIVE_EXIT_REVIEW":
            return "손절 청산 검토";
        case "POSSIBLE_ADD_REVIEW":
            return "추가 매수 검토";
        case "PARTIAL_PROFIT_REVIEW":
            return "부분 익절 검토";
        default:
            return "주문 검토";
    }
}

function PlannedTradeActionItem({
    action,
    stepIndex,
    currency,
}: {
    action: PlannedTradeAction;
    stepIndex: number;
    currency: string;
}) {
    const isProtectiveExit = action.type === "PROTECTIVE_EXIT_REVIEW";
    const isPossibleAdd = action.type === "POSSIBLE_ADD_REVIEW";
    const isPartialProfit = action.type === "PARTIAL_PROFIT_REVIEW";

    const typeBadgeClass = isProtectiveExit
        ? "exit"
        : isPossibleAdd
            ? "add"
            : "profit";

    const typeLabel = getPlannedActionTypeLabel(action.type);

    const hasCompleteAddValues =
        action.triggerPrice != null && action.quantity != null && action.amount != null;
    const hasProfitValues = action.triggerPrice != null && action.quantity != null;

    return (
        <li className={`trade-plan-action-item action-type-${typeBadgeClass}`}>
            <div className="trade-plan-action-header">
                <div className="trade-plan-action-title-group">
                    <span className="trade-plan-action-step-badge">{stepIndex}단계</span>
                    <span className={`trade-plan-action-badge ${typeBadgeClass}`}>
                        {typeLabel}
                    </span>
                </div>
                {isProtectiveExit ? (
                    <span className="trade-plan-action-tag manual">
                        자동 주문 없음 · 직접 확인
                    </span>
                ) : isPossibleAdd ? (
                    hasCompleteAddValues ? (
                        <span className="trade-plan-action-tag manual">
                            자동 주문 없음 · 직접 확인
                        </span>
                    ) : (
                        <span className="trade-plan-action-tag pending">
                            수치 없음 · 추가 산출 대기
                        </span>
                    )
                ) : isPartialProfit ? (
                    hasProfitValues ? (
                        <span className="trade-plan-action-tag manual">
                            자동 주문 없음 · 직접 확인
                        </span>
                    ) : (
                        <span className="trade-plan-action-tag pending">
                            목표가 규칙 검증 전 수치 미제공
                        </span>
                    )
                ) : null}
            </div>

            <dl className="trade-plan-action-details">
                {isProtectiveExit ? (
                    <>
                        <div>
                            <dt>사용자 손절가</dt>
                            <dd>{formatPlanPrice(action.triggerPrice, currency)}</dd>
                        </div>
                        <div>
                            <dt>검토 수량</dt>
                            <dd>전량 {action.quantity != null ? `(${action.quantity}주)` : ""}</dd>
                        </div>
                        <div>
                            <dt>예상 청산 금액</dt>
                            <dd>{formatPlanPrice(action.amount, currency)}</dd>
                        </div>
                        <div>
                            <dt>주문 방식</dt>
                            <dd className="trade-plan-manual-text">
                                자동 주문 없음 · 직접 확인
                            </dd>
                        </div>
                    </>
                ) : null}

                {isPossibleAdd ? (
                    hasCompleteAddValues ? (
                        <>
                            <div>
                                <dt>검토 가격</dt>
                                <dd>{formatPlanPrice(action.triggerPrice, currency)}</dd>
                            </div>
                            <div>
                                <dt>추가 검토 수량</dt>
                                <dd>{action.quantity}주</dd>
                            </div>
                            <div>
                                <dt>최대 검토 금액</dt>
                                <dd>{formatPlanPrice(action.amount, currency)}</dd>
                            </div>
                            <div>
                                <dt>주문 방식</dt>
                                <dd className="trade-plan-manual-text">
                                    자동 주문 없음 · 직접 확인
                                </dd>
                            </div>
                        </>
                    ) : (
                        <>
                            <div>
                                <dt>검토 가격</dt>
                                <dd>
                                    {action.triggerPrice != null
                                        ? formatPlanPrice(action.triggerPrice, currency)
                                        : "수치 없음 (추가 산출 대기)"}
                                </dd>
                            </div>
                            <div>
                                <dt>추가 검토 수량</dt>
                                <dd className="trade-plan-pending-text">
                                    수치 없음 (추가 산출 대기)
                                </dd>
                            </div>
                            <div>
                                <dt>최대 검토 금액</dt>
                                <dd className="trade-plan-pending-text">
                                    수치 없음 (추가 산출 대기)
                                </dd>
                            </div>
                            <div>
                                <dt>산출 상태</dt>
                                <dd className="trade-plan-pending-text">
                                    수치 없음 · 추가 산출 대기
                                </dd>
                            </div>
                        </>
                    )
                ) : null}

                {isPartialProfit ? (
                    hasProfitValues ? (
                        <>
                            <div>
                                <dt>목표 가격</dt>
                                <dd>{formatPlanPrice(action.triggerPrice, currency)}</dd>
                            </div>
                            <div>
                                <dt>검토 수량</dt>
                                <dd>{action.quantity}주</dd>
                            </div>
                            {action.amount != null ? (
                                <div>
                                    <dt>예상 익절 금액</dt>
                                    <dd>{formatPlanPrice(action.amount, currency)}</dd>
                                </div>
                            ) : null}
                            <div>
                                <dt>주문 방식</dt>
                                <dd className="trade-plan-manual-text">
                                    자동 주문 없음 · 직접 확인
                                </dd>
                            </div>
                        </>
                    ) : (
                        <>
                            <div>
                                <dt>목표 가격</dt>
                                <dd className="trade-plan-pending-text">
                                    목표가 규칙 검증 전 수치 미제공
                                </dd>
                            </div>
                            <div>
                                <dt>검토 수량</dt>
                                <dd className="trade-plan-pending-text">
                                    목표가 규칙 검증 전 수치 미제공
                                </dd>
                            </div>
                            <div>
                                <dt>익절 비율</dt>
                                <dd className="trade-plan-pending-text">
                                    규칙 검증 전 수치 미제공
                                </dd>
                            </div>
                            <div>
                                <dt>주문 방식</dt>
                                <dd className="trade-plan-manual-text">
                                    자동 주문 없음 · 직접 확인
                                </dd>
                            </div>
                        </>
                    )
                ) : null}
            </dl>

            <p className="trade-plan-action-reason">{action.reason}</p>
        </li>
    );
}

function formatConstraintLabel(constraint: TradePlanPreviewConstraint): string {
    switch (constraint) {
        case "AVAILABLE_CASH_NOT_SYNCED":
            return "가용 현금 미연동";
        case "SINGLE_ASSET_EXPOSURE_CAP_APPLIED":
            return "종목 한도 적용";
        default:
            return "제약 사항 적용";
    }
}
