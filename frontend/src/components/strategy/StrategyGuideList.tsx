import type {
    AssetStrategyGuide,
    EmptyHoldingsGuidance,
    StrategyAction,
    UnavailableAsset,
} from "../../types/strategyGuide";
import {formatUsd} from "../../utils/format";
import StrategyBacktestSection from "./StrategyBacktestSection";
import StrategyGuideEmptyHoldingsNotice from "./StrategyGuideEmptyHoldingsNotice";

const actionLabels: Record<StrategyAction, string> = {
    BUY: "매수 검토",
    HOLD: "보유 유지",
    REDUCE: "비중 축소 검토",
    SELL: "매도 검토",
    WATCH: "관찰",
};
const trendLabels = {
    ABOVE_LONG_AVERAGE: "장기 이동평균 위",
    BELOW_LONG_AVERAGE: "장기 이동평균 아래",
};
const signalEventLabels = {
    CROSS_UP: "상향 교차",
    CROSS_DOWN: "하향 교차",
    NONE: "교차 없음",
};
const entryTimingStatusLabels: Record<string, string> = {
    ELIGIBLE_NOW: "현재 검토 구간",
    WAIT: "관찰 대기",
    HOLDING: "보유 상태 검토",
    UNKNOWN: "정보 없음",
};
const stopLossStatusLabels: Record<string, string> = {
    NOT_CONFIGURED: "미설정",
    USER_DEFINED: "사용자 설정",
    STRATEGY_DEFINED: "전략 기준",
};

function getConfidenceLabel(confidence?: string | null): string {
    switch (confidence) {
        case "low-medium":
            return "낮음-중간";
        case "medium":
            return "중간";
        case "high":
            return "높음";
        case "low":
            return "낮음";
        default:
            return confidence ?? "정보 없음";
    }
}

type Props = {
    memberId?: number;
    portfolioId?: number;
    guides: AssetStrategyGuide[];
    unavailableAssets: UnavailableAsset[];
    emptyHoldingsGuidance?: EmptyHoldingsGuidance | null;
    emptyMessage: string;
    guideType?: "holding" | "candidate";
    onRetry?: () => void;
};

function renderReasonPill(reason?: string | null) {
    switch (reason) {
        case "ASSET_PROFILE_NOT_FOUND":
            return <span className="status-pill pill-warning">전략 프로필 미등록</span>;
        case "MARKET_DATA_RATE_LIMIT_EXCEEDED":
            return <span className="status-pill pill-warning">요청 제한 (Rate Limit)</span>;
        case "MARKET_DATA_UNAVAILABLE":
            return <span className="status-pill pill-neutral">시세 조회 실패</span>;
        default:
            return <span className="status-pill pill-neutral">조회 불가</span>;
    }
}

function getUnavailableItemMessage(asset: UnavailableAsset): string {
    switch (asset.reason) {
        case "ASSET_PROFILE_NOT_FOUND":
            return "이 종목에 적용할 전략 프로필이 등록되지 않았습니다. 하단 '보유 종목 투자 트랙 및 손절 기준 설정'에서 Track A를 설정해 주세요.";
        case "MARKET_DATA_RATE_LIMIT_EXCEEDED":
            return "선택한 시장 데이터 제공자의 요청 제한(Rate Limit)을 초과했습니다. 잠시 후 다시 시도해 주세요.";
        case "MARKET_DATA_UNAVAILABLE":
            return "외부 시장 데이터 제공자로부터 시세 데이터를 조회하지 못했습니다. 잠시 후 다시 시도해 주세요.";
        default:
            return asset.message;
    }
}

