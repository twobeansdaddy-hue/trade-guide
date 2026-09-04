import {useCallback, useEffect, useState, type FormEvent} from "react";
import {
    createBrokerConnection,
    deleteBrokerConnection,
    getBrokerConnections,
    verifyBrokerConnection,
} from "../../api/brokerConnectionApi";
import RequestError from "../common/RequestError";
import type {BrokerConnection} from "../../types/brokerConnection";
import PortfolioBrokerLinkSection from "./PortfolioBrokerLinkSection";
import {usePortfolioContext} from "../../context/portfolioContext";

type BrokerConnectionSectionProps = {
    memberId: number
}

export default function BrokerConnectionSection({memberId}: BrokerConnectionSectionProps) {
    const {selectedPortfolioId} = usePortfolioContext();
    const [connections, setConnections] = useState<BrokerConnection[] | null>(null);
    const [isLoading, setIsLoading] = useState(true);
    const [loadError, setLoadError] = useState<string | null>(null);
    const [displayName, setDisplayName] = useState("");
    const [clientId, setClientId] = useState("");
    const [clientSecret, setClientSecret] = useState("");
    const [formError, setFormError] = useState<string | null>(null);
    const [success, setSuccess] = useState<string | null>(null);
    const [isSubmitting, setIsSubmitting] = useState(false);
    const [deletingConnectionId, setDeletingConnectionId] = useState<number | null>(null);
    const [verifyingConnectionId, setVerifyingConnectionId] = useState<number | null>(null);

    const loadConnections = useCallback(async () => {
        setIsLoading(true);
        setLoadError(null);

        try {
            setConnections(await getBrokerConnections(memberId));
        } catch (reason) {
            setConnections(null);
            setLoadError(
                reason instanceof Error
                    ? reason.message
                    : "증권사 연결 정보를 불러오지 못했습니다.",
            );
        } finally {
            setIsLoading(false);
        }
    }, [memberId]);

    useEffect(() => {
        let isCurrentRequest = true;

        void getBrokerConnections(memberId)
            .then((data) => {
                if (!isCurrentRequest) return;

                setConnections(data);
                setLoadError(null);
            })
            .catch((reason: unknown) => {
                if (!isCurrentRequest) return;

                setConnections(null);
                setLoadError(
                    reason instanceof Error
                        ? reason.message
                        : "증권사 연결 정보를 불러오지 못했습니다.",
                );
            })
            .finally(() => {
                if (isCurrentRequest) {
                    setIsLoading(false);
                }
            });

        return () => {
            isCurrentRequest = false;
        };
    }, [memberId]);

    const submit = async (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        setFormError(null);
        setSuccess(null);

        if (!displayName.trim() || !clientId.trim() || !clientSecret.trim()) {
            setFormError("연결 이름, Client ID, Client Secret을 모두 입력해 주세요.");
            return;
        }

        setIsSubmitting(true);
        try {
            const createdConnection = await createBrokerConnection(memberId, {
                provider: "TOSS_SECURITIES",
                displayName: displayName.trim(),
                clientId,
                clientSecret,
            });
            setConnections((current) => [createdConnection, ...(current ?? [])]);
            setDisplayName("");
            setClientId("");
            setClientSecret("");
            setSuccess("토스증권 연결 정보를 안전하게 저장했습니다. 계좌 확인은 다음 단계에서 지원합니다.");
        } catch (reason) {
            setFormError(
                reason instanceof Error
                    ? reason.message
                    : "증권사 연결 정보를 저장하지 못했습니다.",
            );
        } finally {
            setIsSubmitting(false);
        }
    };

    const disconnect = async (connectionId: number) => {
        setFormError(null);
        setSuccess(null);
        setDeletingConnectionId(connectionId);

        try {
            await deleteBrokerConnection(memberId, connectionId);
            setConnections((current) => current?.filter((connection) => connection.id !== connectionId) ?? []);
            setSuccess("증권사 연결 정보를 해제했습니다.");
        } catch (reason) {
            setFormError(
                reason instanceof Error
                    ? reason.message
                    : "증권사 연결 정보를 해제하지 못했습니다.",
            );
        } finally {
            setDeletingConnectionId(null);
        }
    };

    const verify = async (connectionId: number) => {
        setFormError(null);
        setSuccess(null);
        setVerifyingConnectionId(connectionId);

        try {
            const verifiedConnection = await verifyBrokerConnection(memberId, connectionId);
            setConnections((current) => current?.map((connection) =>
                connection.id === connectionId ? verifiedConnection : connection,
            ) ?? []);
            setSuccess("토스증권 연결을 확인했습니다.");
        } catch (reason) {
            setFormError(reason instanceof Error ? reason.message : "증권사 연결을 확인하지 못했습니다.");
        } finally {
            setVerifyingConnectionId(null);
        }
    };

    return <><section className="broker-connection-section">
        <div className="section-heading">
            <div>
                <p className="section-label">BROKER CONNECTION</p>
                <h2>증권사 연결</h2>
            </div>
        </div>
        <p className="section-description">
            증권사 접근 정보는 암호화해 저장합니다. 현재는 연결 정보 보관만 지원하며 주문이나 계좌 동기화는 실행하지 않습니다.
        </p>

        {isLoading ? <p className="status-message" aria-live="polite">증권사 연결 정보를 불러오는 중입니다.</p> : null}
        {loadError ? <RequestError message={loadError} onRetry={loadConnections} retryLabel="증권사 연결 다시 시도"/> : null}
        {connections?.length === 0 ? <p className="empty-state">연결된 증권사가 없습니다.</p> : null}
        {connections && connections.length > 0 ? <ul className="broker-connection-list">
            {connections.map((connection) => <li key={connection.id}>
                <div>
                    <strong>{connection.displayName}</strong>
                    <p>토스증권 · {connection.status === "CONNECTED" ? "연결 확인됨" : "연결 확인 전"}</p>
                    {connection.accounts.length > 0 ? <p>확인된 계좌: {connection.accounts.map((account) => account.maskedAccountNumber).join(", ")}</p> : null}
                </div>
                <div className="broker-connection-actions">
                    <button type="button" className="quiet-action" onClick={() => void verify(connection.id)} disabled={verifyingConnectionId === connection.id || deletingConnectionId === connection.id}>
                        {verifyingConnectionId === connection.id ? "확인 중..." : "연결 확인"}
                    </button>
                    <button type="button" className="quiet-action" onClick={() => void disconnect(connection.id)} disabled={deletingConnectionId === connection.id || verifyingConnectionId === connection.id}>
                        {deletingConnectionId === connection.id ? "해제 중..." : "연결 해제"}
                    </button>
                </div>
            </li>)}
        </ul> : null}

        <form className="broker-connection-editor" onSubmit={submit}>
            <h3>토스증권 연결 추가</h3>
            <label>연결 이름<input value={displayName} onChange={(event) => setDisplayName(event.target.value)} maxLength={100} placeholder="예: 개인 토스증권" autoComplete="off"/></label>
            <label>Client ID<input value={clientId} onChange={(event) => setClientId(event.target.value)} autoComplete="off"/></label>
            <label>Client Secret<input type="password" value={clientSecret} onChange={(event) => setClientSecret(event.target.value)} autoComplete="new-password"/></label>
            {formError ? <p className="form-error-message" role="alert">{formError}</p> : null}
            {success ? <p className="form-success-message" role="status">{success}</p> : null}
            <button type="submit" disabled={isSubmitting}>{isSubmitting ? "암호화하여 저장 중..." : "토스증권 연결 저장"}</button>
        </form>
    </section>{selectedPortfolioId !== null ? <PortfolioBrokerLinkSection memberId={memberId} portfolioId={selectedPortfolioId}/> : null}</>;
}
