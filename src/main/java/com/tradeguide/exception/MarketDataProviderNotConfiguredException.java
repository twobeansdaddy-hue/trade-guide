package com.tradeguide.exception;

import com.tradeguide.domain.market.MarketDataProvider;

/**
 * 시장 데이터 제공자의 서버 측 설정 전제 조건이 준비되지 않아 조회를 시작할 수 없는 상태다.
 *
 * <p>외부 제공자 호출이 실패한 상태({@link MarketDataUnavailableException})와 구분한다.
 * 이 예외는 아직 외부 호출을 시도하지 않았음을 뜻하므로 재시도로 해결되지 않으며,
 * 운영자 또는 사용자가 전제 조건을 갖춰야 한다.
 *
 * <p>기존 다종목 조회의 부분 실패 처리를 유지하기 위해
 * {@link MarketDataUnavailableException}을 상속한다.
 */
public class MarketDataProviderNotConfiguredException
        extends MarketDataUnavailableException {

    private final MarketDataProvider provider;

    public MarketDataProviderNotConfiguredException(
            MarketDataProvider provider,
            String message
    ) {
        super(message);
        this.provider = provider;
    }

    public MarketDataProvider getProvider() {
        return provider;
    }
}
