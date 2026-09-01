import {useEffect, useState} from "react";
import {getCandidateStrategyGuides, getPortfolioStrategyGuides} from "../api/strategyGuideApi";
import {MEMBER_ID, PORTFOLIO_ID} from "../constants/portfolio";
import type {StrategyGuideBatch} from "../types/strategyGuide";
import StrategyGuideList from "../components/strategy/StrategyGuideList";

type GuideState = {data: StrategyGuideBatch | null; error: string | null};

export default function StrategyGuidesPage() {
    const [holdings, setHoldings] = useState<GuideState>({data: null, error: null});
    const [candidates, setCandidates] = useState<GuideState>({data: null, error: null});
    useEffect(() => { void getPortfolioStrategyGuides(MEMBER_ID, PORTFOLIO_ID).then((data) => setHoldings({data, error: null})).catch((reason: unknown) => setHoldings({data: null, error: reason instanceof Error ? reason.message : "보유 종목 가이드를 불러오지 못했습니다."})); void getCandidateStrategyGuides(MEMBER_ID, PORTFOLIO_ID).then((data) => setCandidates({data, error: null})).catch((reason: unknown) => setCandidates({data: null, error: reason instanceof Error ? reason.message : "후보 가이드를 불러오지 못했습니다."})); }, []);
    const render = (state: GuideState, emptyMessage: string) => state.error ? <p className="status-message error" role="alert">{state.error}</p> : state.data ? <StrategyGuideList {...state.data} emptyMessage={emptyMessage}/> : <p className="status-message" aria-live="polite">전략 가이드를 불러오는 중입니다.</p>;
    return <><header className="page-header"><p className="eyebrow">TRACK A</p><h1>전략 가이드</h1><p>가이드는 자동 주문이 아닌 최종 판단 전 검토 정보입니다.</p></header><section className="content-section"><div className="section-heading"><div><p className="section-label">HELD ASSETS</p><h2>보유 종목 가이드</h2></div></div>{render(holdings, "전략 가이드를 표시할 보유 종목이 없습니다.")}</section><section className="content-section"><div className="section-heading"><div><p className="section-label">CANDIDATES</p><h2>후보 가이드</h2></div></div>{render(candidates, "현재 등록된 후보 가이드가 없습니다.")}</section></>;
}
