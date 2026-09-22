import {useEffect, useState} from "react";
import {
    getApiRetryAfterSeconds,
    hasApiStatus,
    isMarketDataRateLimitExceeded,
} from "../api/apiError";
import RequestError from "../components/common/RequestError";
import AccordionSection from "../components/strategy/AccordionSection";
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

function CandidateGuideNotice({ candidateCount }: { candidateCount: number | null }) {
    const [expanded, setExpanded] = useState(false);
    
    if (!expanded) {
        return (
            <div className="candidate-notice-collapsed" role="note">
                <span style={{fontSize: "13px", color: "var(--ink)"}}>Track A 후보 가이드 운영 원칙 및 검토 기준</span>
                <button type="button" onClick={() => setExpanded(true)}>펼치기</button>
            </div>
        );
    }
    
    return (
        <div className="candidate-notice-expanded" role="note">
            <div style={{display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "12px"}}>
                <h4>Track A 후보 가이드 운영 원칙 및 검토 기준</h4>
                <button type="button" onClick={() => setExpanded(false)}>접기</button>
            </div>
            <ul>
                <li><strong>관리자 선별 Track A 검토 유니버스:</strong> 미국 주식 전 종목 대상 자동 스크리너가 아니며, {candidateCount !== null && candidateCount > 0 ? `현재 이 포트폴리오에 직접 등록한 Track A 후보(${candidateCount}개)를 우선 조회합니다.` : "관리자가 선별·관리하는 전역 Track A 검토 유니버스(기본 대상군)를 기준으로 조회합니다."}</li>
                <li><strong>미보유 종목 한정:</strong> 이미 포트폴리오 매매 원장에 등록된 보유 종목은 대상에서 제외되며, 미보유 관심 종목의 사전 기술적 분석만 제공합니다.</li>
                <li><strong>완료 주봉 기준:</strong> 주간 거래가 마감된 완료 주봉 기준 10주/40주 단순이동평균선의 추세와 교차 상태를 바탕으로 산출됩니다.</li>
                <li><strong>정보 제공 목적:</strong> 단순 검토용 정보이며 투자 자문이나 자동 주문 기능이 아닙니다. 실제 매매 판단은 전적으로 사용자의 자기 책임하에 진행되어야 합니다.</li>
            </ul>
        </div>
    );
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
            <AccordionSection
                id="held-asset-guides"
                sectionLabel="HELD ASSETS"
                title="보유 종목 가이드"
                defaultOpen={true}
                summary={
                    holdingsResource.data
                        ? `총 ${holdingsResource.data.guides.length}건`
                        : undefined
                }
            >
                {render(
                    holdingsResource,
                    "전략 가이드를 표시할 보유 종목이 없습니다.",
                    "보유 종목 가이드 다시 시도",
                    "전략 프로필이 등록되지 않은 보유 종목이 포함되어 있거나, 전략 가이드를 표시할 보유 종목이 없습니다.",
                )}
            </AccordionSection>
            <AccordionSection
                id="candidate-guides"
                className="candidate-guides-section"
                sectionLabel="TRACK A CANDIDATES"
                title={`Track A 후보 — 미보유 ${candidateCount ?? 0}건`}
                defaultOpen={false}
                summary={
                    candidatesResource.data
                        ? `후보 ${candidatesResource.data.guides.length}건`
                        : undefined
                }
            >
                <p className="section-description">
                    이 포트폴리오에 설정한 Track A 후보 중 미보유 종목을 대상으로 한 기술적 검토 가이드입니다.
                </p>
                <CandidateGuideNotice candidateCount={candidateCount} />
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
            </AccordionSection>
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
