package com.tradeguide.service.market;

import com.tradeguide.domain.broker.BrokerConnection;
import com.tradeguide.domain.broker.BrokerConnectionStatus;
import com.tradeguide.domain.broker.BrokerCredentials;
import com.tradeguide.domain.broker.BrokerProvider;
import com.tradeguide.domain.market.MarketDataProvider;
import com.tradeguide.domain.market.MarketPrice;
import com.tradeguide.domain.portfolio.PortfolioBrokerLink;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.dto.ApiErrorCode;
import com.tradeguide.exception.BrokerConnectionUnavailableException;
import com.tradeguide.exception.MarketDataProviderNotConfiguredException;
import com.tradeguide.exception.MarketDataUnavailableException;
import com.tradeguide.repository.broker.PortfolioBrokerLinkRepository;
import com.tradeguide.service.broker.BrokerCredentialLoader;
import com.tradeguide.service.broker.TossSecuritiesMarketPriceProvider;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 포트폴리오가 선택한 {@link MarketDataProvider}를 실제로 호출할 {@link MarketPriceProvider}로
 * 연결한다. {@link com.tradeguide.service.valuation.PortfolioValuationService}는 제공자를
 * 직접 분기하지 않고 이 레지스트리를 거친다.
 *
 * <p>Twelve Data는 서버 전역 자격 증명만 있으면 되는 상태 없는(stateless) 제공자라서
 * 싱글턴 빈을 그대로 돌려준다. 토스증권은 회원마다 다른 자격 증명이 필요한 상태 있는
 * 제공자라서, 이 레지스트리가 포트폴리오에 연결된 <b>검증된</b> 토스증권 연결을 찾아
 * 자격 증명을 미리 로딩한 어댑터를 그 자리에서 만들어 돌려준다. 검증된 연결이 없으면
 * {@link BrokerConnectionUnavailableException}으로 명확히 거부한다.
 *
 * <p>Yahoo Finance는 연구/백테스트 전용이며 이 레지스트리에 등록하지 않는다. 운영 라우팅
 * 경로로 들어오면(정상적으로는 {@link MarketDataProvider#isSelectable()}이 막지만, 방어적으로)
 * {@link MarketDataProviderNotConfiguredException}으로 거부한다.
 */
@Component
public class MarketPriceProviderRegistry {

    private final Map<MarketDataProvider, MarketPriceProvider> statelessProviders;
    private final TossSecuritiesMarketPriceProvider tossSecuritiesMarketPriceProvider;
    private final PortfolioBrokerLinkRepository portfolioBrokerLinkRepository;
    private final BrokerCredentialLoader brokerCredentialLoader;

    public MarketPriceProviderRegistry(
            List<MarketPriceProvider> statelessProviders,
            TossSecuritiesMarketPriceProvider tossSecuritiesMarketPriceProvider,
            PortfolioBrokerLinkRepository portfolioBrokerLinkRepository,
            BrokerCredentialLoader brokerCredentialLoader
    ) {
        this.statelessProviders = new EnumMap<>(MarketDataProvider.class);
        statelessProviders.forEach(provider -> this.statelessProviders.put(provider.getProvider(), provider));
        this.tossSecuritiesMarketPriceProvider = tossSecuritiesMarketPriceProvider;
        this.portfolioBrokerLinkRepository = portfolioBrokerLinkRepository;
        this.brokerCredentialLoader = brokerCredentialLoader;
    }

    /**
     * 포트폴리오 {@code portfolioId}가 {@code providerType}으로 현재가를 조회할 때 실제로
     * 호출할 어댑터를 반환한다.
     *
     * @throws BrokerConnectionUnavailableException 토스증권인데 포트폴리오에 연결된 검증된
     *         토스증권 연결이 없을 때
     * @throws MarketDataProviderNotConfiguredException 가격 라우팅을 지원하지 않는 제공자일 때
     *         (Yahoo Finance 등, 정상 경로에서는 도달하지 않는다)
     */
    public MarketPriceProvider resolve(MarketDataProvider providerType, Long portfolioId) {
        if (providerType == MarketDataProvider.TOSS_SECURITIES) {
            return resolveTossSecuritiesProvider(portfolioId);
        }

        MarketPriceProvider provider = statelessProviders.get(providerType);
        if (provider == null) {
            throw new MarketDataProviderNotConfiguredException(
                    providerType,
                    (providerType != null ? providerType.getDisplayName() : "선택한 제공자")
                            + "는 현재 가격 조회 라우팅을 지원하지 않습니다."
            );
        }
        return provider;
    }

    private MarketPriceProvider resolveTossSecuritiesProvider(Long portfolioId) {
        PortfolioBrokerLink link = portfolioBrokerLinkRepository.findByPortfolio_Id(portfolioId)
                .filter(candidate -> candidate.getBrokerConnection().getProvider() == BrokerProvider.TOSS_SECURITIES)
                .filter(candidate -> candidate.getBrokerConnection().getStatus() == BrokerConnectionStatus.CONNECTED)
                .orElseThrow(() -> new BrokerConnectionUnavailableException(
                        "토스증권 시장 데이터를 사용하려면 포트폴리오에 검증된 토스증권 연결이 연결되어 있어야 합니다.",
                        ApiErrorCode.BROKER_CONNECTION_UNAVAILABLE
                ));

        BrokerConnection connection = link.getBrokerConnection();
        BrokerCredentials credentials = brokerCredentialLoader.load(connection);

        return new TossBackedMarketPriceProvider(tossSecuritiesMarketPriceProvider, credentials);
    }

    /**
     * 이번 요청 동안만 쓰는, 자격 증명을 미리 붙인 어댑터다. 빈으로 등록하지 않는다
     * (자격 증명이 회원마다 다르므로 싱글턴으로 공유할 수 없다).
     */
    private static final class TossBackedMarketPriceProvider implements MarketPriceProvider {

        private final TossSecuritiesMarketPriceProvider delegate;
        private final BrokerCredentials credentials;

        private TossBackedMarketPriceProvider(
                TossSecuritiesMarketPriceProvider delegate,
                BrokerCredentials credentials
        ) {
            this.delegate = delegate;
            this.credentials = credentials;
        }

        @Override
        public MarketDataProvider getProvider() {
            return MarketDataProvider.TOSS_SECURITIES;
        }

        @Override
        public MarketPrice getCurrentPrice(Market market, String ticker) {
            Map<String, MarketPrice> prices = getCurrentPrices(market, List.of(ticker));
            MarketPrice price = prices.get(ticker.toUpperCase(Locale.ROOT));
            if (price == null) {
                throw new MarketDataUnavailableException("현재가를 찾을 수 없습니다: " + ticker);
            }
            return price;
        }

        @Override
        public Map<String, MarketPrice> getCurrentPrices(Market market, List<String> tickers) {
            return delegate.getCurrentPrices(credentials, market, tickers);
        }
    }
}
