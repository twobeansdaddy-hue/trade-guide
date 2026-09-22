import {useEffect, useRef, useState} from "react";
import {Link} from "react-router-dom";
import {usePortfolioContext} from "../context/portfolioContext";
import {getBrokerConnections, getBrokerProviders} from "../api/brokerConnectionApi";
import {
    getLatestBrokerHoldingSnapshot,
    getPortfolioBrokerLinks,
} from "../api/portfolioBrokerApi";
import BrokerProgressStepper, {
    type BrokerStepItem,
} from "../components/broker/BrokerProgressStepper";
import BrokerConnectionSection from "../components/broker/BrokerConnectionSection";
import PortfolioBrokerLinkSection from "../components/broker/PortfolioBrokerLinkSection";
import BrokerHoldingSnapshotSection from "../components/broker/BrokerHoldingSnapshotSection";
import BrokerOrderImportSection from "../components/broker/BrokerOrderImportSection";
import type {BrokerConnection, BrokerProviderCatalogItem} from "../types/brokerConnection";
import type {PortfolioBrokerLink} from "../types/portfolioBroker";

export default function BrokerAccountsPage() {
    const {memberId, selectedPortfolioId} = usePortfolioContext();

    if (selectedPortfolioId === null) return null;

    return (
        <BrokerAccountsContent
            key={`${memberId}-${selectedPortfolioId}`}
            memberId={memberId}
            portfolioId={selectedPortfolioId}
        />
    );
}

