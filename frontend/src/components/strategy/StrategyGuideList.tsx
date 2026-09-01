import type {AssetStrategyGuide, StrategyAction, UnavailableAsset} from "../../types/strategyGuide";
import {formatUsd} from "../../utils/format";

const actionLabels: Record<StrategyAction, string> = {BUY: "매수 검토", HOLD: "보유 유지", REDUCE: "비중 축소 검토", SELL: "매도 검토", WATCH: "관찰"};

type Props = {guides: AssetStrategyGuide[]; unavailableAssets: UnavailableAsset[]; emptyMessage: string};

export default function StrategyGuideList({guides, unavailableAssets, emptyMessage}: Props) {
    return <>
        {guides.length === 0 ? <p className="empty-state">{emptyMessage}</p> : <ul className="guide-list">{guides.map(({market, ticker, decision}) => <li key={`${market}-${ticker}`} className="guide-card">
            <div className="guide-card-heading"><div><strong>{ticker}</strong><span>{market}</span></div><span className={`action-badge ${decision.action.toLowerCase()}`}>{actionLabels[decision.action]}</span></div>
            <p className="guide-reason">{decision.reason}</p>
            <dl className="guide-details"><div><dt>기준 가격</dt><dd>{formatUsd(decision.referencePrice)}</dd></div><div><dt>기준일</dt><dd>{decision.metadata.dataAsOf}</dd></div><div><dt>전략</dt><dd>{decision.metadata.strategyId} v{decision.metadata.strategyVersion}</dd></div>{decision.weeksSinceCross !== null ? <div><dt>최근 교차 후</dt><dd>{decision.weeksSinceCross}주</dd></div> : null}</dl>
        </li>)}</ul>}
        {unavailableAssets.length > 0 ? <section className="unavailable-assets" aria-label="조회할 수 없는 종목"><h3>일부 종목의 가이드를 조회할 수 없습니다</h3><ul>{unavailableAssets.map((asset) => <li key={`${asset.market}-${asset.ticker}`}><strong>{asset.ticker}</strong> ({asset.market}) — {asset.message}</li>)}</ul></section> : null}
    </>;
}
