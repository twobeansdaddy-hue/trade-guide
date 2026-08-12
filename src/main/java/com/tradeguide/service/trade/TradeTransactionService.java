package com.tradeguide.service.trade;

import com.tradeguide.domain.portfolio.Portfolio;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.domain.trade.TradeTransaction;
import com.tradeguide.domain.trade.TradeType;
import com.tradeguide.repository.portfolio.PortfolioRepository;
import com.tradeguide.repository.trade.TradeTransactionRepository;
import com.tradeguide.service.holding.HoldingCalculator;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class TradeTransactionService {

    private final PortfolioRepository portfolioRepository;
    private final TradeTransactionRepository tradeTransactionRepository;
    private final HoldingCalculator holdingCalculator;

    public TradeTransactionService(
            PortfolioRepository portfolioRepository,
            TradeTransactionRepository tradeTransactionRepository,
            HoldingCalculator holdingCalculator
    ) {
        this.portfolioRepository = portfolioRepository;
        this.tradeTransactionRepository = tradeTransactionRepository;
        this.holdingCalculator = holdingCalculator;
    }

    public TradeTransaction createTradeTransaction(
            Long memberId,
            Long portfolioId,
            Market market,
            String ticker,
            TradeType tradeType,
            BigDecimal quantity,
            BigDecimal executedPrice,
            BigDecimal fee,
            Instant tradedAt
    ) {
        validateInput(
                market,
                ticker,
                tradeType,
                quantity,
                executedPrice,
                fee,
                tradedAt
        );

        Portfolio portfolio = portfolioRepository
                .findByMember_IdAndId(memberId, portfolioId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "포트폴리오를 찾을 수 없습니다."
                        )
                );

        TradeTransaction transaction = new TradeTransaction(
                portfolio,
                market,
                ticker.trim().toUpperCase(Locale.ROOT),
                tradeType,
                quantity,
                executedPrice,
                fee,
                tradedAt
        );

        validateTransactionHistory(portfolioId, transaction);

        return tradeTransactionRepository.save(transaction);
    }

    private void validateInput(
            Market market,
            String ticker,
            TradeType tradeType,
            BigDecimal quantity,
            BigDecimal executedPrice,
            BigDecimal fee,
            Instant tradedAt
    ) {
        if (market == null) {
            throw new IllegalArgumentException("시장은 필수입니다.");
        }

        if (ticker == null || ticker.isBlank()) {
            throw new IllegalArgumentException("종목 코드는 필수입니다.");
        }

        if (tradeType == null) {
            throw new IllegalArgumentException("거래 유형은 필수입니다.");
        }

        if (quantity == null || quantity.signum() <= 0) {
            throw new IllegalArgumentException(
                    "거래 수량은 0보다 커야 합니다."
            );
        }

        if (executedPrice == null || executedPrice.signum() <= 0) {
            throw new IllegalArgumentException(
                    "체결 단가는 0보다 커야 합니다."
            );
        }

        if (fee == null || fee.signum() < 0) {
            throw new IllegalArgumentException("수수료는 0 이상이어야 합니다.");
        }

        if (tradedAt == null) {
            throw new IllegalArgumentException("체결 시각은 필수입니다.");
        }
    }

    private void validateTransactionHistory(
            Long portfolioId,
            TradeTransaction transaction
    ) {
        List<TradeTransaction> transactions = new ArrayList<>(
                tradeTransactionRepository
                        .findAllByPortfolio_IdOrderByTradedAtAsc(
                                portfolioId
                        )
        );

        transactions.add(transaction);

        holdingCalculator.calculate(transactions);
    }
}