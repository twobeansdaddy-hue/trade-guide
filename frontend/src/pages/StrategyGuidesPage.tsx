import {useEffect, useState} from "react";
import {
    getApiRetryAfterSeconds,
    hasApiStatus,
    isMarketDataRateLimitExceeded,
} from "../api/apiError";
import RequestError from "../components/common/RequestError";
import StrategyGuideList from "../components/strategy/StrategyGuideList";
import PortfolioAssetStrategyProfileSection from "../components/strategy/PortfolioAssetStrategyProfileSection";
import PortfolioCandidateAssetManager from "../components/strategy/PortfolioCandidateAssetManager";
import PremarketGuidePanel from "../components/strategy/PremarketGuidePanel";
import TradePlanPreviewSection from "../components/strategy/TradePlanPreviewSection";
import MarketDataRateLimitNotice from "../components/valuation/MarketDataRateLimitNotice";
import {getCandidateStrategyGuides, getPortfolioStrategyGuides} from "../api/strategyGuideApi";
import {getTodayPremarketGuide} from "../api/premarketGuideApi";
import {getTradePlanPreview} from "../api/tradePlanPreviewApi";
import {usePortfolioContext} from "../context/portfolioContext";
import {usePortfolioResource} from "../hooks/usePortfolioResource";

export default function StrategyGuidesPage() {
    const {memberId, selectedPortfolioId} = usePortfolioContext();
    if (selectedPortfolioId === null) return null;

    return <StrategyGuidesContent key={`${memberId}-${selectedPortfolioId}`} memberId={memberId} portfolioId={selectedPortfolioId}/>;
}

