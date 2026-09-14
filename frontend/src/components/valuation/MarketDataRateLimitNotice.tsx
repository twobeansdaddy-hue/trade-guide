import {useEffect, useState} from "react";
import {Link} from "react-router-dom";

type MarketDataRateLimitNoticeProps = {
    message?: string;
    onRetry?: () => void;
    retryLabel?: string;
    isPreservedValuation?: boolean;
    className?: string;
    cooldownSeconds?: number;
};

const DEFAULT_COOLDOWN_SECONDS = 60;

export default function MarketDataRateLimitNotice({
    message,
    onRetry,
    retryLabel = "평가 다시 시도",
    isPreservedValuation = false,
    className,
    cooldownSeconds = DEFAULT_COOLDOWN_SECONDS,
}: MarketDataRateLimitNoticeProps) {
    const [prevCooldown, setPrevCooldown] = useState(cooldownSeconds);
    const [remainingSeconds, setRemainingSeconds] = useState(cooldownSeconds);
    const [isRetrying, setIsRetrying] = useState(false);

    if (prevCooldown !== cooldownSeconds) {
        setPrevCooldown(cooldownSeconds);
        setRemainingSeconds(cooldownSeconds);
    }

    useEffect(() => {
        if (remainingSeconds <= 0) return;

        const timer = window.setInterval(() => {
            setRemainingSeconds((current) => {
                if (current <= 1) {
                    window.clearInterval(timer);
                    return 0;
                }
                return current - 1;
            });
        }, 1000);

        return () => window.clearInterval(timer);
    }, [remainingSeconds]);

    const handleRetry = async () => {
        if (remainingSeconds > 0 || isRetrying) return;
        setIsRetrying(true);
        try {
            await onRetry?.();
        } finally {
            setIsRetrying(false);
            setRemainingSeconds(cooldownSeconds);
        }
    };

    const isCooldownActive = remainingSeconds > 0;
    const title = isPreservedValuation
        ? "시장 데이터 요청 한도 초과 (최근 평가 유지)"
        : "시장 데이터 요청 한도 초과";

    const defaultDescription = isPreservedValuation
        ? "외부 시장 데이터 제공자의 분당 요청 한도를 초과했습니다. 마지막으로 성공한 평가 데이터를 유지하고 있습니다."
        : "외부 시장 데이터 제공자의 분당 요청 한도를 초과하여 최신 시세를 조회하지 못했습니다.";

    const guidance = isCooldownActive
        ? `요청 폭주를 방지하기 위해 ${remainingSeconds}초 후 다시 시도할 수 있습니다.`
        : "대기 시간이 만료되었습니다. 다시 시도해 주세요.";

    const retryButtonText = isRetrying
        ? "재시도 중..."
        : isCooldownActive
            ? `${remainingSeconds}초 후 재시도 가능`
            : retryLabel;

    return (
        <section
            className={`market-data-rate-limit-notice ${
                isPreservedValuation ? "is-preserved" : "is-standalone"
            }${className ? ` ${className}` : ""}`}
            role="alert"
            aria-live="polite"
            aria-label={title}
        >
            <div className="rate-limit-notice-body">
                <div className="rate-limit-notice-header">
                    <p className="rate-limit-notice-title">{title}</p>
                    <span className="rate-limit-notice-badge">429 RATE LIMIT</span>
                </div>
                <p className="rate-limit-notice-message">{message || defaultDescription}</p>
                <p className="rate-limit-notice-guidance">{guidance}</p>
            </div>
            <div className="rate-limit-notice-actions">
                {onRetry ? (
                    <button
                        type="button"
                        className="rate-limit-retry-button"
                        disabled={isCooldownActive || isRetrying}
                        onClick={handleRetry}
                    >
                        {retryButtonText}
                    </button>
                ) : null}
                <Link
                    to="/settings#market-data-provider"
                    className="rate-limit-settings-link"
                >
                    시장 데이터 설정
                </Link>
            </div>
        </section>
    );
}
