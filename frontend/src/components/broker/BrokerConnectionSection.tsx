import {useCallback, useEffect, useRef, useState, type FormEvent} from "react";
import {
    createBrokerConnection,
    deleteBrokerConnection,
    getBrokerConnections,
    getBrokerProviders,
    verifyBrokerConnection,
} from "../../api/brokerConnectionApi";
import RequestError from "../common/RequestError";
import AccordionSection from "../strategy/AccordionSection";
import type {
    BrokerConnection,
    BrokerConnectionCreateRequest,
    BrokerCredentialField,
    BrokerProviderCatalogItem,
} from "../../types/brokerConnection";
import {formatDateTime} from "../../utils/format";

type BrokerConnectionSectionProps = {
    memberId: number;
    onConnectionChanged?: () => void;
    viewMode?: "registration" | "verification" | "all";
    onNextStep?: () => void;
    onPrevStep?: () => void;
    onGoToStep?: (step: number) => void;
    onConnectionsChange?: (connections: BrokerConnection[]) => void;
};

const CAPABILITY_METADATA: Record<
    string,
    { label: string; description: string }
> = {
    CONNECTION_VERIFICATION: {
        label: "연결 확인",
        description: "API 자격 증명 검증 및 연동 계좌 목록 조회",
    },
    HOLDING_SNAPSHOT: {
        label: "보유 종목 스냅샷",
        description: "실시간 보유 종목 조회 및 포트폴리오 비교",
    },
    TRANSACTION_HISTORY_IMPORT: {
        label: "거래 내역 가져오기",
        description: "증권사 주문 체결 내역 조회 및 원장 반영 검토",
    },
    CASH_BALANCE: {
        label: "예수금 조회",
        description: "계좌 현금 및 예수금 잔고 조회 (현재 어댑터 미지원)",
    },
};

function formatMarket(market: string): string {
    switch (market) {
        case "US":
            return "미국(US)";
        case "KR":
            return "한국(KR)";
        default:
            return market;
    }
}

