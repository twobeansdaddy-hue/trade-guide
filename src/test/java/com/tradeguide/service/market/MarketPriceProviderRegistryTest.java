package com.tradeguide.service.market;

import com.tradeguide.domain.broker.BrokerAccount;
import com.tradeguide.domain.broker.BrokerConnection;
import com.tradeguide.domain.broker.BrokerConnectionStatus;
import com.tradeguide.domain.broker.BrokerCredentials;
import com.tradeguide.domain.broker.BrokerProvider;
import com.tradeguide.domain.market.MarketDataProvider;
import com.tradeguide.domain.market.MarketPrice;
import com.tradeguide.domain.portfolio.Portfolio;
import com.tradeguide.domain.portfolio.PortfolioBrokerLink;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.exception.BrokerConnectionUnavailableException;
import com.tradeguide.exception.MarketDataProviderNotConfiguredException;
import com.tradeguide.repository.broker.PortfolioBrokerLinkRepository;
import com.tradeguide.service.broker.BrokerCredentialLoader;
import com.tradeguide.service.broker.TossSecuritiesMarketPriceProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.tradeguide.domain.broker.BrokerCredentialsFixture.tossCredentials;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketPriceProviderRegistryTest {

    @Mock
    private TwelveDataMarketPriceProvider twelveDataMarketPriceProvider;

    @Mock
    private TossSecuritiesMarketPriceProvider tossSecuritiesMarketPriceProvider;

    @Mock
    private PortfolioBrokerLinkRepository portfolioBrokerLinkRepository;

    @Mock
    private BrokerCredentialLoader brokerCredentialLoader;

    @Test
    void resolvesTwelveDataAsTheStatelessSingletonBean() {
        when(twelveDataMarketPriceProvider.getProvider()).thenReturn(MarketDataProvider.TWELVE_DATA);
        MarketPriceProviderRegistry registry = new MarketPriceProviderRegistry(
                List.of(twelveDataMarketPriceProvider),
                tossSecuritiesMarketPriceProvider,
                portfolioBrokerLinkRepository,
                brokerCredentialLoader
        );

        MarketPriceProvider resolved = registry.resolve(MarketDataProvider.TWELVE_DATA, 100L);

        assertThat(resolved).isSameAs(twelveDataMarketPriceProvider);
    }

    @Test
    void resolvesTossSecuritiesUsingThePortfoliosVerifiedConnectionCredentials() {
        when(twelveDataMarketPriceProvider.getProvider()).thenReturn(MarketDataProvider.TWELVE_DATA);
        MarketPriceProviderRegistry registry = new MarketPriceProviderRegistry(
                List.of(twelveDataMarketPriceProvider),
                tossSecuritiesMarketPriceProvider,
                portfolioBrokerLinkRepository,
                brokerCredentialLoader
        );

        BrokerConnection connection = mock(BrokerConnection.class);
        when(connection.getProvider()).thenReturn(BrokerProvider.TOSS_SECURITIES);
        when(connection.getStatus()).thenReturn(BrokerConnectionStatus.CONNECTED);
        Portfolio portfolio = new Portfolio(mock(com.tradeguide.domain.member.Member.class), "US Stocks");
        PortfolioBrokerLink link = new PortfolioBrokerLink(
                portfolio, connection, mock(BrokerAccount.class), LocalDateTime.now()
        );

        BrokerCredentials credentials = tossCredentials();
        when(portfolioBrokerLinkRepository.findByPortfolio_Id(100L)).thenReturn(Optional.of(link));
        when(brokerCredentialLoader.load(connection)).thenReturn(credentials);

        MarketPrice price = new MarketPrice(Market.US, "AAPL", new BigDecimal("210.50"), Instant.now());
        when(tossSecuritiesMarketPriceProvider.getCurrentPrices(credentials, Market.US, List.of("AAPL")))
                .thenReturn(Map.of("AAPL", price));

        MarketPriceProvider resolved = registry.resolve(MarketDataProvider.TOSS_SECURITIES, 100L);

        assertThat(resolved.getProvider()).isEqualTo(MarketDataProvider.TOSS_SECURITIES);
        assertThat(resolved.getCurrentPrices(Market.US, List.of("AAPL"))).containsEntry("AAPL", price);
    }

    @Test
    void rejectsTossSecuritiesWhenPortfolioHasNoVerifiedLink() {
        when(twelveDataMarketPriceProvider.getProvider()).thenReturn(MarketDataProvider.TWELVE_DATA);
        MarketPriceProviderRegistry registry = new MarketPriceProviderRegistry(
                List.of(twelveDataMarketPriceProvider),
                tossSecuritiesMarketPriceProvider,
                portfolioBrokerLinkRepository,
                brokerCredentialLoader
        );

        when(portfolioBrokerLinkRepository.findByPortfolio_Id(100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> registry.resolve(MarketDataProvider.TOSS_SECURITIES, 100L))
                .isInstanceOf(BrokerConnectionUnavailableException.class);
    }

    @Test
    void rejectsTossSecuritiesWhenLinkedConnectionIsNotConnected() {
        when(twelveDataMarketPriceProvider.getProvider()).thenReturn(MarketDataProvider.TWELVE_DATA);
        MarketPriceProviderRegistry registry = new MarketPriceProviderRegistry(
                List.of(twelveDataMarketPriceProvider),
                tossSecuritiesMarketPriceProvider,
                portfolioBrokerLinkRepository,
                brokerCredentialLoader
        );

        BrokerConnection connection = mock(BrokerConnection.class);
        when(connection.getProvider()).thenReturn(BrokerProvider.TOSS_SECURITIES);
        when(connection.getStatus()).thenReturn(BrokerConnectionStatus.UNVERIFIED);
        Portfolio portfolio = new Portfolio(mock(com.tradeguide.domain.member.Member.class), "US Stocks");
        PortfolioBrokerLink link = new PortfolioBrokerLink(
                portfolio, connection, mock(BrokerAccount.class), LocalDateTime.now()
        );
        when(portfolioBrokerLinkRepository.findByPortfolio_Id(100L)).thenReturn(Optional.of(link));

        assertThatThrownBy(() -> registry.resolve(MarketDataProvider.TOSS_SECURITIES, 100L))
                .isInstanceOf(BrokerConnectionUnavailableException.class);
    }

    @Test
    void rejectsUnsupportedProviderLikeYahooFinance() {
        MarketPriceProviderRegistry registry = new MarketPriceProviderRegistry(
                List.of(),
                tossSecuritiesMarketPriceProvider,
                portfolioBrokerLinkRepository,
                brokerCredentialLoader
        );

        assertThatThrownBy(() -> registry.resolve(MarketDataProvider.YAHOO_FINANCE, 100L))
                .isInstanceOf(MarketDataProviderNotConfiguredException.class);
    }
}
