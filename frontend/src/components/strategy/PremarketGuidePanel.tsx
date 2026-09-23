import {useState} from "react";
import {
    getApiRetryAfterSeconds,
    isMarketDataRateLimitExceeded,
} from "../../api/apiError";
import RequestError from "../common/RequestError";
import MarketDataRateLimitNotice from "../valuation/MarketDataRateLimitNotice";
import AccordionSection from "./AccordionSection";
import {generateTodayPremarketGuide} from "../../api/premarketGuideApi";
import type {PremarketGuide} from "../../types/premarketGuide";

type Props = {
    memberId: number;
    portfolioId: number;
    resource: {
        data: PremarketGuide | null;
        error: Error | null;
        isLoading: boolean;
        refresh: () => void;
        replaceData: (data: PremarketGuide) => void;
    };
};

export default function PremarketGuidePanel({memberId, portfolioId, resource}: Props) {
    const [isGenerating, setIsGenerating] = useState(false);
    const [generationError, setGenerationError] = useState<Error | null>(null);
    const guide = resource.data;
    const isResourceRateLimit = isMarketDataRateLimitExceeded(resource.error);
    const resourceRetryAfterSeconds = getApiRetryAfterSeconds(resource.error);
    const isGenerationRateLimit = isMarketDataRateLimitExceeded(generationError);
    const generationRetryAfterSeconds = getApiRetryAfterSeconds(generationError);

    const generate = async (force: boolean) => {
        setIsGenerating(true);
        setGenerationError(null);
        try {
            const generated = await generateTodayPremarketGuide(memberId, portfolioId, force);
            resource.replaceData(generated);
        } catch (reason: unknown) {
            setGenerationError(
                reason instanceof Error ? reason : new Error("오늘 장전 가이드를 생성하지 못했습니다."),
            );
        } finally {
            setIsGenerating(false);
        }
    };

    const statusLabel = guide?.status === "COMPLETED"
        ? "생성 완료"
        : guide?.status === "PARTIAL"
            ? "일부 조회 불가"
            : "생성 전";
    const statusClass = guide?.status === "PARTIAL"
        ? "partial"
        : guide?.status === "COMPLETED"
            ? "completed"
            : "not-generated";
    const dataAsOfLabel = guide?.dataAsOfFrom && guide.dataAsOfTo
        ? guide.dataAsOfFrom === guide.dataAsOfTo
            ? guide.dataAsOfFrom
            : `${guide.dataAsOfFrom} ~ ${guide.dataAsOfTo}`
        : "아직 생성되지 않음";
    const guideRows = [
        ...(guide?.heldGuides ?? []).map((item) => ({...item, scopeLabel: "보유"})),
        ...(guide?.candidateGuides ?? []).map((item) => ({...item, scopeLabel: "후보"})),
    ].slice(0, 8);
    const unavailableMessage = getUnavailableMessage(guide?.unavailableAssets ?? []);

    return (
        <AccordionSection
            id="premarket-guide"
            className="premarket-guide-panel"
            sectionLabel="DAILY PRE-MARKET"
            title="오늘 장전 가이드"
            defaultOpen={true}
            summary={
                <span className={`premarket-guide-status ${statusClass}`}>
                    {statusLabel}
                </span>
            }
            headerExtra={
                <button
                    type="button"
                    className="quiet-action"
                    onClick={() => void generate(guide?.status !== "NOT_GENERATED")}
                    disabled={isGenerating || resource.isLoading}
                >
                    {isGenerating
                        ? "가이드 생성 중..."
                        : guide?.status === "NOT_GENERATED"
                            ? "오늘 가이드 생성"
                            : "오늘 가이드 다시 생성"}
                </button>
            }
        >
            <p className="premarket-guide-description">
                오늘 장 시작 전 확인할 Track A 보유·후보 종목의 매수·매도 검토 결과입니다.
                자동 주문이 아니라 최종 판단을 위한 기록입니다.
            </p>

            {resource.isLoading && !guide ? (
                <p className="status-message" aria-live="polite">오늘 가이드 상태를 확인하는 중입니다.</p>
            ) : null}
            {resource.error ? (
                isResourceRateLimit ? (
                    <MarketDataRateLimitNotice
                        message={resource.error.message}
                        onRetry={resource.refresh}
                        retryLabel="오늘 가이드 다시 확인"
                        isPreservedValuation={Boolean(guide)}
                        cooldownSeconds={resourceRetryAfterSeconds}
                    />
                ) : !guide ? (
                    <RequestError
                        message={resource.error.message}
                        onRetry={resource.refresh}
                        retryLabel="오늘 가이드 다시 확인"
                    />
                ) : null
            ) : null}
            {generationError ? (
                isGenerationRateLimit ? (
                    <MarketDataRateLimitNotice
                        message={generationError.message}
                        onRetry={() => void generate(Boolean(guide?.snapshotId))}
                        retryLabel="가이드 생성 다시 시도"
                        cooldownSeconds={generationRetryAfterSeconds}
                    />
                ) : (
                    <RequestError
                        message={generationError.message}
                        onRetry={() => void generate(Boolean(guide?.snapshotId))}
                        retryLabel="가이드 생성 다시 시도"
                    />
                )
            ) : null}

            {guide ? (
                <>
                    <div className="premarket-guide-meta">
                        <span className={`premarket-guide-status ${statusClass}`}>{statusLabel}</span>
                        {guide.snapshotId ? (
                            <span className={`premarket-guide-status ${guide.inputEvidenceStatus === "VERIFIED" ? "completed" : "partial"}`}>
                                {guide.inputEvidenceStatus === "VERIFIED"
                                    ? "입력 근거 검증됨"
                                    : guide.inputEvidenceStatus === "CAPTURED"
                                        ? "입력 근거 수집·미검증"
                                        : guide.inputEvidenceStatus === "UNVERIFIED"
                                            ? "입력 근거 미검증"
                                            : "입력 근거 기록 없음"}
                            </span>
                        ) : null}
                        <span className="premarket-guide-meta-item">가이드 기준일 {guide.guideDate}</span>
                        <span className="premarket-guide-meta-item">시세 기준일 {dataAsOfLabel}</span>
                        {guide.snapshotId ? (
                            <span className="premarket-guide-meta-item">
                                시세 제공자 {toMarketDataProviderLabel(guide.marketDataProvider)}
                            </span>
                        ) : null}
                        {guide.generatedAt ? (
                            <span className="premarket-guide-meta-item">
                                생성 시각 {formatGeneratedAt(guide.generatedAt)}
                            </span>
                        ) : null}
                    </div>
                    <div className="premarket-guide-stats" aria-label="오늘 장전 가이드 요약">
                        <div><strong>{guide.heldGuides.length}</strong><span>보유 가이드</span></div>
                        <div><strong>{guide.candidateGuides.length}</strong><span>후보 가이드</span></div>
                        <div><strong>{guide.unavailableCount}</strong><span>조회 불가</span></div>
                    </div>
                    {guideRows.length > 0 ? (
                        <div className="premarket-guide-preview">
                            {guideRows.map((item) => (
                                <div className="premarket-guide-row" key={`${item.scopeLabel}-${item.market}-${item.ticker}`}>
                                    <span className="premarket-guide-row-scope">{item.scopeLabel}</span>
                                    {item.displayName && item.displayName !== item.ticker ? (
                                        <div className="premarket-guide-row-name broker-snapshot-item-identity">
                                            <strong className="broker-snapshot-item-name">{item.displayName}</strong>
                                            <div className="broker-snapshot-item-symbol">
                                                <span className="market-badge">{item.market}</span>
                                                <span className="broker-ticker">{item.ticker}</span>
                                            </div>
                                        </div>
                                    ) : (
                                        <strong>{item.ticker}</strong>
                                    )}
                                    <span className={`strategy-action action-${item.decision.action.toLowerCase()}`}>
                                        {toActionLabel(item.decision.action)}
                                    </span>
                                    <span className="premarket-guide-row-reason">{item.decision.reason}</span>
                                </div>
                            ))}
                            {guide.availableGuideCount > guideRows.length ? (
                                <p className="premarket-guide-more">상세 판단은 아래 보유·후보 가이드에서 전체 확인할 수 있습니다.</p>
                            ) : null}
                        </div>
                    ) : (
                        <p className="premarket-guide-empty">
                            {guide.snapshotId
                                ? "이번 가이드에서 검토할 종목이 없습니다. 보유 종목과 후보 종목 설정을 확인해 주세요."
                                : "아직 저장된 오늘 가이드가 없습니다. 생성 버튼을 눌러 현재 포트폴리오 기준을 기록해 주세요."}
                        </p>
                    )}
                    {unavailableMessage ? (
                        <p className="premarket-guide-warning">
                            {unavailableMessage}
                        </p>
                    ) : null}
                </>
            ) : null}
        </AccordionSection>
    );
}

