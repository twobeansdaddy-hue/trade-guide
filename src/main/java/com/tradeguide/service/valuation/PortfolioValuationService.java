package com.tradeguide.service.valuation;

import com.tradeguide.domain.asset.AssetListing;
import com.tradeguide.domain.asset.ListingStatus;
import com.tradeguide.domain.holding.Holding;
import com.tradeguide.domain.market.MarketPrice;
import com.tradeguide.domain.valuation.HoldingValuation;
import com.tradeguide.domain.valuation.PortfolioValuation;
import com.tradeguide.repository.asset.AssetListingRepository;
import com.tradeguide.service.holding.HoldingService;
import com.tradeguide.service.market.MarketPriceProvider;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PortfolioValuationService {

    private final HoldingService holdingService;
    private final MarketPriceProvider marketPriceProvider;
    private final AssetListingRepository assetListingRepository;
    private final HoldingValuationCalculator holdingValuationCalculator;
    private final PortfolioValuationCalculator portfolioValuationCalculator;

    public PortfolioValuationService(
            HoldingService holdingService,
            MarketPriceProvider marketPriceProvider,
            AssetListingRepository assetListingRepository,
            HoldingValuationCalculator holdingValuationCalculator,
            PortfolioValuationCalculator portfolioValuationCalculator

    ) {
        this.holdingService = holdingService;
        this.marketPriceProvider = marketPriceProvider;
        this.assetListingRepository = assetListingRepository;
        this.holdingValuationCalculator = holdingValuationCalculator;
        this.portfolioValuationCalculator = portfolioValuationCalculator;
    }

    public PortfolioValuation getPortfolioValuation(
            Long memberId,
            Long portfolioId
    ) {
        List<Holding> holdings = holdingService.getHoldings(memberId, portfolioId);

        List<HoldingValuation> holdingValuations = holdings.stream()
                .map(holding -> {
                    assertTradableListing(holding);

                    MarketPrice marketPrice = marketPriceProvider.getCurrentPrice(
                            holding.getMarket(),
                            holding.getTicker()
                    );

                    return holdingValuationCalculator.calculate(
                            holding,
                            marketPrice
                    );
                })
                .toList();

        return portfolioValuationCalculator.calculate(holdingValuations);
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
