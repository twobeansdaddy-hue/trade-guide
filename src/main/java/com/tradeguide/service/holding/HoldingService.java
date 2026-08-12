package com.tradeguide.service.holding;

import com.tradeguide.domain.holding.Holding;
import com.tradeguide.domain.trade.TradeTransaction;
import com.tradeguide.repository.portfolio.PortfolioRepository;
import com.tradeguide.repository.trade.TradeTransactionRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class HoldingService {

    private final PortfolioRepository portfolioRepository;
    private final TradeTransactionRepository tradeTransactionRepository;
    private final HoldingCalculator holdingCalculator;

    public HoldingService(
            PortfolioRepository portfolioRepository,
            TradeTransactionRepository tradeTransactionRepository,
            HoldingCalculator holdingCalculator
    ) {
        this.portfolioRepository = portfolioRepository;
        this.tradeTransactionRepository = tradeTransactionRepository;
        this.holdingCalculator = holdingCalculator;
    }

    public List<Holding> getHoldings(Long memberId, Long portfolioId) {
        portfolioRepository.findByMember_IdAndId(memberId, portfolioId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "포트폴리오를 찾을 수 없습니다."
                        )
                );

        List<TradeTransaction> transactions =
                tradeTransactionRepository
                        .findAllByPortfolio_IdOrderByTradedAtAsc(
                                portfolioId
                        );

        return holdingCalculator.calculate(transactions);
    }
}