export default function BrokerConnectionSection({
    memberId,
    onConnectionChanged,
    viewMode = "all",
    onNextStep,
    onPrevStep,
    onGoToStep,
    onConnectionsChange,
}: BrokerConnectionSectionProps) {
    const [connections, setConnections] = useState<BrokerConnection[] | null>(null);
    const [isLoading, setIsLoading] = useState(true);
    const [loadError, setLoadError] = useState<string | null>(null);

    const [providers, setProviders] = useState<BrokerProviderCatalogItem[] | null>(null);
    const [isProvidersLoading, setIsProvidersLoading] = useState(true);
    const [providersError, setProvidersError] = useState<string | null>(null);

    // Registration form state
    const [selectedProvider, setSelectedProvider] = useState<string>("");
    const [displayName, setDisplayName] = useState("");
    const [credentials, setCredentials] = useState<Record<string, string>>({});

    // Renewal flow state (separate credential renewal without re-exposing secrets)
    const [renewingConnection, setRenewingConnection] = useState<BrokerConnection | null>(null);
    const [renewDisplayName, setRenewDisplayName] = useState("");
    const [renewCredentials, setRenewCredentials] = useState<Record<string, string>>({});

    // Secret visibility toggles (keyed by inputId)
    const [showSecretFields, setShowSecretFields] = useState<Record<string, boolean>>({});

    const [formError, setFormError] = useState<string | null>(null);
    const [success, setSuccess] = useState<string | null>(null);
    const [isSubmitting, setIsSubmitting] = useState(false);
    const [deletingConnectionId, setDeletingConnectionId] = useState<number | null>(null);
    const [verifyingConnectionId, setVerifyingConnectionId] = useState<number | null>(null);
    const [isAddFormOpen, setIsAddFormOpen] = useState(false);

    const onConnectionsChangeRef = useRef(onConnectionsChange);
    useEffect(() => {
        onConnectionsChangeRef.current = onConnectionsChange;
    }, [onConnectionsChange]);

    const loadConnections = useCallback(async () => {
        setIsLoading(true);
        setLoadError(null);

        try {
            const data = await getBrokerConnections(memberId);
            setConnections(data);
            onConnectionsChangeRef.current?.(data);
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

    const loadCatalog = useCallback(async () => {
        setIsProvidersLoading(true);
        setProvidersError(null);

        try {
            const data = await getBrokerProviders();
            setProviders(data);
            setSelectedProvider((prev) => {
                if (prev && data.some((item) => item.provider === prev)) {
                    return prev;
                }
                const connectable = data.find((item) => item.connectable);
                return connectable ? connectable.provider : (data[0]?.provider ?? "");
            });
        } catch (reason) {
            setProviders(null);
            setProvidersError(
                reason instanceof Error
                    ? reason.message
                    : "증권사 제공자 목록을 불러오지 못했습니다.",
            );
        } finally {
            setIsProvidersLoading(false);
        }
    }, []);

    useEffect(() => {
        let isCurrentRequest = true;

        void getBrokerConnections(memberId)
            .then((data) => {
                if (!isCurrentRequest) return;
                setConnections(data);
                onConnectionsChangeRef.current?.(data);
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

    useEffect(() => {
        let isCurrentRequest = true;

        void getBrokerProviders()
            .then((data) => {
                if (!isCurrentRequest) return;
                setProviders(data);
                setProvidersError(null);
                setSelectedProvider((prev) => {
                    if (prev && data.some((item) => item.provider === prev)) {
                        return prev;
                    }
                    const connectable = data.find((item) => item.connectable);
                    return connectable ? connectable.provider : (data[0]?.provider ?? "");
                });
            })
            .catch((reason: unknown) => {
                if (!isCurrentRequest) return;
                setProviders(null);
                setProvidersError(
                    reason instanceof Error
                        ? reason.message
                        : "증권사 제공자 목록을 불러오지 못했습니다.",
                );
            })
            .finally(() => {
                if (isCurrentRequest) {
                    setIsProvidersLoading(false);
                }
            });

        return () => {
            isCurrentRequest = false;
        };
    }, []);

    const selectedProviderItem = providers?.find((p) => p.provider === selectedProvider) ?? null;

    const areRequiredFieldsFilled = Boolean(
        selectedProviderItem &&
        selectedProviderItem.credentialFields.every((field) => {
            if (!field.required) return true;
            const value = credentials[field.key];
            return value !== undefined && value.trim().length > 0;
        }),
    );

    const isConnectable = Boolean(selectedProviderItem?.connectable);

    const isFormValid =
        Boolean(selectedProviderItem) &&
        isConnectable &&
        displayName.trim().length > 0 &&
        areRequiredFieldsFilled;

    // Renewal validation
    const renewingProviderItem = providers?.find((p) => p.provider === renewingConnection?.provider) ?? null;
    const isRenewValid = Boolean(
        renewingConnection &&
        renewingProviderItem &&
        renewingProviderItem.connectable &&
        renewDisplayName.trim().length > 0 &&
        renewingProviderItem.credentialFields.every((field) => {
            if (!field.required) return true;
            const val = renewCredentials[field.key];
            return val !== undefined && val.trim().length > 0;
        }),
    );

    const isAnyActionPending = isSubmitting || deletingConnectionId !== null || verifyingConnectionId !== null;

    let unavailableMessage: string | null = null;
    if (selectedProviderItem && !isConnectable) {
        unavailableMessage = `현재 환경에서는 ${selectedProviderItem.displayName} 연결 기능을 지원하지 않아 연결 저장이 비활성화됩니다. 현재 실제 가용 증권사는 토스증권뿐입니다.`;
    }

    const toggleSecretVisibility = (fieldId: string) => {
        setShowSecretFields((prev) => ({
            ...prev,
            [fieldId]: !prev[fieldId],
        }));
    };

    const handleProviderChange = (newProvider: string) => {
        setSelectedProvider(newProvider);
        setCredentials({});
        setFormError(null);
        setSuccess(null);
    };

    const handleCredentialChange = (key: string, value: string) => {
        setCredentials((prev) => ({
            ...prev,
            [key]: value,
        }));
    };

    const handleRenewClick = (connection: BrokerConnection) => {
        setRenewingConnection(connection);
        setRenewDisplayName(`${connection.displayName} (갱신)`);
        setRenewCredentials({});
        setFormError(null);
        setSuccess(null);
        setIsAddFormOpen(false);
    };

    const handleCancelRenew = () => {
        setRenewingConnection(null);
        setRenewDisplayName("");
        setRenewCredentials({});
        setFormError(null);
    };

    const handleRenewCredentialChange = (key: string, value: string) => {
        setRenewCredentials((prev) => ({
            ...prev,
            [key]: value,
        }));
    };

    const submit = async (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        setFormError(null);
        setSuccess(null);

        if (!selectedProviderItem) {
            setFormError("증권사를 선택해 주세요.");
            return;
        }

        if (!selectedProviderItem.connectable) {
            setFormError(`${selectedProviderItem.displayName} 연결 기능을 현재 환경에서 지원하지 않습니다. 현재 실제 가용한 제공자는 토스증권뿐입니다.`);
            return;
        }

        if (!displayName.trim() || !areRequiredFieldsFilled) {
            setFormError("연결 이름과 모든 필수 자격 증명 항목을 입력해 주세요.");
            return;
        }

        setIsSubmitting(true);
        try {
            const cleanCredentials: Record<string, string> = {};
            for (const field of selectedProviderItem.credentialFields) {
                const val = credentials[field.key]?.trim();
                if (val) {
                    cleanCredentials[field.key] = val;
                }
            }

            const requestPayload: BrokerConnectionCreateRequest = {
                provider: selectedProviderItem.provider,
                displayName: displayName.trim(),
                credentials: cleanCredentials,
            };

            const createdConnection = await createBrokerConnection(memberId, requestPayload);
            const nextConnections = [createdConnection, ...(connections ?? [])];
            setConnections(nextConnections);
            onConnectionsChangeRef.current?.(nextConnections);
            setDisplayName("");
            setCredentials({});
            setIsAddFormOpen(false);
            setSuccess(
                `${selectedProviderItem.displayName} 연결 정보를 안전하게 암호화하여 저장했습니다. 계좌 확인은 다음 2단계에서 진행합니다.`,
            );
            onConnectionChanged?.();
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

    const submitRenew = async (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        setFormError(null);
        setSuccess(null);

        if (!renewingConnection || !renewingProviderItem) {
            setFormError("갱신 대상 연결 정보를 찾을 수 없습니다.");
            return;
        }

        if (!renewingProviderItem.connectable) {
            setFormError(`${renewingProviderItem.displayName} 연결 기능을 현재 지원하지 않습니다.`);
            return;
        }

        if (!renewDisplayName.trim() || !isRenewValid) {
            setFormError("연결 이름과 모든 필수 자격 증명 항목을 새로 입력해 주세요.");
            return;
        }

        setIsSubmitting(true);
        try {
            const cleanCredentials: Record<string, string> = {};
            for (const field of renewingProviderItem.credentialFields) {
                const val = renewCredentials[field.key]?.trim();
                if (val) {
                    cleanCredentials[field.key] = val;
                }
            }

            const requestPayload: BrokerConnectionCreateRequest = {
                provider: renewingProviderItem.provider,
                displayName: renewDisplayName.trim(),
                credentials: cleanCredentials,
            };

            const createdConnection = await createBrokerConnection(memberId, requestPayload);
            const nextConnections = [createdConnection, ...(connections ?? [])];
            setConnections(nextConnections);
            onConnectionsChangeRef.current?.(nextConnections);
            setRenewingConnection(null);
            setRenewDisplayName("");
            setRenewCredentials({});
            setSuccess(
                `'${createdConnection.displayName}' 새 자격 증명을 안전하게 암호화 저장했습니다. 다음 2단계에서 [연결 확인]을 진행해 주세요.`,
            );
            onConnectionChanged?.();
        } catch (reason) {
            setFormError(
                reason instanceof Error
                    ? reason.message
                    : "자격 증명을 갱신 저장하지 못했습니다.",
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
            const nextConnections = connections?.filter((connection) => connection.id !== connectionId) ?? [];
            setConnections(nextConnections);
            onConnectionsChangeRef.current?.(nextConnections);
            if (renewingConnection?.id === connectionId) {
                setRenewingConnection(null);
            }
            setSuccess("증권사 연결 정보를 해제했습니다.");
            onConnectionChanged?.();
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

    const verify = async (connectionId: number, targetName: string) => {
        setFormError(null);
        setSuccess(null);
        setVerifyingConnectionId(connectionId);

        try {
            const verifiedConnection = await verifyBrokerConnection(memberId, connectionId);
            const nextConnections = connections?.map((connection) =>
                connection.id === connectionId ? verifiedConnection : connection,
            ) ?? [];
            setConnections(nextConnections);
            onConnectionsChangeRef.current?.(nextConnections);
            setSuccess(`${targetName} 연결을 확인했습니다. 조회된 계좌를 확인하세요.`);
            onConnectionChanged?.();
        } catch (reason) {
            setFormError(reason instanceof Error ? reason.message : "증권사 연결을 확인하지 못했습니다.");
        } finally {
            setVerifyingConnectionId(null);
        }
    };

    const hasVerifiedConnection = Boolean(
        connections?.some((c) => c.status === "CONNECTED" && c.accounts.length > 0),
    );

    const renderCredentialFieldInput = (
        field: BrokerCredentialField,
        value: string,
        onChange: (value: string) => void,
        fieldPrefix: string,
        isOnlyField = false,
    ) => {
        const inputId = `${fieldPrefix}-${field.key}`;
        const hintId = `${inputId}-hint`;
        const isSecret = field.type === "SECRET";
        const isRevealed = Boolean(showSecretFields[inputId]);

        return (
            <label
                key={field.key}
                htmlFor={inputId}
                className={isOnlyField ? "form-field-full" : undefined}
            >
                <span>
                    {field.label}
                    {field.required ? (
                        <span className="field-required-mark" aria-hidden="true"> *</span>
                    ) : (
                        <span className="field-optional-mark"> (선택)</span>
                    )}
                </span>
                <div className={isSecret ? "secret-input-wrapper" : undefined}>
                    <input
                        id={inputId}
                        name={field.key}
                        type={isSecret ? (isRevealed ? "text" : "password") : "text"}
                        autoComplete={isSecret ? "new-password" : "off"}
                        placeholder={field.placeholder}
                        required={field.required}
                        aria-required={field.required}
                        value={value}
                        onChange={(event) => onChange(event.target.value)}
                        aria-describedby={field.hint ? hintId : undefined}
                        disabled={isAnyActionPending}
                    />
                    {isSecret ? (
                        <button
                            type="button"
                            className="secret-toggle-btn"
                            onClick={() => toggleSecretVisibility(inputId)}
                            disabled={isAnyActionPending}
                            aria-label={isRevealed ? `${field.label} 숨기기` : `${field.label} 보기`}
                            title={isRevealed ? "비밀값 숨기기" : "비밀값 보기"}
                        >
                            {isRevealed ? (
                                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                                    <path d="M9.88 9.88a3 3 0 1 0 4.24 4.24" />
                                    <path d="M10.73 5.08A10.43 10.43 0 0 1 12 5c7 0 10 7 10 7a13.16 13.16 0 0 1-1.67 2.68" />
                                    <path d="M6.61 6.61A13.526 13.526 0 0 0 2 12s3 7 10 7a9.74 9.74 0 0 0 5.39-1.61" />
                                    <line x1="2" x2="22" y1="2" y2="22" />
                                </svg>
                            ) : (
                                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                                    <path d="M2 12s3-7 10-7 10 7 10 7-3 7-10 7-10-7-10-7Z" />
                                    <circle cx="12" cy="12" r="3" />
                                </svg>
                            )}
                        </button>
                    ) : null}
                </div>
                {field.hint ? (
                    <span id={hintId} className="form-field-hint">
                        {field.hint}
                    </span>
                ) : null}
            </label>
        );
    };

    const renderHonestProviderNotice = () => (
        <div className="broker-provider-notice" role="note">
            <div className="provider-notice-header">
                <span className="notice-badge">제공자 가용 현황</span>
                <strong>현재 실제 가용 제공자는 토스증권뿐입니다</strong>
            </div>
            <p>
                Trade Guide는 현재 <strong>토스증권(Toss Securities)</strong>의 오픈API 연동만 지원합니다.
                키움증권, KB증권 등 타 증권사는 OpenAPI 검토 및 지원 준비 단계로, 현재 선택 및 연결이 불가함을 정직하게 안내합니다.
            </p>
        </div>
    );

    const renderCredentialRenewalForm = () => {
        if (!renewingConnection || !renewingProviderItem) return null;

        return (
            <form className="broker-connection-renewal-form" onSubmit={submitRenew}>
                <div className="renewal-form-header">
                    <div className="renewal-title-block">
                        <span className="renewal-badge">별도 자격 증명 갱신</span>
                        <h3>{renewingConnection.displayName} 자격 증명 갱신</h3>
                    </div>
                    <button
                        type="button"
                        className="quiet-action"
                        onClick={handleCancelRenew}
                        disabled={isAnyActionPending}
                        aria-label="자격 증명 갱신 취소"
                    >
                        ✕ 갱신 취소
                    </button>
                </div>

                <div className="broker-renewal-security-banner" role="note">
                    <strong>보안 원칙 안내</strong>
                    <p>
                        보안 정책에 따라 기존에 저장된 비밀값(Client Secret 등)은 복호화하여 화면에 재노출하지 않으며 폼에 미리 채워지지 않습니다.
                        토스증권에서 새로 발급받았거나 갱신된 Client ID 및 Client Secret을 아래에 직접 새로 입력해 주세요.
                    </p>
                </div>

                <div className="form-grid">
                    <div className="form-field-full">
                        <span className="field-label-text">증권사 제공자</span>
                        <div className="locked-provider-display">
                            <strong>{renewingProviderItem.displayName}</strong>
                            <span className="status-pill pill-done">✓ 연결 지원 제공자</span>
                        </div>
                    </div>

                    <label className="form-field-full" htmlFor="renew-display-name">
                        <span>
                            연결 이름 <span className="field-required-mark" aria-hidden="true">*</span>
                        </span>
                        <input
                            id="renew-display-name"
                            value={renewDisplayName}
                            onChange={(event) => setRenewDisplayName(event.target.value)}
                            maxLength={100}
                            placeholder="예: 개인 토스 계좌 (갱신)"
                            autoComplete="off"
                            disabled={isAnyActionPending}
                            required
                            aria-required="true"
                        />
                    </label>

                    {renewingProviderItem.credentialFields.map((field) =>
                        renderCredentialFieldInput(
                            field,
                            renewCredentials[field.key] ?? "",
                            (val) => handleRenewCredentialChange(field.key, val),
                            "renew-cred",
                            renewingProviderItem.credentialFields.length === 1,
                        ),
                    )}
                </div>

                <div className="broker-connection-feedback" aria-live="polite">
                    {formError ? <p className="form-error-message" role="alert">{formError}</p> : null}
                    {!formError && !isRenewValid ? (
                        <p className="form-helper-note" role="note">
                            * 새로운 연결 이름과 모든 필수(*) 자격 증명 항목을 입력하면 갱신 저장이 활성화됩니다.
                        </p>
                    ) : null}
                </div>

                <div className="form-actions renewal-form-actions">
                    <button
                        type="button"
                        className="secondary-button"
                        onClick={handleCancelRenew}
                        disabled={isAnyActionPending}
                    >
                        갱신 취소
                    </button>
                    <button
                        type="submit"
                        className="primary-button"
                        disabled={!isRenewValid || isAnyActionPending}
                    >
                        {isSubmitting ? "암호화하여 갱신 저장 중..." : "새 자격 증명으로 갱신 저장"}
                    </button>
                </div>
            </form>
        );
    };

    const renderConnectionEditorForm = () => (
        <form className="broker-connection-editor" onSubmit={submit}>
            <h3>{selectedProviderItem ? `${selectedProviderItem.displayName} 연결 등록` : "증권사 연결 등록"}</h3>

            {isProvidersLoading ? (
                <p className="status-message" aria-live="polite">증권사 제공자 목록을 불러오는 중입니다.</p>
            ) : providersError ? (
                <RequestError message={providersError} onRetry={loadCatalog} retryLabel="증권사 목록 다시 시도"/>
            ) : !providers || providers.length === 0 ? (
                <p className="empty-state">선택할 수 있는 증권사가 없습니다.</p>
            ) : (
                <>
                    <div className="form-grid">
                        <label className="form-field-full" htmlFor="broker-provider-select">
                            <span>
                                증권사 선택 <span className="field-required-mark" aria-hidden="true">*</span>
                            </span>
                            <select
                                id="broker-provider-select"
                                value={selectedProvider}
                                onChange={(event) => handleProviderChange(event.target.value)}
                                disabled={isAnyActionPending}
                                aria-required="true"
                            >
                                {providers.map((item) => {
                                    const isToss = item.provider === "TOSS_SECURITIES";
                                    return (
                                        <option
                                            key={item.provider}
                                            value={item.provider}
                                            disabled={!item.connectable}
                                        >
                                            {item.displayName}
                                            {isToss
                                                ? " (현재 실제 연동 가능)"
                                                : item.connectable
                                                  ? " (연결 지원)"
                                                  : " (연결 준비 중 / 현재 미지원)"}
                                        </option>
                                    );
                                })}
                            </select>
                        </label>

                        {selectedProviderItem ? (
                            <div className="form-field-full broker-provider-card" role="region" aria-label={`${selectedProviderItem.displayName} 제공자 정보`}>
                                <div className="broker-provider-meta-row">
                                    <div className="provider-market-info">
                                        <span className="provider-meta-label">지원 시장:</span>
                                        <span className="provider-meta-value">
                                            {selectedProviderItem.supportedMarkets.map(formatMarket).join(", ")}
                                        </span>
                                        {selectedProviderItem.ledgerWritableMarkets && selectedProviderItem.ledgerWritableMarkets.length > 0 && (
                                            <span className="provider-meta-sub">
                                                (원장 반영 가능: {selectedProviderItem.ledgerWritableMarkets.map(formatMarket).join(", ")})
                                            </span>
                                        )}
                                    </div>
                                    <div className="provider-connection-status-info">
                                        <span className={`status-pill ${selectedProviderItem.connectable ? "pill-done" : "pill-wait"}`}>
                                            {selectedProviderItem.provider === "TOSS_SECURITIES" && selectedProviderItem.connectable
                                                ? "✓ 현재 유일 가용 제공자"
                                                : selectedProviderItem.connectable
                                                  ? "✓ 연결 지원"
                                                  : "! 연결 준비 중 (현재 미지원)"}
                                        </span>
                                    </div>
                                </div>

                                <p className="provider-market-policy-note">
                                    * Trade Guide 매매 원장은 달러(USD) 단일 통화 기준이므로, 한국 주식(KR)은 잔고 조회 및 스냅샷 비교만 가능하며 매매 원장 반영은 미국 주식(US)으로 제한됩니다.
                                </p>

                                <div className="broker-capabilities-section">
                                    <div className="capabilities-header">
                                        <span className="provider-meta-label">제공자 기능 지원 현황</span>
                                        <span className="capabilities-header-desc">
                                            (제공자가 선언한 기능 중 현재 환경에서 실제 이용 가능한 기능을 표시합니다)
                                        </span>
                                    </div>
                                    <div className="broker-capabilities-list" role="list">
                                        {selectedProviderItem.supportedCapabilities.map((cap) => {
                                            const isAvailable = Boolean(selectedProviderItem.availableCapabilities?.includes(cap));
                                            const meta = CAPABILITY_METADATA[cap];
                                            const label = meta?.label ?? cap;
                                            return (
                                                <div
                                                    key={cap}
                                                    role="listitem"
                                                    className={`broker-capability-item ${isAvailable ? "is-available" : "is-pending"}`}
                                                    title={meta?.description}
                                                >
                                                    <span className="capability-name">{label}</span>
                                                    <span className={`capability-badge ${isAvailable ? "badge-available" : "badge-pending"}`}>
                                                        {isAvailable ? "현재 이용 가능" : "준비 중"}
                                                    </span>
                                                </div>
                                            );
                                        })}
                                    </div>
                                </div>
                            </div>
                        ) : null}

                        <label className="form-field-full" htmlFor="broker-connection-display-name">
                            <span>
                                연결 이름 <span className="field-required-mark" aria-hidden="true">*</span>
                            </span>
                            <input
                                id="broker-connection-display-name"
                                value={displayName}
                                onChange={(event) => setDisplayName(event.target.value)}
                                maxLength={100}
                                placeholder={selectedProviderItem ? `예: 개인 ${selectedProviderItem.displayName}` : "예: 개인 증권 계좌"}
                                autoComplete="off"
                                disabled={isAnyActionPending}
                                required
                                aria-required="true"
                            />
                        </label>

                        {selectedProviderItem?.credentialFields.map((field) =>
                            renderCredentialFieldInput(
                                field,
                                credentials[field.key] ?? "",
                                (val) => handleCredentialChange(field.key, val),
                                "broker-cred",
                                selectedProviderItem.credentialFields.length === 1,
                            ),
                        )}
                    </div>

                    <div className="broker-connection-feedback" aria-live="polite">
                        {formError ? <p className="form-error-message" role="alert">{formError}</p> : null}
                        {success ? <p className="form-success-message" role="status">{success}</p> : null}
                        {!formError && !success && unavailableMessage ? (
                            <p className="form-warning-message" role="status">{unavailableMessage}</p>
                        ) : null}
                        {!formError && !success && !unavailableMessage && !isFormValid && selectedProviderItem ? (
                            <p className="form-helper-note" role="note">
                                * 연결 이름과 모든 필수(*) 자격 증명 항목을 입력하면 연결 저장이 활성화됩니다.
                            </p>
                        ) : null}
                    </div>

                    <div className="form-actions">
                        <button
                            type="submit"
                            className="primary-button"
                            disabled={!isFormValid || isAnyActionPending}
                        >
                            {isSubmitting
                                ? "암호화하여 저장 중..."
                                : `${selectedProviderItem ? selectedProviderItem.displayName : "증권사"} 연결 저장`}
                        </button>
                    </div>
                </>
            )}
        </form>
    );

    const renderConnectionItemCard = (connection: BrokerConnection, isVerificationStep = false) => {
        const providerInfo = providers?.find((p) => p.provider === connection.provider);
        const providerLabel = providerInfo?.displayName ?? (connection.provider === "TOSS_SECURITIES" ? "토스증권" : connection.provider);
        const isConnected = connection.status === "CONNECTED";
        const isBeingVerified = verifyingConnectionId === connection.id;
        const isBeingDeleted = deletingConnectionId === connection.id;

        return (
            <li key={connection.id} className="broker-connection-item-card">
                <div className="broker-conn-main-info">
                    <div className="broker-conn-title-row">
                        <strong className="broker-conn-title">{connection.displayName}</strong>
                        <span className={`status-pill ${isConnected ? "pill-done" : "pill-action"}`}>
                            {isConnected ? "✓ 연결 확인됨" : "! 연결 확인 전"}
                        </span>
                        <span className="broker-conn-provider-pill">{providerLabel}</span>
                    </div>

                    <div className="broker-conn-meta-details">
                        <div className="broker-conn-meta-item">
                            <span className="meta-label">대표 계좌:</span>
                            <span className="meta-value">
                                {connection.maskedAccountLabel ?? connection.accounts[0]?.maskedAccountNumber ?? "확인 전"}
                            </span>
                        </div>
                        <div className="broker-conn-meta-item">
                            <span className="meta-label">등록 일시:</span>
                            <span className="meta-value">{formatDateTime(connection.createdAt)}</span>
                        </div>
                        {connection.lastVerifiedAt ? (
                            <div className="broker-conn-meta-item">
                                <span className="meta-label">최근 확인:</span>
                                <span className="meta-value">{formatDateTime(connection.lastVerifiedAt)}</span>
                            </div>
                        ) : null}
                    </div>

                    {connection.accounts.length > 0 ? (
                        <div className="broker-conn-accounts-preview">
                            <span className="accounts-preview-label">
                                연동 가능 계좌 ({connection.accounts.length}개):
                            </span>
                            <div className="accounts-preview-list">
                                {connection.accounts.map((acc) => (
                                    <span key={acc.id} className="account-tag">
                                        {acc.maskedAccountNumber}
                                        {acc.accountType ? ` (${acc.accountType})` : ""}
                                    </span>
                                ))}
                            </div>
                        </div>
                    ) : isConnected ? (
                        <p className="broker-no-accounts-note">증권사에서 반환된 계좌가 없습니다.</p>
                    ) : null}

                    <div className="broker-secret-security-note">
                        <span className="security-icon" aria-hidden="true">🔒</span>
                        <span>
                            자격 증명 보안: Client Secret 등 인증 비밀값은 AES-256-GCM으로 암호화 보관되며, 보안 정책상 화면에 절대 재노출되지 않습니다.
                        </span>
                    </div>
                </div>

                <div className="broker-connection-actions">
                    <button
                        type="button"
                        className={isConnected && !isVerificationStep ? "quiet-action" : "primary-button"}
                        onClick={() => void verify(connection.id, connection.displayName)}
                        disabled={isAnyActionPending}
                        aria-label={`${connection.displayName} ${isConnected ? "연결 다시 확인" : "연결 확인"}`}
                    >
                        {isBeingVerified
                            ? "확인 중..."
                            : isConnected
                              ? "다시 확인"
                              : "연결 확인"}
                    </button>
                    <button
                        type="button"
                        className="secondary-button"
                        onClick={() => handleRenewClick(connection)}
                        disabled={isAnyActionPending}
                        title="보안을 위해 기존 값을 노출하지 않고 새 자격 증명을 입력해 갱신합니다."
                        aria-label={`${connection.displayName} 자격 증명 갱신`}
                    >
                        자격 증명 갱신
                    </button>
                    <button
                        type="button"
                        className="quiet-action danger-action"
                        onClick={() => void disconnect(connection.id)}
                        disabled={isAnyActionPending}
                        aria-label={`${connection.displayName} 연결 해제`}
                    >
                        {isBeingDeleted ? "해제 중..." : "연결 해제"}
                    </button>
                </div>
            </li>
        );
    };

    // Step 1: Registration View
    if (viewMode === "registration") {
        return (
            <section className="content-section broker-connection-section">
                <div className="section-heading">
                    <div>
                        <p className="section-label">1. BROKER CONNECTION</p>
                        <h2>증권사 연결 등록</h2>
                    </div>
                </div>
                <p className="section-description">
                    증권사 접근 정보(API 키)는 안전하게 암호화하여 저장합니다. 등록을 마치면 2단계에서 연결을 확인하고 연동 계좌를 조회합니다.
                </p>

                {renderHonestProviderNotice()}

                {isLoading ? (
                    <p className="status-message" aria-live="polite">증권사 연결 정보를 불러오는 중입니다.</p>
                ) : null}
                {loadError ? (
                    <RequestError message={loadError} onRetry={loadConnections} retryLabel="증권사 연결 다시 시도"/>
                ) : null}

                {connections && connections.length > 0 ? (
                    <>
                        <div className="broker-connected-list-header">
                            <h3>등록된 증권사 연결 ({connections.length}건)</h3>
                        </div>
                        <ul className="broker-connection-list">
                            {connections.map((connection) => renderConnectionItemCard(connection, false))}
                        </ul>

                        {renewingConnection ? (
                            renderCredentialRenewalForm()
                        ) : (
                            <div className="broker-form-toggle-row">
                                <button
                                    type="button"
                                    className="secondary-button"
                                    onClick={() => setIsAddFormOpen((prev) => !prev)}
                                    disabled={isAnyActionPending}
                                >
                                    {isAddFormOpen ? "연결 등록 폼 닫기" : "+ 새 증권사 연결 추가"}
                                </button>
                            </div>
                        )}
                    </>
                ) : null}

                {(!connections || connections.length === 0 || isAddFormOpen) && !renewingConnection ? (
                    renderConnectionEditorForm()
                ) : null}

                <div className="broker-step-nav-footer">
                    <div />
                    {connections && connections.length > 0 ? (
                        <button
                            type="button"
                            className="primary-button"
                            onClick={onNextStep}
                        >
                            다음: 2단계 연결 확인 진행 →
                        </button>
                    ) : null}
                </div>
            </section>
        );
    }

    // Step 2: Verification View
    if (viewMode === "verification") {
        return (
            <section className="content-section broker-connection-section">
                <div className="section-heading">
                    <div>
                        <p className="section-label">2. CONNECTION VERIFICATION</p>
                        <h2>증권사 연결 확인</h2>
                    </div>
                </div>
                <p className="section-description">
                    등록된 증권사 API 접근 정보가 유효한지 확인하고 연동 가능한 계좌 목록을 가져옵니다. 확인된 계좌는 3단계에서 포트폴리오에 연결할 수 있습니다.
                </p>

                {renderHonestProviderNotice()}

                {isLoading ? (
                    <p className="status-message" aria-live="polite">증권사 연결 정보를 불러오는 중입니다.</p>
                ) : null}
                {loadError ? (
                    <RequestError message={loadError} onRetry={loadConnections} retryLabel="증권사 연결 다시 시도"/>
                ) : null}

                {!isLoading && (!connections || connections.length === 0) ? (
                    <div className="empty-state empty-state-action">
                        <p>등록된 증권사 연결이 없습니다. 먼저 1단계에서 증권사 연결을 등록해 주세요.</p>
                        <button
                            type="button"
                            className="primary-button"
                            onClick={() => onGoToStep?.(1)}
                        >
                            1단계: 증권사 연결 등록으로 이동
                        </button>
                    </div>
                ) : null}

                {connections && connections.length > 0 ? (
                    <>
                        <ul className="broker-connection-list">
                            {connections.map((connection) => renderConnectionItemCard(connection, true))}
                        </ul>

                        {renewingConnection ? renderCredentialRenewalForm() : null}

                        <div className="broker-connection-feedback" aria-live="polite">
                            {formError ? <p className="form-error-message" role="alert">{formError}</p> : null}
                            {success ? <p className="form-success-message" role="status">{success}</p> : null}
                        </div>

                        {hasVerifiedConnection ? (
                            <div className="broker-verification-success-card" role="note">
                                <p>
                                    <strong>✓ 증권사 연결 확인 완료:</strong> API 접근이 확인되었으며 연동 가능한 계좌가 준비되었습니다. 다음 3단계에서 포트폴리오에 연결할 계좌를 선택하세요.
                                </p>
                            </div>
                        ) : null}
                    </>
                ) : null}

                <div className="broker-step-nav-footer">
                    <button
                        type="button"
                        className="secondary-button"
                        onClick={onPrevStep}
                    >
                        ← 이전: 1단계 연결 등록
                    </button>
                    {hasVerifiedConnection ? (
                        <button
                            type="button"
                            className="primary-button"
                            onClick={onNextStep}
                        >
                            다음: 3단계 계좌 선택 진행 →
                        </button>
                    ) : null}
                </div>
            </section>
        );
    }

    // Default / "all" view (e.g. on SettingsPage)
    return (
        <AccordionSection
            id="broker-connections"
            className="broker-connection-section"
            sectionLabel="1. BROKER CONNECTION"
            title="증권사 연결 등록"
            defaultOpen={false}
            summary={
                connections && connections.length > 0
                    ? `연결 ${connections.length}건`
                    : !isLoading && !loadError
                        ? "연결 없음"
                        : undefined
            }
        >
            <p className="section-description">
                증권사 접근 정보는 암호화해 저장합니다. 연결한 계좌의 보유 종목은 사용자가 직접 갱신해 스냅샷으로 저장하고 비교할 수 있으며, 주문 전송이나 자동 동기화는 실행하지 않습니다.
            </p>

            {renderHonestProviderNotice()}

            {isLoading ? (
                <p className="status-message" aria-live="polite">증권사 연결 정보를 불러오는 중입니다.</p>
            ) : null}
            {loadError ? (
                <RequestError message={loadError} onRetry={loadConnections} retryLabel="증권사 연결 다시 시도"/>
            ) : null}

            {connections && connections.length > 0 ? (
                <>
                    <div className="broker-connected-list-header">
                        <h3>등록된 증권사 연결 ({connections.length}건)</h3>
                    </div>
                    <ul className="broker-connection-list">
                        {connections.map((connection) => renderConnectionItemCard(connection, false))}
                    </ul>
                </>
            ) : !isLoading && !loadError ? (
                <p className="empty-state">연결된 증권사가 없습니다.</p>
            ) : null}

            {renewingConnection ? renderCredentialRenewalForm() : renderConnectionEditorForm()}
        </AccordionSection>
    );
}
