import { useState } from "react";
import type { AssetStrategyGuide, EmptyHoldingsGuidance, StrategyAction, UnavailableAsset } from "../../types/strategyGuide";
import { formatUsd } from "../../utils/format";
import StrategyBacktestSection from "./StrategyBacktestSection";
import StrategyGuideEmptyHoldingsNotice from "./StrategyGuideEmptyHoldingsNotice";

const actionLabels: Record<StrategyAction, string> = {
    BUY: "매수 검토",
    HOLD: "보유 유지",
    REDUCE: "비중 축소 검토",
    SELL: "매도 검토",
    WATCH: "관찰",
};
const actionBadgeClass: Record<StrategyAction, string> = {
    BUY: "action-buy",
    HOLD: "action-hold",
    REDUCE: "action-reduce",
    SELL: "action-sell",
    WATCH: "action-watch",
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

export default function StrategyGuideList({ memberId, portfolioId, guides, unavailableAssets, emptyHoldingsGuidance, emptyMessage, }: Props) {
    if (guides.length === 0 && unavailableAssets.length === 0) {
        if (emptyHoldingsGuidance) {
            return <StrategyGuideEmptyHoldingsNotice guidance={emptyHoldingsGuidance} />;
        }
        return <div className="status-box status-empty"><p className="status-text">{emptyMessage}</p></div>;
    }

    return (
        <div className="strategy-guide-list-wrapper">
            <ul className="guide-list">
                {guides.map(guide => (
                    <GuideCard key={`${guide.market}-${guide.ticker}`} guide={guide} memberId={memberId} portfolioId={portfolioId} />
                ))}
            </ul>
        </div>
    );
}

function GuideCard({ guide, memberId, portfolioId }: { guide: AssetStrategyGuide, memberId?: number, portfolioId?: number }) {
    const [isExpanded, setIsExpanded] = useState(false);
    const { decision } = guide;
    
    return (
        <li className="guide-card">
            <div className="guide-card-main">
                <div className="guide-card-header">
                    <div className="guide-asset">
                        <span className="guide-ticker">{guide.ticker}</span>
                        <span className="guide-market">{guide.market}</span>
                    </div>
                    <span className={`action-badge ${actionBadgeClass[decision.action]}`}>{actionLabels[decision.action]}</span>
                </div>
                <p className="guide-reason">{decision.reason}</p>
                <div className="guide-metrics">
                    <div className="guide-metric-item">
                        <span className="guide-metric-label">기준 가격</span>
                        <span className="guide-metric-value">{formatUsd(decision.referencePrice)}</span>
                    </div>
                    <div className="guide-metric-item">
                        <span className="guide-metric-label">추세</span>
                        <span className="guide-metric-value">{decision.trend ? trendLabels[decision.trend] : "정보 없음"}</span>
                    </div>
                    <div className="guide-metric-item">
                        <span className="guide-metric-label">교차 상태</span>
                        <span className="guide-metric-value">{decision.signalEvent ? signalEventLabels[decision.signalEvent] : "정보 없음"}</span>
                    </div>
                    <div className="guide-metric-item">
                        <span className="guide-metric-label">최근 교차 후</span>
                        <span className="guide-metric-value">{decision.weeksSinceCross !== null ? `${decision.weeksSinceCross}주` : "정보 없음"}</span>
                    </div>
                </div>
                <div className="guide-stop-loss">
                    <strong>손절 가이드: </strong>
                    {decision.guidance?.stopLossPrice !== null 
                        ? `${formatUsd(decision.guidance?.stopLossPrice!)}` 
                        : (decision.guidance?.stopLossMessage || "미설정")}
                </div>
            </div>
            
            <button className="guide-details-toggle" onClick={() => setIsExpanded(!isExpanded)} aria-expanded={isExpanded}>
                <span>근거와 백테스트 펼치기</span>
                <span>{isExpanded ? "▴" : "▾"}</span>
            </button>
            
            {isExpanded && (
                <div className="guide-details-content">
                    <div className="guide-evidence-section">
                        <h4 className="guide-evidence-title">전략 및 신뢰도</h4>
                        <ul className="guide-evidence-list">
                            <li>전략: {decision.metadata.strategyId}</li>
                            <li>신뢰도: {decision.metadata.confidence || "정보 없음"}</li>
                            <li>시세 기준일: {decision.metadata.dataAsOf}</li>
                        </ul>
                    </div>
                    {decision.metadata.caveats && decision.metadata.caveats.length > 0 && (
                        <div className="guide-evidence-section">
                            <h4 className="guide-evidence-title">주의사항</h4>
                            <ul className="guide-evidence-list">
                                {decision.metadata.caveats.map(c => <li key={c}>{c}</li>)}
                            </ul>
                        </div>
                    )}
                    {memberId !== undefined && portfolioId !== undefined && (
                        <StrategyBacktestSection memberId={memberId} portfolioId={portfolioId} market={guide.market} ticker={guide.ticker} strategyId={decision.metadata.strategyId} />
                    )}
                </div>
            )}
        </li>
    );
}
