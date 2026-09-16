package com.tradeguide.service.valuation;

import com.tradeguide.domain.asset.AssetListing;
import com.tradeguide.domain.asset.ListingStatus;
import com.tradeguide.domain.holding.Holding;
import com.tradeguide.domain.market.MarketDataProvider;
import com.tradeguide.domain.market.PortfolioMarketDataPreference;
import com.tradeguide.domain.market.MarketPrice;
import com.tradeguide.domain.trade.Currency;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.domain.valuation.HoldingValuation;
import com.tradeguide.domain.valuation.PortfolioValuation;
import com.tradeguide.repository.asset.AssetListingRepository;
import com.tradeguide.exception.MarketDataProviderNotConfiguredException;
import com.tradeguide.exception.MarketDataUnavailableException;
import com.tradeguide.service.holding.HoldingService;
import com.tradeguide.service.market.MarketDataProviderConfigurationStatus;
import com.tradeguide.service.market.MarketPriceProvider;
import com.tradeguide.service.market.MarketPriceProviderRegistry;
import com.tradeguide.service.portfolio.PortfolioService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioValuationServiceTest {

    @Mock
    private HoldingService holdingService;

    @Mock
    private MarketPriceProviderRegistry marketPriceProviderRegistry;

    @Mock
    private MarketPriceProvider marketPriceProvider;

    @Mock
    private AssetListingRepository assetListingRepository;

    @Mock
    private HoldingValuationCalculator holdingValuationCalculator;

    @Mock
    private PortfolioValuationCalculator portfolioValuationCalculator;

    @Mock
    private PortfolioService portfolioService;

    @Mock
    private MarketDataProviderConfigurationStatus marketDataProviderConfigurationStatus;

    @InjectMocks
    private PortfolioValuationService portfolioValuationService;

    @Test
    void getsPortfolioValuation() {
        // given
        Holding holding = new Holding(
                Market.US,
                "AAPL",
                new BigDecimal("10"),
                new BigDecimal("100")
        );
        AssetListing listing = new AssetListing(
                Market.US,
                "AAPL",
                "Apple Inc.",
                ListingStatus.ACTIVE
        );
        MarketPrice marketPrice = new MarketPrice(
                Market.US,
                "AAPL",
                new BigDecimal("110"),
                Currency.USD,
                Instant.parse("2026-08-04T00:00:00Z")
        );
        HoldingValuation holdingValuation = new HoldingValuation(
                Market.US,
                "AAPL",
                new BigDecimal("10"),
                new BigDecimal("100"),
                new BigDecimal("110"),
                new BigDecimal("1000"),
                new BigDecimal("1100"),
                new BigDecimal("100"),
                new BigDecimal("10")
        );
        PortfolioValuation expected = new PortfolioValuation(
                List.of(holdingValuation),
                new BigDecimal("1000"),
                new BigDecimal("1100"),
                new BigDecimal("100"),
                new BigDecimal("10")
        );

        when(holdingService.getHoldings(10L, 100L))
                .thenReturn(List.of(holding));
        when(portfolioService.getMarketDataPreference(10L, 100L))
                .thenReturn(PortfolioMarketDataPreference.unified(
                        MarketDataProvider.TWELVE_DATA
                ));
        when(marketPriceProviderRegistry.resolve(MarketDataProvider.TWELVE_DATA, 100L))
                .thenReturn(marketPriceProvider);
        when(assetListingRepository.findByMarketAndTicker(Market.US, "AAPL"))
                .thenReturn(Optional.of(listing));
        when(marketPriceProvider.getCurrentPrices(Market.US, List.of("AAPL")))
                .thenReturn(Map.of("AAPL", marketPrice));
        when(holdingValuationCalculator.calculate(holding, marketPrice))
                .thenReturn(holdingValuation);
        when(portfolioValuationCalculator.calculate(List.of(holdingValuation)))
                .thenReturn(expected);

        // when
        PortfolioValuation result =
                portfolioValuationService.getPortfolioValuation(10L, 100L);

        // then
        assertThat(result).isSameAs(expected);
        verify(holdingService).getHoldings(10L, 100L);
        verify(marketPriceProvider).getCurrentPrices(Market.US, List.of("AAPL"));
        verify(holdingValuationCalculator).calculate(holding, marketPrice);
        verify(portfolioValuationCalculator)
                .calculate(List.of(holdingValuation));
    }

    @Test
    void rejectsHoldingWithoutAssetListingWithoutCallingMarketPriceProvider() {
        // given
        Holding holding = new Holding(
                Market.US,
                "DELISTEDX",
                new BigDecimal("10"),
                new BigDecimal("100")
        );

        when(holdingService.getHoldings(10L, 100L))
                .thenReturn(List.of(holding));
        when(portfolioService.getMarketDataPreference(10L, 100L))
                .thenReturn(PortfolioMarketDataPreference.unified(
                        MarketDataProvider.TWELVE_DATA
                ));
        when(marketPriceProviderRegistry.resolve(MarketDataProvider.TWELVE_DATA, 100L))
                .thenReturn(marketPriceProvider);
        when(assetListingRepository.findByMarketAndTicker(Market.US, "DELISTEDX"))
                .thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() ->
                portfolioValuationService.getPortfolioValuation(10L, 100L))
                .isInstanceOf(IllegalArgumentException.class);

        verify(marketPriceProvider, never()).getCurrentPrices(any(), any());
    }

    @Test
    void rejectsHoldingWithInactiveListingWithoutCallingMarketPriceProvider() {
        // given
        Holding holding = new Holding(
                Market.US,
                "OLDCO",
                new BigDecimal("10"),
                new BigDecimal("100")
        );
        AssetListing inactiveListing = new AssetListing(
                Market.US,
                "OLDCO",
                "Old Co.",
                ListingStatus.INACTIVE
        );

        when(holdingService.getHoldings(10L, 100L))
                .thenReturn(List.of(holding));
        when(portfolioService.getMarketDataPreference(10L, 100L))
                .thenReturn(PortfolioMarketDataPreference.unified(
                        MarketDataProvider.TWELVE_DATA
                ));
        when(marketPriceProviderRegistry.resolve(MarketDataProvider.TWELVE_DATA, 100L))
                .thenReturn(marketPriceProvider);
        when(assetListingRepository.findByMarketAndTicker(Market.US, "OLDCO"))
                .thenReturn(Optional.of(inactiveListing));

        // when / then
        assertThatThrownBy(() ->
                portfolioValuationService.getPortfolioValuation(10L, 100L))
                .isInstanceOf(IllegalArgumentException.class);

        verify(marketPriceProvider, never()).getCurrentPrices(any(), any());
    }

    @Test
    void failsFastWithPortfolioPriceProviderPrerequisiteWhenProviderIsNotConfigured() {
        // given
        Holding holding = new Holding(
                Market.US,
                "AAPL",
                new BigDecimal("10"),
                new BigDecimal("100")
        );

        when(holdingService.getHoldings(10L, 100L))
                .thenReturn(List.of(holding));
        when(portfolioService.getMarketDataPreference(10L, 100L))
                .thenReturn(PortfolioMarketDataPreference.unified(
                        MarketDataProvider.TWELVE_DATA
                ));
        doThrow(new MarketDataProviderNotConfiguredException(
                MarketDataProvider.TWELVE_DATA,
                "Twelve Data 시장 데이터 API 키가 서버에 설정되지 않았습니다."
        ))
                .when(marketDataProviderConfigurationStatus)
                .requireConfigured(MarketDataProvider.TWELVE_DATA);

        // when / then
        assertThatThrownBy(() ->
                portfolioValuationService.getPortfolioValuation(10L, 100L))
                .isInstanceOf(MarketDataProviderNotConfiguredException.class);

        verify(marketPriceProviderRegistry, never()).resolve(any(), any());
        verify(assetListingRepository, never())
                .findByMarketAndTicker(any(), any());
    }

    @Test
    void skipsPriceProviderPrerequisiteCheckWhenPortfolioHasNoHoldings() {
        // given
        PortfolioValuation expected = new PortfolioValuation(
                List.of(),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );

        when(holdingService.getHoldings(10L, 100L)).thenReturn(List.of());
        when(portfolioValuationCalculator.calculate(List.of()))
                .thenReturn(expected);

        // when
        PortfolioValuation result =
                portfolioValuationService.getPortfolioValuation(10L, 100L);

        // then
        assertThat(result).isSameAs(expected);
        verify(portfolioService, never()).getMarketDataPreference(any(), any());
        verify(marketDataProviderConfigurationStatus, never())
                .requireConfigured(any());
        verify(marketPriceProviderRegistry, never()).resolve(any(), any());
    }

    @Test
    void throwsWhenResolvedProviderOmitsARequestedTicker() {
        // given
        Holding holding = new Holding(
                Market.US,
                "AAPL",
                new BigDecimal("10"),
                new BigDecimal("100")
        );
        AssetListing listing = new AssetListing(
                Market.US,
                "AAPL",
                "Apple Inc.",
                ListingStatus.ACTIVE
        );

        when(holdingService.getHoldings(10L, 100L))
                .thenReturn(List.of(holding));
        when(portfolioService.getMarketDataPreference(10L, 100L))
                .thenReturn(PortfolioMarketDataPreference.unified(
                        MarketDataProvider.TWELVE_DATA
                ));
        when(marketPriceProviderRegistry.resolve(MarketDataProvider.TWELVE_DATA, 100L))
                .thenReturn(marketPriceProvider);
        when(assetListingRepository.findByMarketAndTicker(Market.US, "AAPL"))
                .thenReturn(Optional.of(listing));
        when(marketPriceProvider.getCurrentPrices(Market.US, List.of("AAPL")))
                .thenReturn(Map.of());

        // when / then
        assertThatThrownBy(() ->
                portfolioValuationService.getPortfolioValuation(10L, 100L))
                .isInstanceOf(MarketDataUnavailableException.class);
    }
}