function StrategyGuidesContent({memberId, portfolioId}: {memberId: number; portfolioId: number}) {
    const holdingsResource = usePortfolioResource(memberId, portfolioId, getPortfolioStrategyGuides);
    const candidatesResource = usePortfolioResource(memberId, portfolioId, getCandidateStrategyGuides);
    const premarketGuideResource = usePortfolioResource(memberId, portfolioId, getTodayPremarketGuide);
    const tradePlanResource = usePortfolioResource(memberId, portfolioId, getTradePlanPreview);
    const [candidateCount, setCandidateCount] = useState<number | null>(null);
    const dataAsOfDates = [holdingsResource.data, candidatesResource.data]
        .flatMap((batch) => batch?.guides.map((guide) => guide.decision.metadata.dataAsOf) ?? [])
        .filter((date, index, dates) => dates.indexOf(date) === index)
        .sort();
    const dataAsOfLabel = dataAsOfDates.length === 1
        ? dataAsOfDates[0]
        : dataAsOfDates.length > 1
            ? `${dataAsOfDates[0]} ~ ${dataAsOfDates.at(-1)}`
            : null;

    useEffect(() => {
        if (
            window.location.hash === "#held-asset-strategy-profiles" ||
            window.location.hash === "#strategy-profiles"
        ) {
            const timer = setTimeout(() => {
                document.getElementById("held-asset-strategy-profiles")?.scrollIntoView({
                    behavior: "smooth",
                    block: "start",
                });
            }, 100);
            return () => clearTimeout(timer);
        }
    }, []);

    const isMissingStrategyProfile = (error: unknown): boolean => {
        if (!error) return false;
        if (hasApiStatus(error, 404)) return true;
        if (
            error instanceof Error &&
            (error.message.includes("AssetProfile") || error.message.includes("전략 프로필"))
        ) {
            return true;
        }
        return false;
    };

    const render = (
        resource: typeof holdingsResource,
        emptyMessage: string,
        retryLabel: string,
        missingProfileMessage?: string,
        guideType: "holding" | "candidate" = "holding",
    ) => {
        const isRateLimit = isMarketDataRateLimitExceeded(resource.error);
        const rateLimitCooldown = getApiRetryAfterSeconds(resource.error);

        if (resource.isLoading && !resource.data) {
            return (
                <p className="status-message" aria-live="polite">
                    {guideType === "candidate"
                        ? "후보 가이드를 불러오는 중입니다."
                        : "전략 가이드를 불러오는 중입니다."}
                </p>
            );
        }
        if (isMissingStrategyProfile(resource.error)) {
            return (
                <p className="empty-state">
                    {missingProfileMessage ?? emptyMessage}
                </p>
            );
        }
        if (isRateLimit && !resource.data) {
            return (
                <MarketDataRateLimitNotice
                    message={resource.error?.message}
                    onRetry={resource.refresh}
                    retryLabel={retryLabel}
                    cooldownSeconds={rateLimitCooldown}
                />
            );
        }
        if (resource.error && !resource.data) {
            return (
                <RequestError
                    message={resource.error.message}
                    onRetry={resource.refresh}
                    retryLabel={retryLabel}
                />
            );
        }
        return resource.data ? (
            <>
                {isRateLimit ? (
                    <MarketDataRateLimitNotice
                        isPreservedValuation
                        message={resource.error?.message}
                        onRetry={resource.refresh}
                        retryLabel={retryLabel}
                        cooldownSeconds={rateLimitCooldown}
                    />
                ) : null}
                <StrategyGuideList
                    memberId={memberId}
                    portfolioId={portfolioId}
                    {...resource.data}
                    emptyMessage={emptyMessage}
                    guideType={guideType}
                    onRetry={resource.refresh}
                />
            </>
        ) : null;
    };

    return (
        <>
            <header className="page-header">
                <p className="eyebrow">TRACK A</p>
                <h1>전략 가이드</h1>
                <p>가이드는 자동 주문이 아닌 최종 판단 전 검토 정보입니다.</p>
                {dataAsOfLabel ? <p className="data-as-of">시세 데이터 기준일 {dataAsOfLabel}</p> : null}
            </header>
            <PremarketGuidePanel
                memberId={memberId}
                portfolioId={portfolioId}
                resource={premarketGuideResource}
            />
            <TradePlanPreviewSection
                memberId={memberId}
                portfolioId={portfolioId}
                resource={tradePlanResource}
            />
            <section className="content-section">
                <div className="section-heading">
                    <div>
                        <p className="section-label">HELD ASSETS</p>
                        <h2>보유 종목 가이드</h2>
                    </div>
                </div>
                {render(
                    holdingsResource,
                    "전략 가이드를 표시할 보유 종목이 없습니다.",
                    "보유 종목 가이드 다시 시도",
                    "전략 프로필이 등록되지 않은 보유 종목이 포함되어 있거나, 전략 가이드를 표시할 보유 종목이 없습니다.",
                )}
            </section>
            <section className="content-section candidate-guides-section">
                <div className="section-heading">
                    <div className="section-title-wrap">
                        <p className="section-label">TRACK A CANDIDATES (NON-HELD ASSETS)</p>
                        <div className="section-title-row">
                            <h2>Track A 후보 가이드</h2>
                            <span className="candidate-scope-badge">미보유 종목 전용 · 검토용</span>
                        </div>
                    </div>
                </div>
                <p className="section-description">
                    {candidateCount !== null && candidateCount > 0
                        ? `이 포트폴리오에 직접 등록한 Track A 후보(${candidateCount}개) 중 현재 미보유 종목을 대상으로 완료 주봉 10주/40주 이동평균(SMA) 신호를 계산한 기술적 검토 가이드입니다.`
                        : candidateCount === 0
                            ? "포트폴리오 직접 등록 후보가 없어, 관리자가 선별한 전역 Track A 검토 유니버스(기본 대상군) 중 현재 미보유 종목을 대상으로 완료 주봉 10주/40주 이동평균(SMA) 신호를 계산한 기술적 검토 가이드입니다."
                            : "포트폴리오에 설정한 Track A 후보(후보 미설정 시 관리자 선별 전역 검토 유니버스) 중 현재 미보유 종목을 대상으로 완료 주봉 10주/40주 이동평균(SMA) 신호를 계산한 기술적 검토 가이드입니다."}
                </p>
                <div className="candidate-guide-notice" role="note" aria-label="Track A 후보 가이드 기준 및 유의사항">
                    <p className="candidate-guide-notice-title">Track A 후보 가이드 운영 원칙 및 검토 기준</p>
                    <ul className="candidate-guide-notice-list">
                        <li>
                            <strong>관리자 선별 Track A 검토 유니버스:</strong> 미국 주식 전 종목 대상의 자동 스크리너가 아니며,{" "}
                            {candidateCount !== null && candidateCount > 0 ? (
                                <>
                                    현재 <strong>이 포트폴리오에 직접 등록한 Track A 후보({candidateCount}개)</strong>를 우선 조회하고 있습니다. (후보를 모두 삭제하면 관리자가 사전 선별·관리하는 전역 Track A 검토 유니버스로 자동 전환됩니다.)
                                </>
                            ) : candidateCount === 0 ? (
                                <>
                                    현재 등록된 포트폴리오 후보가 없어 <strong>관리자가 선별·관리하는 전역 Track A 검토 유니버스(기본 대상군)</strong>를 기준으로 조회하고 있습니다. (아래 후보 종목 관리에서 종목을 추가하면 해당 포트폴리오 후보가 우선 적용됩니다.)
                                </>
                            ) : (
                                <>
                                    <strong>포트폴리오 직접 설정 후보</strong>가 있으면 해당 종목을 우선 조회하고, 미설정 시 <strong>관리자가 선별·관리하는 전역 Track A 검토 유니버스(기본 대상군)</strong>를 기준으로 자동 조회합니다.
                                </>
                            )}
                        </li>
                        <li>
                            <strong>미보유 종목 한정 (보유 종목과 분리):</strong> 이미 포트폴리오 매매 원장에 등록된 보유 종목은 상단의 <strong>&apos;보유 종목 가이드&apos;</strong>에서 관리되므로 후보 대상에서 자동으로 제외됩니다. 본 섹션은 아직 매수하지 않은 미보유 관심 종목의 사전 기술적 분석만을 제공합니다.
                        </li>
                        <li>
                            <strong>완료 주봉 10주/40주 이동평균 신호:</strong> 주간 거래가 마감된 <strong>완료 주봉(Completed Weekly Candles)</strong> 기준 10주/40주 단순이동평균선(SMA)의 추세(상승/하락)와 골든크로스·데드크로스 교차 상태를 바탕으로 산출됩니다. 실시간 호가나 주중 미완료 봉에 의한 일시적 가격 왜곡·노이즈를 배제하고 확정된 주간 추세만을 확인합니다.
                        </li>
                        <li>
                            <strong>정보 제공 목적 (투자 권유·자동 주문 아님):</strong> 본 가이드는 기술적 지표에 기반한 단순 검토용 정보이며, 특정 종목 추천이나 투자 자문, 포트폴리오 적합성 판정 또는 자동 주문 실행 기능이 아닙니다. 실제 매매 판단 및 증권사 주문 등록은 전적으로 사용자의 자기 책임과 직접 결정하에 진행되어야 합니다.
                        </li>
                    </ul>
                </div>
                <PortfolioCandidateAssetManager
                    memberId={memberId}
                    portfolioId={portfolioId}
                    onCandidateChanged={() => {
                        candidatesResource.refresh();
                        tradePlanResource.refresh();
                    }}
                    onCandidateCountChange={setCandidateCount}
                />
                {render(
                    candidatesResource,
                    candidateCount !== null && candidateCount > 0
                        ? "이 포트폴리오에 설정한 Track A 후보 중 현재 미보유 종목이 없거나, 유효한 후보 가이드가 없습니다."
                        : candidateCount === 0
                            ? "관리자 선별 Track A 검토 유니버스(기본 대상군) 중 현재 포트폴리오 미보유 종목이 없거나, 유효한 후보 가이드가 없습니다."
                            : "이 포트폴리오에 설정한 Track A 후보 또는 관리자 선별 검토 유니버스 중 현재 미보유 종목이 없거나, 유효한 후보 가이드가 없습니다.",
                    "후보 가이드 다시 시도",
                    undefined,
                    "candidate",
                )}
            </section>
            <PortfolioAssetStrategyProfileSection
                memberId={memberId}
                portfolioId={portfolioId}
                onProfileChanged={() => {
                    holdingsResource.refresh();
                    tradePlanResource.refresh();
                }}
            />
        </>
    );
}