function getUnavailableSectionHeading(assets: UnavailableAsset[], isCandidate: boolean): string {
    if (assets.every((a) => a.reason === "ASSET_PROFILE_NOT_FOUND")) {
        return isCandidate
            ? "전략 프로필이 등록되지 않은 후보 종목"
            : "전략 프로필이 등록되지 않은 보유 종목";
    }
    if (assets.every((a) => a.reason === "MARKET_DATA_RATE_LIMIT_EXCEEDED")) {
        return "시장 데이터 요청 제한으로 조회가 지연된 종목";
    }
    if (assets.every((a) => a.reason === "MARKET_DATA_UNAVAILABLE")) {
        return "시세 데이터를 가져오지 못한 종목";
    }
    return isCandidate
        ? "일부 후보 종목의 가이드를 조회할 수 없습니다"
        : "일부 종목의 가이드를 조회할 수 없습니다";
}

function getUnavailableSectionDescription(assets: UnavailableAsset[], isCandidate: boolean): string {
    if (assets.some((a) => a.reason === "ASSET_PROFILE_NOT_FOUND")) {
        return isCandidate
            ? "후보 종목의 전략 프로필이 등록되지 않아 가이드를 계산할 수 없습니다."
            : "전략 프로필이 없는 종목은 하단 '보유 종목 투자 트랙 및 손절 기준 설정'에서 Track A를 지정해야 가이드가 계산됩니다.";
    }
    if (assets.every((a) => a.reason === "MARKET_DATA_RATE_LIMIT_EXCEEDED")) {
        return "외부 시세 제공자의 분당 호출 한도(8회)를 초과했습니다. 잠시 후(약 1분 뒤) 다시 시도해 주세요.";
    }
    if (assets.every((a) => a.reason === "MARKET_DATA_UNAVAILABLE")) {
        return "외부 시세 제공자의 응답 지연이나 일시적 오류일 수 있습니다. 잠시 후 다시 시도해 주세요.";
    }
    return isCandidate
        ? "후보 종목별 상태 사유를 확인하고 잠시 후 다시 시도해 주세요."
        : "종목별 상태 사유를 확인하고 프로필 등록 또는 시세 조회를 다시 시도해 주세요.";
}

