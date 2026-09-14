import {Link} from "react-router-dom";

type MarketDataProviderPrerequisiteNoticeProps = {
    message?: string;
    className?: string;
};

export default function MarketDataProviderPrerequisiteNotice({
    message,
    className,
}: MarketDataProviderPrerequisiteNoticeProps) {
    return (
        <section
            className={`empty-state empty-state-action valuation-prerequisite-notice${className ? ` ${className}` : ""}`}
            role="status"
            aria-label="시장 데이터 제공자 설정 안내"
        >
            <div className="valuation-prerequisite-body">
                <p className="valuation-prerequisite-title">시장 데이터 제공자 설정 필요</p>
                <p className="valuation-prerequisite-message">
                    {message || "현재가 및 평가 조회를 위한 시장 데이터 제공자 서버 설정이 필요합니다. 설정에서 제공자 상태를 확인해 주세요."}
                </p>
            </div>
            <Link
                className="quiet-action valuation-prerequisite-action"
                to="/settings#market-data-provider"
            >
                시장 데이터 설정
            </Link>
        </section>
    );
}
