package com.tradeguide.exception;

import com.tradeguide.domain.market.MarketDataProvider;

/**
 * 시장 데이터 제공자가 허용 IP 또는 자격 증명 문제로 접근을 거부했다.
 *
 * <p>{@link MarketDataUnavailableException}(일시적 호출 실패, 재시도 가능)과 구분한다.
 * 이 예외는 서버 IP를 제공자의 허용 목록에 등록하거나 자격 증명을 다시 확인해야 해결되며,
 * 단순 재시도로는 풀리지 않는다. {@link #getReason()}으로 두 원인을 구분해, 클라이언트가
 * 문구가 아니라 코드로 사용자에게 정확한 조치를 안내할 수 있게 한다.
 */
public class MarketDataProviderAccessDeniedException extends MarketDataUnavailableException {

    public enum Reason {
        /** 서버 IP가 제공자의 허용 IP 목록에 등록되어 있지 않다. */
        IP_NOT_ALLOWED,
        /** Client ID/Secret 등 자격 증명이 유효하지 않거나 만료되었다. */
        INVALID_CREDENTIALS
    }

    private final MarketDataProvider provider;
    private final Reason reason;

    public MarketDataProviderAccessDeniedException(
            MarketDataProvider provider,
            Reason reason,
            String message,
            Throwable cause
    ) {
        super(message, cause);
        this.provider = provider;
        this.reason = reason;
    }

    public MarketDataProvider getProvider() {
        return provider;
    }

    public Reason getReason() {
        return reason;
    }
}
