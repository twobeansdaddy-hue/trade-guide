package com.tradeguide.service.valuation;

import com.tradeguide.domain.asset.AssetListing;
import com.tradeguide.domain.asset.ListingStatus;
import com.tradeguide.domain.holding.Holding;
import com.tradeguide.domain.market.MarketDataProvider;
import com.tradeguide.domain.market.MarketPrice;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.domain.valuation.HoldingValuation;
import com.tradeguide.domain.valuation.PortfolioValuation;
import com.tradeguide.exception.MarketDataUnavailableException;
import com.tradeguide.repository.asset.AssetListingRepository;
import com.tradeguide.service.holding.HoldingService;
import com.tradeguide.service.market.MarketDataProviderConfigurationStatus;
import com.tradeguide.service.market.MarketPriceProvider;
import com.tradeguide.service.market.MarketPriceProviderRegistry;
import com.tradeguide.service.portfolio.PortfolioService;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class PortfolioValuationService {

    private final HoldingService holdingService;
    private final MarketPriceProviderRegistry marketPriceProviderRegistry;
    private final AssetListingRepository assetListingRepository;
    private final HoldingValuationCalculator holdingValuationCalculator;
    private final PortfolioValuationCalculator portfolioValuationCalculator;
    private final PortfolioService portfolioService;
    private final MarketDataProviderConfigurationStatus marketDataProviderConfigurationStatus;

    public PortfolioValuationService(
            HoldingService holdingService,
            MarketPriceProviderRegistry marketPriceProviderRegistry,
            AssetListingRepository assetListingRepository,
            HoldingValuationCalculator holdingValuationCalculator,
            PortfolioValuationCalculator portfolioValuationCalculator,
            PortfolioService portfolioService,
            MarketDataProviderConfigurationStatus marketDataProviderConfigurationStatus
    ) {
        this.holdingService = holdingService;
        this.marketPriceProviderRegistry = marketPriceProviderRegistry;
        this.assetListingRepository = assetListingRepository;
        this.holdingValuationCalculator = holdingValuationCalculator;
        this.portfolioValuationCalculator = portfolioValuationCalculator;
        this.portfolioService = portfolioService;
        this.marketDataProviderConfigurationStatus = marketDataProviderConfigurationStatus;
    }

    public PortfolioValuation getPortfolioValuation(
            Long memberId,
            Long portfolioId
    ) {
        List<Holding> holdings = holdingService.getHoldings(memberId, portfolioId);

        if (holdings.isEmpty()) {
            return portfolioValuationCalculator.calculate(List.of());
        }

        MarketPriceProvider priceProvider = resolvePriceProvider(memberId, portfolioId);

        // 리스팅 검증을 모두 먼저 통과시킨다. 종목 하나라도 조회 불가하면 가격 조회 자체를
        // 시작하지 않아, 불필요한 외부 호출과 크레딧 소비를 만들지 않는다.
        holdings.forEach(this::assertTradableListing);

        Map<String, MarketPrice> prices = fetchPrices(priceProvider, holdings);

        List<HoldingValuation> holdingValuations = holdings.stream()
                .map(holding -> {
                    MarketPrice marketPrice = prices.get(priceKey(holding.getMarket(), holding.getTicker()));
                    if (marketPrice == null) {
                        throw new MarketDataUnavailableException(
                                "현재가를 조회하지 못했습니다: "
                                        + holding.getMarket() + " " + holding.getTicker()
                        );
                    }
                    return holdingValuationCalculator.calculate(holding, marketPrice);
                })
                .toList();

        return portfolioValuationCalculator.calculate(holdingValuations);
    }

    /**
     * 포트폴리오가 선택한 가격 제공자의 서버 설정 전제 조건을 확인한 뒤, 그 제공자를 실제로
     * 호출할 어댑터를 찾는다. 이 두 판정이 없으면 포트폴리오 설정과 무관하게 항상 같은
     * 제공자가 호출된다.
     */
    private MarketPriceProvider resolvePriceProvider(Long memberId, Long portfolioId) {
        MarketDataProvider priceProviderType = portfolioService
                .getMarketDataPreference(memberId, portfolioId)
                .getPriceProvider();

        marketDataProviderConfigurationStatus.requireConfigured(priceProviderType);

        return marketPriceProviderRegistry.resolve(priceProviderType, portfolioId);
    }

    /**
     * 보유 종목을 시장별로 묶어 시장마다 한 번씩만 다종목 배치 조회를 호출한다. 종목마다
     * 개별 호출하던 이전 방식보다 외부 호출 횟수를 줄인다(요청 합치기).
     */
    private Map<String, MarketPrice> fetchPrices(MarketPriceProvider priceProvider, List<Holding> holdings) {
        Map<Market, Set<String>> tickersByMarket = new LinkedHashMap<>();
        for (Holding holding : holdings) {
            tickersByMarket
                    .computeIfAbsent(holding.getMarket(), market -> new LinkedHashSet<>())
                    .add(holding.getTicker());
        }

        Map<String, MarketPrice> prices = new LinkedHashMap<>();
        tickersByMarket.forEach((market, tickers) -> {
            Map<String, MarketPrice> marketPrices = priceProvider.getCurrentPrices(market, List.copyOf(tickers));
            marketPrices.forEach((ticker, price) -> prices.put(priceKey(market, ticker), price));
        });

        return prices;
    }

    private String priceKey(Market market, String ticker) {
        return market.name() + ":" + ticker.toUpperCase(Locale.ROOT);
    }

    private void assertTradableListing(Holding holding) {
        AssetListing listing = assetListingRepository
                .findByMarketAndTicker(holding.getMarket(), holding.getTicker())
                .orElseThrow(() -> new IllegalArgumentException(
                        "거래 가능한 상장 정보를 찾을 수 없습니다: "
                                + holding.getMarket() + " / " + holding.getTicker()
                ));

        if (listing.getListingStatus() != ListingStatus.ACTIVE) {
            throw new IllegalArgumentException(
                    "비활성 상장 종목은 현재가를 조회할 수 없습니다: "
                            + holding.getMarket() + " / " + holding.getTicker()
            );
        }
    }
}