function formatGeneratedAt(value: string) {
    // 백엔드 Clock은 UTC LocalDateTime을 반환하므로 브라우저 현지 시간으로 변환한다.
    const utcValue = value.endsWith("Z") ? value : `${value}Z`;
    return new Intl.DateTimeFormat("ko-KR", {
        dateStyle: "medium",
        timeStyle: "short",
    }).format(new Date(utcValue));
}

function toActionLabel(action: string) {
    return {
        BUY: "매수 검토",
        HOLD: "보유",
        REDUCE: "비중 축소",
        SELL: "매도 검토",
        WATCH: "관찰",
    }[action] ?? action;
}

function toMarketDataProviderLabel(provider: string | null) {
    return {
        TWELVE_DATA: "Twelve Data",
        TOSS_SECURITIES: "토스증권",
        YAHOO_FINANCE: "Yahoo Finance",
    }[provider ?? ""] ?? "기록 없음 (이전 가이드)";
}

function getUnavailableMessage(assets: PremarketGuide["unavailableAssets"]) {
    if (assets.length === 0) {
        return null;
    }

    const profileMissingCount = assets.filter(
        (asset) => asset.reason === "ASSET_PROFILE_NOT_FOUND",
    ).length;
    const marketDataCount = assets.length - profileMissingCount;
    const messages: string[] = [];

    if (profileMissingCount > 0) {
        messages.push(
            `${profileMissingCount}개 종목은 전략 프로필이 없어 가이드 계산에서 제외되었습니다. 아래 '보유 종목 투자 트랙 및 손절 기준 설정'에서 Track A를 지정해 주세요.`,
        );
    }

    if (marketDataCount > 0) {
        messages.push(
            `${marketDataCount}개 종목은 시세 제공자 응답 제한 또는 데이터 오류로 이번 실행에 포함되지 않았습니다.`,
        );
    }

    return messages.join(" ");
}
