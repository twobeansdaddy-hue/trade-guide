package com.tradeguide.service.market;

import com.tradeguide.domain.broker.BrokerConnection;
import com.tradeguide.domain.broker.BrokerConnectionStatus;
import com.tradeguide.domain.broker.BrokerCredentials;
import com.tradeguide.domain.broker.BrokerProvider;
import com.tradeguide.domain.market.CandleInterval;
import com.tradeguide.domain.market.MarketCandle;
import com.tradeguide.domain.market.MarketDataProvider;
import com.tradeguide.domain.portfolio.PortfolioBrokerLink;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.repository.broker.PortfolioBrokerLinkRepository;
import com.tradeguide.service.broker.BrokerCredentialLoader;
import com.tradeguide.service.broker.TossSecuritiesMarketHistoryProvider;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static com.tradeguide.domain.broker.BrokerCredentialsFixture.tossCredentials;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MarketHistoryProviderRegistryTest {

    @Test
    void preservesTossPageReceiptsAcrossPortfolioProviderRouting() {
        TossSecuritiesMarketHistoryProvider tossProvider = mock(TossSecuritiesMarketHistoryProvider.class);
        PortfolioBrokerLinkRepository links = mock(PortfolioBrokerLinkRepository.class);
        BrokerCredentialLoader credentialsLoader = mock(BrokerCredentialLoader.class);
        PortfolioBrokerLink link = mock(PortfolioBrokerLink.class);
        BrokerConnection connection = mock(BrokerConnection.class);
        BrokerCredentials credentials = tossCredentials("id", "secret");
        when(links.findByPortfolio_Id(10L)).thenReturn(Optional.of(link));
        when(link.getBrokerConnection()).thenReturn(connection);
        when(connection.getProvider()).thenReturn(BrokerProvider.TOSS_SECURITIES);
        when(connection.getStatus()).thenReturn(BrokerConnectionStatus.CONNECTED);
        when(credentialsLoader.load(connection)).thenReturn(credentials);
        Instant receivedAt = Instant.parse("2026-09-11T20:00:00Z");
        when(tossProvider.getCandlesWithReceipt(credentials, Market.US, "SOXL", CandleInterval.WEEKLY, 101))
                .thenReturn(new TossSecuritiesMarketHistoryProvider.TossCandleFetch(
                        List.<MarketCandle>of(), List.of(receivedAt), true));

        MarketHistoryProviderRegistry registry = new MarketHistoryProviderRegistry(
                List.of(), tossProvider, links, credentialsLoader);
        ObservedMarketCandles observed = registry.resolve(MarketDataProvider.TOSS_SECURITIES, 10L)
                .getObservedCandles(Market.US, "SOXL", CandleInterval.WEEKLY, 101);

        assertThat(observed.sourceReceipt()).isPresent();
        assertThat(observed.sourceReceipt().orElseThrow().pageReceivedAt())
                .containsExactly(receivedAt);
        assertThat(observed.sourceReceipt().orElseThrow().adjustedRequested()).isTrue();
    }
}
