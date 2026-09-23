package com.tradeguide.service.market;

import com.tradeguide.domain.broker.BrokerConnection;
import com.tradeguide.domain.broker.BrokerConnectionStatus;
import com.tradeguide.domain.broker.BrokerCredentials;
import com.tradeguide.domain.broker.BrokerProvider;
import com.tradeguide.domain.market.MarketCandle;
import com.tradeguide.domain.market.MarketDataProvider;
import com.tradeguide.domain.portfolio.PortfolioBrokerLink;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.dto.ApiErrorCode;
import com.tradeguide.exception.BrokerConnectionUnavailableException;
import com.tradeguide.exception.MarketDataProviderNotConfiguredException;
import com.tradeguide.repository.broker.PortfolioBrokerLinkRepository;
import com.tradeguide.service.broker.BrokerCredentialLoader;
import com.tradeguide.service.broker.TossSecuritiesMarketHistoryProvider;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** 포트폴리오의 캔들 제공자 설정을 실제 호출 어댑터로 연결한다. */
@Component
public class MarketHistoryProviderRegistry {

    private final Map<MarketDataProvider, MarketHistoryProvider> statelessProviders;
    private final TossSecuritiesMarketHistoryProvider tossSecuritiesMarketHistoryProvider;
    private final PortfolioBrokerLinkRepository portfolioBrokerLinkRepository;
    private final BrokerCredentialLoader brokerCredentialLoader;

    public MarketHistoryProviderRegistry(
            List<MarketHistoryProvider> statelessProviders,
            TossSecuritiesMarketHistoryProvider tossSecuritiesMarketHistoryProvider,
            PortfolioBrokerLinkRepository portfolioBrokerLinkRepository,
            BrokerCredentialLoader brokerCredentialLoader
    ) {
        this.statelessProviders = new EnumMap<>(MarketDataProvider.class);
        statelessProviders.forEach(provider -> this.statelessProviders.put(provider.getProvider(), provider));
        this.tossSecuritiesMarketHistoryProvider = tossSecuritiesMarketHistoryProvider;
        this.portfolioBrokerLinkRepository = portfolioBrokerLinkRepository;
        this.brokerCredentialLoader = brokerCredentialLoader;
    }

    public MarketHistoryProvider resolve(MarketDataProvider providerType, Long portfolioId) {
        if (providerType == MarketDataProvider.TOSS_SECURITIES) {
            return resolveTossSecuritiesProvider(portfolioId);
        }

        MarketHistoryProvider provider = statelessProviders.get(providerType);
        if (provider == null) {
            throw new MarketDataProviderNotConfiguredException(
                    providerType,
                    (providerType == null ? "선택한 제공자" : providerType.getDisplayName())
                            + "는 캔들 조회 라우팅을 지원하지 않습니다."
            );
        }
        return provider;
    }

    private MarketHistoryProvider resolveTossSecuritiesProvider(Long portfolioId) {
        PortfolioBrokerLink link = portfolioBrokerLinkRepository.findByPortfolio_Id(portfolioId)
                .filter(candidate -> candidate.getBrokerConnection().getProvider() == BrokerProvider.TOSS_SECURITIES)
                .filter(candidate -> candidate.getBrokerConnection().getStatus() == BrokerConnectionStatus.CONNECTED)
                .orElseThrow(() -> new BrokerConnectionUnavailableException(
                        "토스증권 시장 데이터를 사용하려면 포트폴리오에 검증된 토스증권 연결이 연결되어 있어야 합니다.",
                        ApiErrorCode.BROKER_CONNECTION_UNAVAILABLE
                ));

        BrokerConnection connection = link.getBrokerConnection();
        BrokerCredentials credentials = brokerCredentialLoader.load(connection);
        return new TossBackedMarketHistoryProvider(
                tossSecuritiesMarketHistoryProvider,
                credentials
        );
    }

    private static final class TossBackedMarketHistoryProvider implements MarketHistoryProvider {

        private final TossSecuritiesMarketHistoryProvider delegate;
        private final BrokerCredentials credentials;

        private TossBackedMarketHistoryProvider(
                TossSecuritiesMarketHistoryProvider delegate,
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
        public List<MarketCandle> getCandles(
                Market market,
                String ticker,
                com.tradeguide.domain.market.CandleInterval interval,
                int outputSize
        ) {
            return delegate.getCandles(credentials, market, ticker, interval, outputSize);
        }

        @Override
        public ObservedMarketCandles getObservedCandles(
                Market market, String ticker, com.tradeguide.domain.market.CandleInterval interval, int outputSize
        ) {
            TossSecuritiesMarketHistoryProvider.TossCandleFetch fetched =
                    delegate.getCandlesWithReceipt(credentials, market, ticker, interval, outputSize);
            return new ObservedMarketCandles(fetched.candles(), java.util.Optional.of(
                    new ObservedMarketCandles.SourceReceipt(
                            fetched.pageReceivedAt(), fetched.adjustedRequested())));
        }
    }
}
