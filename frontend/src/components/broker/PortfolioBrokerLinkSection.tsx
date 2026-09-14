import {useEffect, useState} from "react";
import {
    getBrokerLinkCandidates,
    getPortfolioBrokerLinks,
    linkBrokerAccount,
    unlinkBrokerAccount,
} from "../../api/portfolioBrokerApi";
import type {BrokerLinkCandidate, PortfolioBrokerLink} from "../../types/portfolioBroker";
import RequestError from "../common/RequestError";

type PortfolioBrokerLinkSectionProps = {
    memberId: number;
    portfolioId: number;
    connectionRevision: number;
    onLinkedAccountChange?: (account: PortfolioBrokerLink | null) => void;
    onNextStep?: () => void;
    onPrevStep?: () => void;
    onGoToStep?: (step: number) => void;
};

export default function PortfolioBrokerLinkSection({
    memberId,
    portfolioId,
    connectionRevision,
    onLinkedAccountChange,
    onNextStep,
    onPrevStep,
    onGoToStep,
}: PortfolioBrokerLinkSectionProps) {
    const [candidates, setCandidates] = useState<BrokerLinkCandidate[]>([]);
    const [links, setLinks] = useState<PortfolioBrokerLink[]>([]);
    const [error, setError] = useState<string | null>(null);
    const [isLoading, setIsLoading] = useState(true);
    const [isConnecting, setIsConnecting] = useState(false);
    const [isUnlinking, setIsUnlinking] = useState(false);
    const [actionError, setActionError] = useState<string | null>(null);
    const [actionSuccess, setActionSuccess] = useState<string | null>(null);

    const loadLinkData = async () => {
        setIsLoading(true);
        setError(null);
        setActionError(null);
        setActionSuccess(null);

        try {
            const [linkCandidates, portfolioLinks] = await Promise.all([
                getBrokerLinkCandidates(memberId, portfolioId),
                getPortfolioBrokerLinks(memberId, portfolioId),
            ]);
            setCandidates(linkCandidates);
            setLinks(portfolioLinks);
            onLinkedAccountChange?.(portfolioLinks[0] ?? null);
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
            onLinkedAccountChange?.(portfolioLinks[0] ?? null);
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
    }, [memberId, portfolioId, connectionRevision, onLinkedAccountChange]);

    const connect = async (candidate: BrokerLinkCandidate) => {
        setIsConnecting(true);
        setActionError(null);
        setActionSuccess(null);

        try {
            const newLink = await linkBrokerAccount(memberId, portfolioId, candidate);
            setLinks([newLink]);
            onLinkedAccountChange?.(newLink);
            setActionSuccess(`${candidate.displayName} 계좌를 연결했습니다.`);
        } catch (reason) {
            setActionError(reason instanceof Error ? reason.message : "계좌를 연결하지 못했습니다.");
        } finally {
            setIsConnecting(false);
        }
    };

    const unlink = async (connectionId: number, displayName: string) => {
        setIsUnlinking(true);
        setActionError(null);
        setActionSuccess(null);

        try {
            await unlinkBrokerAccount(memberId, portfolioId, connectionId);
            setLinks([]);
            onLinkedAccountChange?.(null);
            const updatedCandidates = await getBrokerLinkCandidates(memberId, portfolioId);
            setCandidates(updatedCandidates);
            setActionSuccess(`${displayName} 계좌 연결을 해제했습니다.`);
        } catch (reason) {
            setActionError(reason instanceof Error ? reason.message : "증권사 계좌 연결을 해제하지 못했습니다.");
        } finally {
            setIsUnlinking(false);
        }
    };

    return (
        <section className="content-section broker-portfolio-section">
            <div className="section-heading">
                <div>
                    <p className="section-label">3. PORTFOLIO ACCOUNT</p>
                    <h2>포트폴리오 계좌 선택</h2>
                </div>
            </div>
            <p className="section-description">
                포트폴리오(PORTFOLIO {portfolioId})에 연동할 증권사 계좌를 선택합니다. 계좌가 연결되면 보유 종목 비교와 주문 이력을 검토할 수 있습니다.
            </p>

            {isLoading ? <p className="status-message">연결 가능한 계좌를 불러오는 중입니다.</p> : null}
            {error ? <RequestError message={error} onRetry={loadLinkData} retryLabel="계좌 정보 다시 시도"/> : null}

            {!isLoading && !error && links.length > 0 ? (
                <>
                    <div className="broker-link-current">
                        <div className="broker-link-current-info">
                            <strong>{links[0].displayName} · {links[0].maskedAccountNumber}</strong>
                            <span className="status-pill pill-done">✓ 연동 활성</span>
                        </div>
                        <button
                            type="button"
                            className="secondary-button"
                            onClick={() => void unlink(links[0].brokerConnectionId, links[0].displayName)}
                            disabled={isUnlinking || isConnecting}
                            aria-label={`${links[0].displayName} 계좌 연결 해제`}
                        >
                            {isUnlinking ? "연결 해제 중..." : "연결 해제"}
                        </button>
                    </div>
                    <div className="broker-link-success-card" role="note">
                        <p>
                            <strong>✓ 계좌 연결 완료:</strong> 현재 포트폴리오에 증권사 계좌가 성공적으로 연결되었습니다. 다음 4단계에서 증권사 보유 종목을 갱신하고 비교하세요.
                        </p>
                    </div>
                </>
            ) : null}

            {!isLoading && !error && links.length === 0 && candidates.length === 0 ? (
                <div className="empty-state empty-state-action broker-link-empty-state">
                    <p>연결 확인을 마친 증권사 계좌가 없습니다. 먼저 2단계에서 증권사 연결 확인을 진행해 주세요.</p>
                    <div className="empty-state-actions">
                        {onGoToStep ? (
                            <button
                                type="button"
                                className="primary-button"
                                onClick={() => onGoToStep(2)}
                            >
                                2단계: 연결 확인으로 이동
                            </button>
                        ) : null}
                        <button
                            type="button"
                            className="quiet-action"
                            onClick={() => void loadLinkData()}
                            disabled={isLoading || isConnecting || isUnlinking}
                        >
                            {isLoading ? "불러오는 중..." : "계좌 목록 새로고침"}
                        </button>
                    </div>
                </div>
            ) : null}

            {!isLoading && !error && links.length === 0 && candidates.length > 0 ? (
                <>
                    <div className="broker-candidate-list-header">
                        <h3>연결 가능한 증권사 계좌 ({candidates.length}건)</h3>
                        <p>포트폴리오에 연동할 계좌 하나를 선택해 주세요.</p>
                    </div>
                    <ul className="broker-link-list">
                        {candidates.map((candidate) => (
                            <li key={candidate.brokerAccountId}>
                                <span>{candidate.displayName} · {candidate.maskedAccountNumber}</span>
                                <button
                                    type="button"
                                    className="primary-button"
                                    onClick={() => void connect(candidate)}
                                    disabled={isConnecting || isUnlinking}
                                >
                                    {isConnecting ? "연결 중..." : "이 계좌 연결"}
                                </button>
                            </li>
                        ))}
                    </ul>
                </>
            ) : null}

            <div className="broker-link-feedback" aria-live="polite">
                {actionError ? <p className="form-error-message" role="alert">{actionError}</p> : null}
                {actionSuccess ? <p className="form-success-message" role="status">{actionSuccess}</p> : null}
            </div>

            <div className="broker-step-nav-footer">
                <button
                    type="button"
                    className="secondary-button"
                    onClick={onPrevStep}
                >
                    ← 이전: 2단계 연결 확인
                </button>
                {links.length > 0 ? (
                    <button
                        type="button"
                        className="primary-button"
                        onClick={onNextStep}
                    >
                        다음: 4단계 보유 종목 비교 진행 →
                    </button>
                ) : null}
            </div>
        </section>
    );
}
