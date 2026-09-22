import {useEffect, useState} from "react";
import { Link } from "react-router-dom";
import { createBrokerReconciliationRun, getBrokerReconciliationRuns } from "../../api/brokerReconciliationApi";
import { formatDateTime } from "../../utils/format";
import { StatusMessage } from "../common/StatusMessage";

export default function BrokerReconciliationSection({ memberId, portfolioId }: { memberId: number; portfolioId: number }) {
    const [latestRun, setLatestRun] = useState<any>(null);
    const [isLoading, setIsLoading] = useState(true);
    const [isCreating, setIsCreating] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [isExpanded, setIsExpanded] = useState(false);

    const loadLatestRun = () => {
        setIsLoading(true);
        getBrokerReconciliationRuns(memberId, portfolioId, 0, 1)
            .then(pageData => {
                setLatestRun(pageData.items[0] || null);
                setError(null);
            })
            .catch(_err => setError("점검 이력을 불러오지 못했습니다."))
            .finally(() => setIsLoading(false));
    };

    useEffect(() => {
        loadLatestRun();
    }, [memberId, portfolioId]);

    const handleCreateRun = async () => {
        setIsCreating(true);
        try {
            await createBrokerReconciliationRun(memberId, portfolioId);
            loadLatestRun();
            setIsExpanded(true);
        } catch (err: any) {
            setError(err.message || "점검을 실행하지 못했습니다.");
        } finally {
            setIsCreating(false);
        }
    };

    if (isLoading) {
        return <StatusMessage kind="loading" message="증권사 정합성을 확인하고 있습니다." />;
    }

    if (error) {
        return <StatusMessage kind="error" message={error} action={<button onClick={loadLatestRun}>다시 시도</button>} />;
    }

    if (!latestRun) {
        return (
            <div className="status-box status-empty" style={{flexDirection: "row", justifyContent: "space-between", padding: "16px 24px"}}>
                <p className="status-text">아직 증권사 정합성 점검 이력이 없습니다.</p>
                <div className="status-action">
                    <button onClick={handleCreateRun} disabled={isCreating}>
                        {isCreating ? "점검 중..." : "다시 점검"}
                    </button>
                </div>
            </div>
        );
    }

    const isMatched = latestRun.overallStatus === "MATCHED";

    return (
        <div className="status-box status-empty" style={{padding: "0", display: "block"}}>
            <div style={{display: "flex", justifyContent: "space-between", alignItems: "center", padding: "16px 24px"}}>
                <div style={{display: "flex", alignItems: "center", gap: "12px"}}>
                    <strong style={{color: isMatched ? "var(--gain)" : "var(--warn)", fontSize: "14px", fontFamily: "var(--head-font)"}}>
                        {isMatched ? "✓ 증권사 수량 일치" : "⚠ 증권사 수량 차이 발생"}
                    </strong>
                    <span style={{fontSize: "13px", color: "var(--muted)"}}>최근 점검: {formatDateTime(latestRun.executedAt)}</span>
                </div>
                <div style={{display: "flex", alignItems: "center", gap: "12px"}}>
                    <button onClick={() => setIsExpanded(!isExpanded)} style={{background: "none", border: "none", color: "var(--muted)", cursor: "pointer", fontSize: "13px"}}>
                        {isExpanded ? "요약 접기" : "요약 펼치기"}
                    </button>
                    <button onClick={handleCreateRun} disabled={isCreating} style={{background: "var(--accent)", color: "var(--accent-fg)", border: "none", borderRadius: "4px", padding: "6px 12px", fontSize: "13px", cursor: "pointer"}}>
                        {isCreating ? "점검 중..." : "다시 점검"}
                    </button>
                </div>
            </div>
            {isExpanded && (
                <div style={{padding: "16px 24px", borderTop: "1px solid var(--rule)", background: "var(--surface)", fontSize: "13px", color: "var(--ink)"}}>
                    <p style={{margin: "0 0 8px 0"}}>일치: {latestRun.matchedCount}건 / 불일치: {latestRun.quantityMismatchCount}건 / 증권사에만: {latestRun.onlyInBrokerCount}건 / 원장에만: {latestRun.onlyInTradeGuideCount}건</p>
                    <p style={{margin: "0", color: "var(--muted)"}}>자세한 내역은 <Link to="/broker-accounts">연동 계좌</Link> 메뉴에서 확인하세요.</p>
                </div>
            )}
        </div>
    );
}
