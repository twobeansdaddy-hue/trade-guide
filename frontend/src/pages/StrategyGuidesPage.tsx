import RequestError from "../components/common/RequestError";
import StrategyGuideList from "../components/strategy/StrategyGuideList";
import {getCandidateStrategyGuides, getPortfolioStrategyGuides} from "../api/strategyGuideApi";
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

    const render = (resource: typeof holdingsResource, emptyMessage: string, retryLabel: string) => {
        if (resource.isLoading) return <p className="status-message" aria-live="polite">전략 가이드를 불러오는 중입니다.</p>;
        if (resource.error) return <RequestError message={resource.error.message} onRetry={resource.refresh} retryLabel={retryLabel}/>;
        return resource.data ? <StrategyGuideList {...resource.data} emptyMessage={emptyMessage}/> : null;
    };

    return <><header className="page-header"><p className="eyebrow">TRACK A</p><h1>전략 가이드</h1><p>가이드는 자동 주문이 아닌 최종 판단 전 검토 정보입니다.</p></header><section className="content-section"><div className="section-heading"><div><p className="section-label">HELD ASSETS</p><h2>보유 종목 가이드</h2></div></div>{render(holdingsResource, "전략 가이드를 표시할 보유 종목이 없습니다.", "보유 종목 가이드 다시 시도")}</section><section className="content-section"><div className="section-heading"><div><p className="section-label">CANDIDATES</p><h2>후보 가이드</h2></div></div>{render(candidatesResource, "현재 등록된 후보 가이드가 없습니다.", "후보 가이드 다시 시도")}</section></>;
}
