import {Link} from "react-router-dom";
import type {EmptyHoldingsGuidance} from "../../types/strategyGuide";

type Props = {
    guidance: EmptyHoldingsGuidance;
};

export default function StrategyGuideEmptyHoldingsNotice({guidance}: Props) {
    if (guidance.reason === "NO_BROKER_SNAPSHOT") {
        return (
            <div className="strategy-guide-empty-card" role="region" aria-label="보유 종목 전략 가이드 빈 상태 안내">
                <div className="strategy-guide-empty-header">
                    <span className="status-pill pill-neutral">원장 보유 종목 0건 · 스냅샷 미존재</span>
                </div>
                <h3 className="strategy-guide-empty-title">전략 가이드를 계산할 보유 종목이 없습니다</h3>
                <p className="strategy-guide-empty-message">{guidance.message}</p>
                <div className="strategy-guide-safety-notice" role="note">
                    <strong>안내:</strong> 전략 프로필 설정만으로는 보유 수량이 생성되지 않습니다.
                    매매 기록을 직접 등록하거나 증권사 계좌를 연동해 보유 종목을 먼저 반영해 주세요.
                </div>
                <div className="strategy-guide-empty-actions">
                    <Link to="/transactions/new" className="primary-button">
                        매매 기록 등록
                    </Link>
                    <Link to="/broker-accounts" className="secondary-button">
                        증권사 계좌 연동
                    </Link>
                </div>
            </div>
        );
    }

    if (guidance.reason === "BROKER_SNAPSHOT_NOT_REFLECTED") {
        return (
            <div className="strategy-guide-empty-card" role="region" aria-label="보유 종목 전략 가이드 빈 상태 안내">
                <div className="strategy-guide-empty-header">
                    <span className="status-pill pill-warning">증권사 스냅샷 미반영 · 원장 0건</span>
                </div>
                <h3 className="strategy-guide-empty-title">증권사 보유 종목이 아직 매매 원장에 반영되지 않았습니다</h3>
                <p className="strategy-guide-empty-message">{guidance.message}</p>
                <div className="strategy-guide-safety-notice warning" role="note">
                    <strong>안전 가이드:</strong> 증권사 연동은 읽기 전용 조회이며, 증권사로 주문을 전송하거나 내부 원장에 보유 종목을 자동으로 반영하지 않습니다. 개시 잔고를 검토하여 원장에 반영해 주세요.
                </div>
                <div className="strategy-guide-empty-actions">
                    <Link to="/broker-accounts#broker-opening-balance" className="primary-button">
                        개시 잔고 반영으로 이동
                    </Link>
                    <Link to="/transactions/new" className="secondary-button">
                        매매 기록 직접 등록
                    </Link>
                </div>
            </div>
        );
    }

    return (
        <div className="strategy-guide-empty-card" role="region" aria-label="보유 종목 전략 가이드 빈 상태 안내">
            <div className="strategy-guide-empty-header">
                <span className="status-pill pill-neutral">보유 종목 없음</span>
            </div>
            <h3 className="strategy-guide-empty-title">전략 가이드를 표시할 보유 종목이 없습니다</h3>
            <p className="strategy-guide-empty-message">{guidance.message}</p>
            <div className="strategy-guide-empty-actions">
                <Link to="/transactions/new" className="primary-button">
                    매매 기록 등록
                </Link>
                <Link to="/broker-accounts" className="secondary-button">
                    증권사 계좌 연동
                </Link>
            </div>
        </div>
    );
}
