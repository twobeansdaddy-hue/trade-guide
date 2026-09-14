package com.tradeguide.service.market;

import com.tradeguide.domain.market.MarketDataProvider;
import com.tradeguide.domain.market.MarketPrice;
import com.tradeguide.domain.trade.Market;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 포트폴리오가 선택한 시장 데이터 제공자별 현재가 어댑터 계약이다.
 *
 * <p>어떤 구현체를 호출할지는 이 인터페이스가 아니라
 * {@link MarketPriceProviderRegistry}가 포트폴리오의 {@code priceProvider} 설정을 보고 결정한다.
 * 구현체는 자신이 어떤 {@link MarketDataProvider}인지만 {@link #getProvider()}로 선언한다.
 */
public interface MarketPriceProvider {

    MarketDataProvider getProvider();

    MarketPrice getCurrentPrice(Market market, String ticker);

    /**
     * 여러 종목의 현재가를 한 번에 조회한다. 기본 구현은 종목마다 {@link #getCurrentPrice}를
     * 호출하는 단순 폴백이며, 심볼당 크레딧을 소비하되 배치 호출을 지원하는 제공자
     * (예: Twelve Data)는 실제 다종목 배치 계약으로 재정의해 외부 호출 횟수를 줄인다.
     *
     * <p>기본 구현은 기존 단건 조회와 같은 실패 방식을 유지한다: 종목 하나라도 조회에
     * 실패하면 그 예외를 그대로 전파하고, 부분 실패를 조용히 감추지 않는다.
     *
     * @return 조회된 종목의 맵. 키는 대문자로 정규화한 티커다.
     */
    default Map<String, MarketPrice> getCurrentPrices(Market market, List<String> tickers) {
        Map<String, MarketPrice> result = new LinkedHashMap<>();

        for (String ticker : tickers) {
            String normalizedTicker = ticker.toUpperCase(Locale.ROOT);
            result.put(normalizedTicker, getCurrentPrice(market, normalizedTicker));
        }

        return result;
    }
}