export default function StrategyGuideList({
    memberId,
    portfolioId,
    guides,
    unavailableAssets,
    emptyHoldingsGuidance,
    emptyMessage,
    guideType = "holding",
    onRetry,
}: Props) {
    const isCandidate = guideType === "candidate";
    const hasMarketErrors = unavailableAssets.some(
        (a) =>
            a.reason === "MARKET_DATA_RATE_LIMIT_EXCEEDED" ||
            a.reason === "MARKET_DATA_UNAVAILABLE",
    );

    const renderEmptyState = () => {
        if (emptyHoldingsGuidance) {
            return <StrategyGuideEmptyHoldingsNotice guidance={emptyHoldingsGuidance} />;
        }

        if (unavailableAssets.length > 0) {
            const hasProfileMissing = unavailableAssets.some(
                (a) => a.reason === "ASSET_PROFILE_NOT_FOUND",
            );
            const allRateLimited = unavailableAssets.every(
                (a) => a.reason === "MARKET_DATA_RATE_LIMIT_EXCEEDED",
            );
            const allMarketUnavailable = unavailableAssets.every(
                (a) => a.reason === "MARKET_DATA_UNAVAILABLE",
            );

            if (!isCandidate && hasProfileMissing && !allRateLimited && !allMarketUnavailable) {
                return (
                    <div className="strategy-guide-empty-card" role="region" aria-label="전략 프로필 미등록 안내">
                        <div className="strategy-guide-empty-header">
                            <span className="status-pill pill-warning">전략 프로필 미등록</span>
                        </div>
                        <h3 className="strategy-guide-empty-title">보유 종목에 적용할 전략 프로필이 없습니다</h3>
                        <p className="strategy-guide-empty-message">
                            매매 원장에 등록된 보유 종목이 있으나, 적용 가능한 전략 프로필(Track A)이 설정되지 않아 전략 가이드를 계산할 수 없습니다.
                            아래 &apos;보유 종목 투자 트랙 및 손절 기준 설정&apos;에서 Track A를 설정해 주세요.
                        </p>
                        <div className="strategy-guide-empty-actions">
                            <a href="#held-asset-strategy-profiles" className="primary-button">
                                전략 프로필 설정으로 이동
                            </a>
                        </div>
                    </div>
                );
            }

            if (allRateLimited) {
                return (
                    <div className="strategy-guide-empty-card" role="region" aria-label="시장 데이터 요청 제한 안내">
                        <div className="strategy-guide-empty-header">
                            <span className="status-pill pill-warning">시장 데이터 요청 제한</span>
                        </div>
                        <h3 className="strategy-guide-empty-title">시장 데이터 요청 제한으로 가이드를 조회하지 못했습니다</h3>
                        <p className="strategy-guide-empty-message">
                            선택한 외부 시세 제공자의 분당 요청 한도(Rate Limit)를 초과했습니다. 잠시 후 다시 시도해 주세요.
                        </p>
                        {onRetry ? (
                            <div className="strategy-guide-empty-actions">
                                <button type="button" className="primary-button" onClick={onRetry}>
                                    {isCandidate ? "후보 가이드 다시 시도" : "보유 종목 가이드 다시 시도"}
                                </button>
                            </div>
                        ) : null}
                    </div>
                );
            }

            if (allMarketUnavailable) {
                return (
                    <div className="strategy-guide-empty-card" role="region" aria-label="시세 데이터 오류 안내">
                        <div className="strategy-guide-empty-header">
                            <span className="status-pill pill-neutral">시세 데이터 오류</span>
                        </div>
                        <h3 className="strategy-guide-empty-title">시장 데이터를 가져오지 못해 가이드를 표시할 수 없습니다</h3>
                        <p className="strategy-guide-empty-message">
                            외부 시세 데이터 제공자 통신 오류로 가격 정보를 조회하지 못했습니다. 잠시 후 다시 시도해 주세요.
                        </p>
                        {onRetry ? (
                            <div className="strategy-guide-empty-actions">
                                <button type="button" className="primary-button" onClick={onRetry}>
                                    {isCandidate ? "후보 가이드 다시 시도" : "보유 종목 가이드 다시 시도"}
                                </button>
                            </div>
                        ) : null}
                    </div>
                );
            }

            return (
                <div className="strategy-guide-empty-card" role="region" aria-label="전략 가이드 계산 불가 안내">
                    <div className="strategy-guide-empty-header">
                        <span className="status-pill pill-warning">가이드 계산 불가</span>
                    </div>
                    <h3 className="strategy-guide-empty-title">
                        {isCandidate
                            ? "후보 종목의 시세 데이터를 조회하지 못했습니다"
                            : "보유 종목의 전략 가이드를 계산하지 못했습니다"}
                    </h3>
                    <p className="strategy-guide-empty-message">
                        {isCandidate
                            ? "외부 시장 데이터 요청 제한 또는 제공자 통신 오류로 후보 가이드를 계산할 수 없습니다."
                            : "전략 프로필 미등록 또는 시장 데이터 조회 오류로 가이드를 생성하지 못했습니다. 아래 종목별 상태를 확인해 주세요."}
                    </p>
                    <div className="strategy-guide-empty-actions">
                        {!isCandidate && hasProfileMissing ? (
                            <a href="#held-asset-strategy-profiles" className="primary-button">
                                전략 프로필 설정으로 이동
                            </a>
                        ) : null}
                        {onRetry ? (
                            <button
                                type="button"
                                className={!isCandidate && hasProfileMissing ? "secondary-button" : "primary-button"}
                                onClick={onRetry}
                            >
                                {isCandidate ? "후보 가이드 다시 시도" : "보유 종목 가이드 다시 시도"}
                            </button>
                        ) : null}
                    </div>
                </div>
            );
        }

        return <p className="empty-state">{emptyMessage}</p>;
    };

    return (
        <>
            {guides.length === 0 ? (
                renderEmptyState()
            ) : (
                <ul className="guide-list">
                    {guides.map(({market, ticker, displayName, decision}) => {
                        const caveats = decision.metadata.caveats ?? [];
                        const hasEvidence = Boolean(decision.metadata.confidence) || caveats.length > 0;
                        const guidance = decision.guidance ?? {
                            entryTimingStatus: "UNKNOWN",
                            entryTimingMessage: "매수 시점 정보를 확인할 수 없습니다.",
                            stopLossStatus: "NOT_CONFIGURED",
                            stopLossRatio: null,
                            stopLossPrice: null,
                            stopLossMessage: "검증된 손절 규칙이 설정되지 않아 손절가를 자동 산출하지 않습니다.",
                        };

                        return (
                            <li key={`${market}-${ticker}`} className="guide-card">
                            <div className="guide-card-heading">
                                <div className="guide-asset">
                                    {displayName && displayName !== ticker ? (
                                        <div className="broker-snapshot-item-identity">
                                            <strong className="broker-snapshot-item-name">{displayName}</strong>
                                            <div className="broker-snapshot-item-symbol">
                                                <span className="market-badge">{market}</span>
                                                <span className="broker-ticker">{ticker}</span>
                                                {isCandidate ? (
                                                    <span className="candidate-track-badge">Track A 후보 (미보유)</span>
                                                ) : null}
                                            </div>
                                        </div>
                                    ) : (
                                        <>
                                            <span className="market-badge">{market}</span>
                                            <strong>{ticker}</strong>
                                            {isCandidate ? (
                                                <span className="candidate-track-badge">Track A 후보 (미보유)</span>
                                            ) : null}
                                        </>
                                    )}
                                </div>
                                <span className={`action-badge ${decision.action.toLowerCase()}`}>
                                     {actionLabels[decision.action]}
                                 </span>
                            </div>
                            <p className="guide-reason">{decision.reason}</p>
                            <dl className="guide-details">
                                <div><dt>기준 가격</dt><dd>{formatUsd(decision.referencePrice)}</dd></div>
                                <div><dt>데이터 기준일</dt><dd>{decision.metadata.dataAsOf}</dd></div>
                                <div><dt>추세</dt><dd>{decision.trend ? trendLabels[decision.trend] : "정보 없음"}</dd></div>
                                <div><dt>교차 상태</dt><dd>{decision.signalEvent ? signalEventLabels[decision.signalEvent] : "정보 없음"}</dd></div>
                                <div>
                                    <dt>{isCandidate ? "신호 기준 (완료 주봉)" : "전략"}</dt>
                                    <dd>
                                        {decision.metadata.strategyId === "track-a-weekly-ma-crossover"
                                            ? `완료 주봉 10/40 MA (${decision.metadata.strategyVersion.startsWith("v") ? decision.metadata.strategyVersion : `v${decision.metadata.strategyVersion}`})`
                                            : `${decision.metadata.strategyId} ${decision.metadata.strategyVersion.startsWith("v") ? decision.metadata.strategyVersion : `v${decision.metadata.strategyVersion}`}`}
                                    </dd>
                                </div>
                                {decision.weeksSinceCross !== null ? <div><dt>최근 교차 후</dt><dd>{decision.weeksSinceCross}주</dd></div> : null}
                            </dl>
                            <dl className="guide-decision-guidance">
                                <div>
                                    <dt>매수 시점</dt>
                                    <dd>
                                        <strong>{entryTimingStatusLabels[guidance.entryTimingStatus] ?? guidance.entryTimingStatus}</strong>
                                        <span>{guidance.entryTimingMessage}</span>
                                    </dd>
                                </div>
                                <div>
                                    <dt>손절 가이드</dt>
                                    <dd>
                                        <strong>{guidance.stopLossPrice !== null
                                            ? `${formatUsd(guidance.stopLossPrice)}${guidance.stopLossRatio !== null ? ` (${(guidance.stopLossRatio * 100).toFixed(2)}%)` : ""}`
                                            : stopLossStatusLabels[guidance.stopLossStatus] ?? "미설정"}</strong>
                                        <span>{guidance.stopLossMessage}</span>
                                    </dd>
                                </div>
                            </dl>
                            {hasEvidence ? (
                                <section className="guide-evidence" aria-label="전략 근거 및 주의사항">
                                    {decision.metadata.confidence ? (
                                        <div className="guide-confidence">
                                            <span>가이드 신뢰도</span>
                                            <strong>{getConfidenceLabel(decision.metadata.confidence)}</strong>
                                        </div>
                                    ) : null}
                                    {caveats.length > 0 ? (
                                        <div className="guide-caveats">
                                            <span className="guide-caveats-title">확인할 사항</span>
                                            <ul>
                                                {caveats.map((caveat) => <li key={caveat}>{caveat}</li>)}
                                            </ul>
                                        </div>
                                    ) : null}
                                </section>
                            ) : null}
                            {isCandidate ? (
                                <div className="candidate-card-footer">
                                    <span className="candidate-card-disclaimer">
                                        완료 주봉 10주/40주 이동평균 기반 기술적 신호이며, 투자 권유나 자동 주문이 아닌 단순 검토용 정보입니다.
                                    </span>
                                </div>
                            ) : null}
                            {memberId !== undefined && portfolioId !== undefined ? (
                                <StrategyBacktestSection
                                    memberId={memberId}
                                    portfolioId={portfolioId}
                                    market={market}
                                    ticker={ticker}
                                    strategyId={decision.metadata.strategyId}
                                />
                                ) : null}
                            </li>
                        );
                    })}
                </ul>
            )}
            {unavailableAssets.length > 0 ? (
                <section
                    className="unavailable-assets"
                    aria-label={isCandidate ? "조회할 수 없는 후보 종목" : "조회할 수 없는 종목"}
                >
                    <div className="unavailable-assets-header">
                        <div>
                            <h3>{getUnavailableSectionHeading(unavailableAssets, isCandidate)}</h3>
                            <p className="unavailable-assets-desc">
                                {getUnavailableSectionDescription(unavailableAssets, isCandidate)}
                            </p>
                        </div>
                        {hasMarketErrors && onRetry ? (
                            <button
                                type="button"
                                className="retry-button unavailable-retry-btn"
                                onClick={onRetry}
                                aria-label={isCandidate ? "후보 가이드 다시 시도" : "보유 종목 가이드 다시 시도"}
                            >
                                다시 시도
                            </button>
                        ) : null}
                    </div>
                    <ul className="unavailable-assets-list">
                        {unavailableAssets.map((asset) => (
                            <li key={`${asset.market}-${asset.ticker}`} className="unavailable-asset-item">
                                <div className="unavailable-asset-info">
                                    <div className="unavailable-asset-title-row">
                                        {renderReasonPill(asset.reason)}
                                        <span className="market-badge">{asset.market}</span>
                                        <strong className="unavailable-asset-ticker">{asset.ticker}</strong>
                                    </div>
                                    <p className="unavailable-asset-message">
                                        {getUnavailableItemMessage(asset)}
                                    </p>
                                </div>
                                <div className="unavailable-asset-action">
                                    {asset.reason === "ASSET_PROFILE_NOT_FOUND" && !isCandidate ? (
                                        <a href="#held-asset-strategy-profiles" className="quiet-action">
                                            프로필 설정 →
                                        </a>
                                    ) : (asset.reason === "MARKET_DATA_RATE_LIMIT_EXCEEDED" ||
                                         asset.reason === "MARKET_DATA_UNAVAILABLE") && onRetry ? (
                                        <button
                                            type="button"
                                            className="quiet-action"
                                            onClick={onRetry}
                                        >
                                            다시 시도
                                        </button>
                                    ) : null}
                                </div>
                            </li>
                        ))}
                    </ul>
                </section>
            ) : null}
        </>
    );
}
