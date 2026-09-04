import {useEffect, useState} from "react";
import {getBrokerHoldingPreview, getBrokerLinkCandidates, getPortfolioBrokerLinks, linkBrokerAccount} from "../../api/portfolioBrokerApi";
import type {BrokerHoldingPreview, BrokerLinkCandidate, PortfolioBrokerLink} from "../../types/portfolioBroker";
import RequestError from "../common/RequestError";

type PortfolioBrokerLinkSectionProps = {
    memberId: number;
    portfolioId: number;
}

const comparisonLabel = {
    MATCHED: "수량 일치",
    QUANTITY_MISMATCH: "수량 차이",
    ONLY_IN_BROKER: "증권사에만 있음",
    ONLY_IN_TRADE_GUIDE: "Trade Guide에만 있음",
} as const;

export default function PortfolioBrokerLinkSection({memberId, portfolioId}: PortfolioBrokerLinkSectionProps) {
    const [candidates, setCandidates] = useState<BrokerLinkCandidate[]>([]);
    const [links, setLinks] = useState<PortfolioBrokerLink[]>([]);
    const [preview, setPreview] = useState<BrokerHoldingPreview | null>(null);
    const [error, setError] = useState<string | null>(null);
    const [isLoading, setIsLoading] = useState(true);
    const [isConnecting, setIsConnecting] = useState(false);
    const [isPreviewLoading, setIsPreviewLoading] = useState(false);

    const loadLinkData = async () => {
        setIsLoading(true);
        setError(null);

        try {
            const [linkCandidates, portfolioLinks] = await Promise.all([
                getBrokerLinkCandidates(memberId, portfolioId),
                getPortfolioBrokerLinks(memberId, portfolioId),
            ]);
            setCandidates(linkCandidates);
            setLinks(portfolioLinks);
        } catch (reason) {
            setError(reason instanceof Error ? reason.message : "계좌 정보를 불러오지 못했습니다.");
        } finally {
            setIsLoading(false);
        }
    };

    useEffect(() => {
        let isCurrentRequest = true;

        void Promise.all([
            getBrokerLinkCandidates(memberId, portfolioId),
            getPortfolioBrokerLinks(memberId, portfolioId),
        ]).then(([linkCandidates, portfolioLinks]) => {
            if (!isCurrentRequest) return;

            setCandidates(linkCandidates);
            setLinks(portfolioLinks);
            setError(null);
        }).catch((reason: unknown) => {
            if (!isCurrentRequest) return;

            setError(reason instanceof Error ? reason.message : "계좌 정보를 불러오지 못했습니다.");
        }).finally(() => {
            if (isCurrentRequest) {
                setIsLoading(false);
            }
        });

        return () => {
            isCurrentRequest = false;
        };
    }, [memberId, portfolioId]);

    const connect = async (candidate: BrokerLinkCandidate) => {
        setIsConnecting(true);
        setError(null);

        try {
            setLinks([await linkBrokerAccount(memberId, portfolioId, candidate)]);
            setPreview(null);
        } catch (reason) {
            setError(reason instanceof Error ? reason.message : "계좌를 연결하지 못했습니다.");
        } finally {
            setIsConnecting(false);
        }
    };

    const loadPreview = async () => {
        setIsPreviewLoading(true);
        setError(null);

        try {
            setPreview(await getBrokerHoldingPreview(memberId, portfolioId));
        } catch (reason) {
            setError(reason instanceof Error ? reason.message : "보유 종목을 불러오지 못했습니다.");
        } finally {
            setIsPreviewLoading(false);
        }
    };

    return <section className="broker-portfolio-section">
        <div className="section-heading">
            <div>
                <p className="section-label">BROKER PORTFOLIO</p>
                <h2>증권사 계좌 기준</h2>
            </div>
        </div>
        <p className="section-description">연결 계좌를 기준으로 보유 종목 차이만 읽기 전용으로 확인합니다.</p>

        {isLoading ? <p className="status-message">연결 가능한 계좌를 불러오는 중입니다.</p> : null}
        {error ? <RequestError message={error} onRetry={loadLinkData} retryLabel="계좌 정보 다시 시도"/> : null}

        {!isLoading && !error && links.length > 0 ? <div className="broker-link-current">
            <strong>{links[0].displayName} · {links[0].maskedAccountNumber}</strong>
            <button type="button" onClick={() => void loadPreview()} disabled={isPreviewLoading}>
                {isPreviewLoading ? "불러오는 중..." : "보유 종목 미리보기"}
            </button>
        </div> : null}

        {!isLoading && !error && links.length === 0 && candidates.length === 0 ? <p className="empty-state">연결 확인을 마친 증권사 계좌가 없습니다.</p> : null}
        {!isLoading && !error && links.length === 0 && candidates.length > 0 ? <ul className="broker-link-list">
            {candidates.map((candidate) => <li key={candidate.brokerAccountId}>
                <span>{candidate.displayName} · {candidate.maskedAccountNumber}</span>
                <button type="button" onClick={() => void connect(candidate)} disabled={isConnecting}>
                    {isConnecting ? "연결 중..." : "이 계좌 연결"}
                </button>
            </li>)}
        </ul> : null}

        {preview ? <>
            <p className="broker-preview-caption">{preview.maskedAccountNumber} 조회 결과 · 매매 기록은 변경하지 않습니다.</p>
            {preview.items.length === 0 ? <p className="empty-state">비교할 보유 종목이 없습니다.</p> : null}
            <ul className="broker-preview">
                {preview.items.map((item) => <li key={`${item.market}-${item.ticker}`}>
                    <div>
                        <strong>{item.displayName}</strong>
                        <span className="broker-ticker">{item.market} · {item.ticker}</span>
                    </div>
                    <span>{comparisonLabel[item.comparison]} · 증권사 {item.brokerQuantity}주 / Trade Guide {item.tradeGuideQuantity}주</span>
                </li>)}
            </ul>
            {preview.unsupportedMarketCount > 0 ? <p className="section-description">지원하지 않는 시장 종목 {preview.unsupportedMarketCount}건은 비교에서 제외했습니다.</p> : null}
        </> : null}
    </section>;
}
