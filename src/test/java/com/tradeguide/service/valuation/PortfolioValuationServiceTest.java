package com.tradeguide.service.valuation;

import com.tradeguide.domain.asset.AssetListing;
import com.tradeguide.domain.asset.ListingStatus;
import com.tradeguide.domain.holding.Holding;
import com.tradeguide.domain.market.MarketDataProvider;
import com.tradeguide.domain.market.PortfolioMarketDataPreference;
import com.tradeguide.domain.market.MarketPrice;
import com.tradeguide.domain.trade.Currency;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.domain.valuation.CurrencyValuationTotals;
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
                Map.of(Currency.USD, new CurrencyValuationTotals(
                        Currency.USD,
                        new BigDecimal("1000"),
                        new BigDecimal("1100"),
                        new BigDecimal("100"),
                        new BigDecimal("10")
                ))
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

    /**
     * 토스증권 가격 제공자를 쓰는 포트폴리오는 US·KR 보유 종목이 섞여 있어도 시장별로
     * 나눠 각각 한 번씩 배치 조회한다 - KR 원장 반영(2026-09-16)이 실제 시세 조회
     * 경로까지 문제없이 이어지는지 확인한다.
     */
    @Test
    void fetchesPricesSeparatelyPerMarketWhenHoldingsSpanUsAndKr() {
        // given
        Holding usHolding = new Holding(Market.US, "AAPL", new BigDecimal("10"), new BigDecimal("100"));
        Holding krHolding = new Holding(Market.KR, "005930", new BigDecimal("5"), new BigDecimal("70000"));

        AssetListing usListing = new AssetListing(Market.US, "AAPL", "Apple Inc.", ListingStatus.ACTIVE);
        AssetListing krListing = new AssetListing(Market.KR, "005930", "삼성전자", ListingStatus.ACTIVE);

        MarketPrice usPrice = new MarketPrice(
                Market.US, "AAPL", new BigDecimal("110"), Currency.USD, Instant.parse("2026-08-04T00:00:00Z"));
        MarketPrice krPrice = new MarketPrice(
                Market.KR, "005930", new BigDecimal("75000"), Currency.KRW, Instant.parse("2026-08-04T00:00:00Z"));

        HoldingValuation usValuation = new HoldingValuation(
                Market.US, "AAPL", new BigDecimal("10"), new BigDecimal("100"), new BigDecimal("110"),
                new BigDecimal("1000"), new BigDecimal("1100"), new BigDecimal("100"), new BigDecimal("10"));
        HoldingValuation krValuation = new HoldingValuation(
                Market.KR, "005930", new BigDecimal("5"), new BigDecimal("70000"), new BigDecimal("75000"),
                new BigDecimal("350000"), new BigDecimal("375000"), new BigDecimal("25000"), new BigDecimal("7.14"));

        when(holdingService.getHoldings(10L, 100L)).thenReturn(List.of(usHolding, krHolding));
        when(portfolioService.getMarketDataPreference(10L, 100L))
                .thenReturn(PortfolioMarketDataPreference.unified(MarketDataProvider.TOSS_SECURITIES));
        when(marketPriceProviderRegistry.resolve(MarketDataProvider.TOSS_SECURITIES, 100L))
                .thenReturn(marketPriceProvider);
        when(assetListingRepository.findByMarketAndTicker(Market.US, "AAPL"))
                .thenReturn(Optional.of(usListing));
        when(assetListingRepository.findByMarketAndTicker(Market.KR, "005930"))
                .thenReturn(Optional.of(krListing));
        when(marketPriceProvider.getCurrentPrices(Market.US, List.of("AAPL")))
                .thenReturn(Map.of("AAPL", usPrice));
        when(marketPriceProvider.getCurrentPrices(Market.KR, List.of("005930")))
                .thenReturn(Map.of("005930", krPrice));
        when(holdingValuationCalculator.calculate(usHolding, usPrice)).thenReturn(usValuation);
        when(holdingValuationCalculator.calculate(krHolding, krPrice)).thenReturn(krValuation);
        when(portfolioValuationCalculator.calculate(List.of(usValuation, krValuation)))
                .thenReturn(new PortfolioValuation(
                        List.of(usValuation, krValuation),
                        Map.of(
                                Currency.USD, new CurrencyValuationTotals(
                                        Currency.USD, new BigDecimal("1000"), new BigDecimal("1100"),
                                        new BigDecimal("100"), new BigDecimal("10")),
                                Currency.KRW, new CurrencyValuationTotals(
                                        Currency.KRW, new BigDecimal("350000"), new BigDecimal("375000"),
                                        new BigDecimal("25000"), new BigDecimal("7.14"))
                        )
                ));

        // when
        PortfolioValuation result = portfolioValuationService.getPortfolioValuation(10L, 100L);

        // then
        verify(marketPriceProvider).getCurrentPrices(Market.US, List.of("AAPL"));
        verify(marketPriceProvider).getCurrentPrices(Market.KR, List.of("005930"));
        assertThat(result.getTotalsFor(Currency.USD).getTotalMarketValue()).isEqualByComparingTo("1100");
        assertThat(result.getTotalsFor(Currency.KRW).getTotalMarketValue()).isEqualByComparingTo("375000");
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
        PortfolioValuation expected = new PortfolioValuation(List.of(), Map.of());

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
