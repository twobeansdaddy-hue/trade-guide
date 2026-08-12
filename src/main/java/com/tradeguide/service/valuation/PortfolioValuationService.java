package com.tradeguide.service.valuation;

import com.tradeguide.domain.holding.Holding;
import com.tradeguide.domain.market.MarketPrice;
import com.tradeguide.domain.valuation.HoldingValuation;
import com.tradeguide.domain.valuation.PortfolioValuation;
import com.tradeguide.service.holding.HoldingService;
import com.tradeguide.service.market.MarketPriceProvider;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PortfolioValuationService {

    private final HoldingService holdingService;
    private final MarketPriceProvider marketPriceProvider;
    private final HoldingValuationCalculator holdingValuationCalculator;
    private final PortfolioValuationCalculator portfolioValuationCalculator;

    public PortfolioValuationService(
            HoldingService holdingService,
            MarketPriceProvider marketPriceProvider,
            HoldingValuationCalculator holdingValuationCalculator,
            PortfolioValuationCalculator portfolioValuationCalculator

    ) {
        this.holdingService = holdingService;
        this.marketPriceProvider = marketPriceProvider;
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
}