function BrokerAccountsContent({
    memberId,
    portfolioId,
}: {
    memberId: number;
    portfolioId: number;
}) {
    const [connections, setConnections] = useState<BrokerConnection[] | null>(null);
    const [providers, setProviders] = useState<BrokerProviderCatalogItem[] | null>(null);
    const [linkedAccount, setLinkedAccount] = useState<PortfolioBrokerLink | null>(null);
    const [hasSnapshot, setHasSnapshot] = useState<boolean | null>(null);
    const [connectionRevision, setConnectionRevision] = useState(0);

    const [hash, setHash] = useState(window.location.hash);
    const [hasAutoNavigated, setHasAutoNavigated] = useState(false);
    const initialScrolledRef = useRef(false);

    useEffect(() => {
        const handleHashChange = () => setHash(window.location.hash);
        window.addEventListener("hashchange", handleHashChange);
        return () => window.removeEventListener("hashchange", handleHashChange);
    }, []);

    let activeStep = 1;
    let step5Tab: "opening-balance" | "order-history" = "opening-balance";
    if (hash === "#broker-holding-imports" || hash === "#broker-opening-balance") {
        activeStep = 5;
        step5Tab = "opening-balance";
    } else if (hash === "#broker-order-imports") {
        activeStep = 5;
        step5Tab = "order-history";
    } else {
        const match = hash.match(/^#step-([1-5])$/);
        if (match) activeStep = parseInt(match[1], 10);
    }

    // Load initial states to determine next actionable step
    useEffect(() => {
        let isCurrent = true;

        void Promise.all([
            getBrokerConnections(memberId).catch(() => [] as BrokerConnection[]),
            getPortfolioBrokerLinks(memberId, portfolioId).catch(() => [] as PortfolioBrokerLink[]),
            getLatestBrokerHoldingSnapshot(memberId, portfolioId)
                .then(() => true)
                .catch(() => false),
            getBrokerProviders().catch(() => [] as BrokerProviderCatalogItem[]),
        ]).then(([conns, links, snap, provs]) => {
            if (!isCurrent) return;

            setConnections(conns);
            setLinkedAccount(links[0] ?? null);
            setHasSnapshot(snap);
            setProviders(provs);

            // If user did not specify a deep-link hash, default to next actionable step
            if (!hasAutoNavigated && !window.location.hash) {
                const hasVerified = conns.some((c) => c.status === "CONNECTED" && c.accounts.length > 0);
                const nextStep = conns.length === 0 ? 1 : !hasVerified ? 2 : links.length === 0 ? 3 : 4;
                setHasAutoNavigated(true);
                window.history.replaceState(null, "", `#step-${nextStep}`);
                setHash(`#step-${nextStep}`);
            }
        });

        return () => { isCurrent = false; };
    }, [memberId, portfolioId, connectionRevision, hasAutoNavigated]);

    // Handle initial scrolling for deep links
    useEffect(() => {
        if (initialScrolledRef.current) return;
        if (hash === "#broker-holding-imports" && activeStep === 5 && step5Tab === "opening-balance") {
            const el = document.getElementById("broker-holding-imports");
            if (el) {
                initialScrolledRef.current = true;
                el.scrollIntoView({behavior: "smooth", block: "start"});
            }
        } else if (hash === "#broker-order-imports" && activeStep === 5 && step5Tab === "order-history") {
            const el = document.getElementById("broker-order-imports");
            if (el) {
                initialScrolledRef.current = true;
                el.scrollIntoView({behavior: "smooth", block: "start"});
            }
        }
    }, [activeStep, step5Tab, hash]);

    const goToStep = (step: number, initialStep5Tab?: "opening-balance" | "order-history") => {
        let targetHash = `#step-${step}`;
        if (step === 5) {
            const targetTab = initialStep5Tab ?? step5Tab;
            targetHash = targetTab === "order-history" ? "#broker-order-imports" : "#broker-opening-balance";
        }
        window.history.pushState(null, "", targetHash);
        setHash(targetHash);
        window.scrollTo({top: 0, behavior: "smooth"});
    };

    const handleStep5TabChange = (tab: "opening-balance" | "order-history") => {
        const targetHash = tab === "order-history" ? "#broker-order-imports" : "#broker-opening-balance";
        window.history.replaceState(null, "", targetHash);
        setHash(targetHash);
    };

    // Step statuses
    const step1Done = Boolean(connections && connections.length > 0);
    const step2Done = Boolean(
        connections && connections.some((c) => c.status === "CONNECTED" && c.accounts.length > 0),
    );
    const step3Done = Boolean(linkedAccount !== null);
    const step4Done = Boolean(hasSnapshot === true);

    const nextActionableStep = !step1Done
        ? 1
        : !step2Done
          ? 2
          : !step3Done
            ? 3
            : !step4Done
              ? 4
              : 5;

    const stepsData: BrokerStepItem[] = [
        {
            step: 1,
            id: "step-1",
            label: "연결 등록",
            shortLabel: "1. 등록",
            statusText: step1Done ? "등록 완료" : "등록 필요",
            isCompleted: step1Done,
            isCurrent: activeStep === 1,
            isActionable: nextActionableStep === 1,
        },
        {
            step: 2,
            id: "step-2",
            label: "연결 확인",
            shortLabel: "2. 확인",
            statusText: step2Done ? "확인 완료" : step1Done ? "확인 필요" : "대기",
            isCompleted: step2Done,
            isCurrent: activeStep === 2,
            isActionable: nextActionableStep === 2,
        },
        {
            step: 3,
            id: "step-3",
            label: "계좌 선택",
            shortLabel: "3. 선택",
            statusText: step3Done ? "연결됨" : step2Done ? "선택 필요" : "대기",
            isCompleted: step3Done,
            isCurrent: activeStep === 3,
            isActionable: nextActionableStep === 3,
        },
        {
            step: 4,
            id: "step-4",
            label: "보유 비교",
            shortLabel: "4. 비교",
            statusText: step4Done ? "비교 완료" : step3Done ? "갱신 필요" : "대기",
            isCompleted: step4Done,
            isCurrent: activeStep === 4,
            isActionable: nextActionableStep === 4,
        },
        {
            step: 5,
            id: "step-5",
            label: "개시 잔고 반영 및 이력",
            shortLabel: "5. 잔고/이력",
            statusText: step3Done ? "검토 가능" : "대기",
            isCompleted: false,
            isCurrent: activeStep === 5,
            isActionable: nextActionableStep === 5,
        },
    ];

    const activeProvider = connections?.[0];
    const matchedProvider = providers?.find((p) => p.provider === activeProvider?.provider);
    const activeProviderName =
        matchedProvider?.displayName ??
        (activeProvider?.provider === "TOSS_SECURITIES" ? "토스증권" : (activeProvider?.provider ?? null));

    return (
        <>
            <header className="page-header">
                <div className="page-header-top">
                    <div>
                        <p className="eyebrow">PORTFOLIO {portfolioId}</p>
                        <h1>연동 계좌</h1>
                        <p className="page-description">
                            증권사 계좌 연결, 보유 종목 비교, 개시 잔고 반영 및 주문 이력 검토를 순서대로 지원합니다.
                        </p>
                    </div>
                    <span className="page-header-meta">STEP {activeStep} / 5</span>
                </div>
                <div className="broker-accounts-safety-banner" role="note">
                    <span className="safety-badge">안전 가이드</span>
                    <p>
                        Trade Guide의 모든 증권사 연동 기능은 <strong>읽기 전용 조회 및 내부 매매 기록(원장) 반영</strong>에만 사용되며, <strong>증권사로 실제 매매 주문을 절대 내지 않습니다.</strong>
                    </p>
                </div>
            </header>

            {/* Stepper Navigation */}
            <BrokerProgressStepper
                steps={stepsData}
                onSelectStep={(step) => goToStep(step)}
            />

            {/* Context Summary Bar for later steps */}
            {activeStep >= 2 ? (
                <div className="broker-flow-context-card" role="region" aria-label="현재 연동 요약">
                    <div className="broker-flow-context-items">
                        <div className="broker-flow-context-col">
                            <span className="context-col-label">연결 증권사</span>
                            <strong>{activeProviderName ? `${activeProviderName} (${activeProvider?.displayName})` : "미등록"}</strong>
                            <span className={`status-pill ${step2Done ? "pill-done" : "pill-wait"}`}>
                                {step2Done ? "✓ API 확인됨" : step1Done ? "! 확인 필요" : "대기"}
                            </span>
                        </div>
                        <div className="broker-flow-context-divider" aria-hidden="true" />
                        <div className="broker-flow-context-col">
                            <span className="context-col-label">포트폴리오 연동 계좌</span>
                            <strong>{linkedAccount ? `${linkedAccount.displayName} · ${linkedAccount.maskedAccountNumber}` : "미연결"}</strong>
                            <span className={`status-pill ${step3Done ? "pill-done" : "pill-wait"}`}>
                                {step3Done ? "✓ 계좌 연결됨" : "계좌 선택 대기"}
                            </span>
                        </div>
                    </div>
                    <div className="broker-flow-context-actions">
                        {activeStep > 3 && (
                            <button
                                type="button"
                                className="quiet-action"
                                onClick={() => goToStep(3)}
                                aria-label="연동 계좌 변경하기"
                            >
                                계좌 변경
                            </button>
                        )}
                    </div>
                </div>
            ) : null}

            {/* Step 1: Broker connection registration */}
            {activeStep === 1 ? (
                <BrokerConnectionSection
                    memberId={memberId}
                    viewMode="registration"
                    onConnectionChanged={() => {
                        setConnectionRevision((rev) => rev + 1);
                    }}
                    onConnectionsChange={(updatedConns) => {
                        setConnections(updatedConns);
                    }}
                    onNextStep={() => goToStep(2)}
                />
            ) : null}

            {/* Step 2: Connection verification */}
            {activeStep === 2 ? (
                <BrokerConnectionSection
                    memberId={memberId}
                    viewMode="verification"
                    onConnectionChanged={() => {
                        setConnectionRevision((rev) => rev + 1);
                    }}
                    onConnectionsChange={(updatedConns) => {
                        setConnections(updatedConns);
                    }}
                    onPrevStep={() => goToStep(1)}
                    onNextStep={() => goToStep(3)}
                    onGoToStep={(step) => goToStep(step)}
                />
            ) : null}

            {/* Step 3: Portfolio account selection */}
            {activeStep === 3 ? (
                <PortfolioBrokerLinkSection
                    memberId={memberId}
                    portfolioId={portfolioId}
                    connectionRevision={connectionRevision}
                    onLinkedAccountChange={(acc) => {
                        setLinkedAccount(acc);
                    }}
                    onPrevStep={() => goToStep(2)}
                    onNextStep={() => goToStep(4)}
                    onGoToStep={(step) => goToStep(step)}
                />
            ) : null}

            {/* Step 4: Holding snapshot refresh and comparison */}
            {activeStep === 4 ? (
                linkedAccount ? (
                    <BrokerHoldingSnapshotSection
                        key={`snapshot-${memberId}-${portfolioId}`}
                        memberId={memberId}
                        portfolioId={portfolioId}
                        viewMode="comparison"
                        onPrevStep={() => goToStep(3)}
                        onNextStep={() => goToStep(5, "opening-balance")}
                        onGoToStep={(step, tab) => goToStep(step, tab)}
                        onSnapshotStateChange={(hasSnap) => setHasSnapshot(hasSnap)}
                    />
                ) : (
                    <section className="content-section broker-accounts-guide-section">
                        <div className="section-heading">
                            <div>
                                <p className="section-label">4. HOLDINGS SNAPSHOT & COMPARISON</p>
                                <h2>보유 종목 비교 대기</h2>
                            </div>
                        </div>
                        <div className="empty-state empty-state-action">
                            <p>포트폴리오에 연결된 증권사 계좌가 없습니다. 먼저 3단계에서 계좌를 연결해 주세요.</p>
                            <button
                                type="button"
                                className="primary-button"
                                onClick={() => goToStep(3)}
                            >
                                3단계: 포트폴리오 계좌 선택으로 이동
                            </button>
                        </div>
                    </section>
                )
            ) : null}

            {/* Step 5: Ledger actions & audit: opening balance & order history */}
            {activeStep === 5 ? (
                linkedAccount ? (
                    <section className="content-section broker-step5-section">
                        <div className="section-heading">
                            <div>
                                <p className="section-label">5. LEDGER IMPORT & AUDIT</p>
                                <h2>원장 반영 및 주문 이력 검토</h2>
                            </div>
                        </div>
                        <p className="section-description">
                            증권사 보유 종목 비교를 바탕으로 개시 잔고를 생성하거나, 주문 체결 이력을 검토하여 내부 매매 원장에 안전하게 반영합니다.
                        </p>

                        <div className="broker-reconciliation-context-link-card" role="note">
                            <div className="reconciliation-link-text">
                                <span className="reconciliation-link-badge">정합성 점검 안내</span>
                                <span>
                                    최신 증권사 스냅샷과 내부 원장의 수량 일치 여부 및 사유 후보를 확인하는
                                    <strong> 읽기 전용 정합성 점검</strong>은 보유 종목 화면에서 언제든 실행할 수 있습니다.
                                </span>
                            </div>
                            <Link
                                to="/holdings#broker-reconciliation"
                                className="quiet-action reconciliation-link-btn"
                            >
                                보유 종목에서 정합성 점검 보기 →
                            </Link>
                        </div>

                        {/* Sub-tab navigation for Step 5 */}
                        <div className="broker-step5-tabs" role="tablist" aria-label="원장 반영 및 검토 항목">
                            <button
                                type="button"
                                role="tab"
                                aria-selected={step5Tab === "opening-balance"}
                                className={`broker-step5-tab-btn ${step5Tab === "opening-balance" ? "active" : ""}`}
                                onClick={() => handleStep5TabChange("opening-balance")}
                            >
                                1. 개시 잔고 반영 및 이력
                            </button>
                            <button
                                type="button"
                                role="tab"
                                aria-selected={step5Tab === "order-history"}
                                className={`broker-step5-tab-btn ${step5Tab === "order-history" ? "active" : ""}`}
                                onClick={() => handleStep5TabChange("order-history")}
                            >
                                2. 증권사 주문 이력 검토
                            </button>
                        </div>

                        {step5Tab === "opening-balance" ? (
                            <BrokerHoldingSnapshotSection
                                key={`ob-${memberId}-${portfolioId}`}
                                memberId={memberId}
                                portfolioId={portfolioId}
                                viewMode="opening-balance"
                                onSnapshotStateChange={(hasSnap) => setHasSnapshot(hasSnap)}
                            />
                        ) : (
                            <BrokerOrderImportSection
                                key={`order-${memberId}-${portfolioId}`}
                                memberId={memberId}
                                portfolioId={portfolioId}
                                linkedAccount={linkedAccount}
                                onGoToStep={(step) => goToStep(step)}
                            />
                        )}

                        <div className="broker-step-nav-footer">
                            <button
                                type="button"
                                className="secondary-button"
                                onClick={() => goToStep(4)}
                            >
                                ← 이전: 4단계 보유 종목 비교
                            </button>
                        </div>
                    </section>
                ) : (
                    <section className="content-section broker-accounts-guide-section">
                        <div className="section-heading">
                            <div>
                                <p className="section-label">5. LEDGER IMPORT & AUDIT</p>
                                <h2>원장 반영 대기</h2>
                            </div>
                        </div>
                        <div className="empty-state empty-state-action">
                            <p>포트폴리오에 연결된 증권사 계좌가 없습니다. 먼저 3단계에서 계좌를 연결해 주세요.</p>
                            <button
                                type="button"
                                className="primary-button"
                                onClick={() => goToStep(3)}
                            >
                                3단계: 포트폴리오 계좌 선택으로 이동
                            </button>
                        </div>
                    </section>
                )
            ) : null}
        </>
    );
}
