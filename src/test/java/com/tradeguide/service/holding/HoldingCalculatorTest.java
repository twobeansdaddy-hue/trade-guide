package com.tradeguide.service.holding;

import com.tradeguide.domain.holding.Holding;
import com.tradeguide.domain.member.Member;
import com.tradeguide.domain.portfolio.Portfolio;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.domain.trade.TradeTransaction;
import com.tradeguide.domain.trade.TradeType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HoldingCalculatorTest {

    private final HoldingCalculator holdingCalculator =
            new HoldingCalculator();

    private final Portfolio portfolio = new Portfolio(
            new Member("beans@example.com", "beans"),
            "US Stocks"
    );

    @Test
    void calculatesAveragePurchasePriceAfterMultipleBuys() {
        List<TradeTransaction> transactions = List.of(
                createTransaction(
                        TradeType.BUY,
                        "10",
                        "100.00",
                        "0.10",
                        "2026-07-30T13:30:00Z"
                ),
                createTransaction(
                        TradeType.BUY,
                        "10",
                        "120.00",
                        "0.10",
                        "2026-07-30T15:30:00Z"
                )
        );

        List<Holding> holdings =
                holdingCalculator.calculate(transactions);

        assertThat(holdings).hasSize(1);

        Holding holding = holdings.get(0);
        assertThat(holding.getQuantity())
                .isEqualByComparingTo("20");
        assertThat(holding.getAveragePurchasePrice())
                .isEqualByComparingTo("110.01");
    }

    @Test
    void keepsAveragePurchasePriceAfterSell() {
        List<TradeTransaction> transactions = List.of(
                createTransaction(
                        TradeType.BUY,
                        "10",
                        "100.00",
                        "0.10",
                        "2026-07-30T13:30:00Z"
                ),
                createTransaction(
                        TradeType.SELL,
                        "2",
                        "120.00",
                        "0.10",
                        "2026-07-30T15:30:00Z"
                )
        );

        List<Holding> holdings =
                holdingCalculator.calculate(transactions);

        Holding holding = holdings.get(0);
        assertThat(holding.getQuantity())
                .isEqualByComparingTo("8");
        assertThat(holding.getAveragePurchasePrice())
                .isEqualByComparingTo("100.01");
    }

    @Test
    void throwsExceptionWhenSellQuantityExceedsHoldingQuantity() {
        List<TradeTransaction> transactions = List.of(
                createTransaction(
                        TradeType.BUY,
                        "10",
                        "100.00",
                        "0.10",
                        "2026-07-30T13:30:00Z"
                ),
                createTransaction(
                        TradeType.SELL,
                        "11",
                        "120.00",
                        "0.10",
                        "2026-07-30T15:30:00Z"
                )
        );

        assertThatThrownBy(() ->
                holdingCalculator.calculate(transactions)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("매도 수량이 보유 수량보다 많습니다.");
    }

    private TradeTransaction createTransaction(
            TradeType tradeType,
            String quantity,
            String executedPrice,
            String fee,
            String tradedAt
    ) {
        return new TradeTransaction(
                portfolio,
                Market.US,
                "AAPL",
                tradeType,
                new BigDecimal(quantity),
                new BigDecimal(executedPrice),
                new BigDecimal(fee),
                Instant.parse(tradedAt)
        );
    }

    @Test
    void calculatesTransactionsInTradeTimeOrderEvenWhenInputIsUnordered() {
        List<TradeTransaction> transactions = List.of(
                createTransaction(
                        TradeType.SELL,
                        "2",
                        "120.00",
                        "0.10",
                        "2026-07-30T15:30:00Z"
                ),
                createTransaction(
                        TradeType.BUY,
                        "10",
                        "100.00",
                        "0.10",
                        "2026-07-30T13:30:00Z"
                )
        );

        List<Holding> holdings =
                holdingCalculator.calculate(transactions);

        Holding holding = holdings.get(0);
        assertThat(holding.getQuantity())
                .isEqualByComparingTo("8");
        assertThat(holding.getAveragePurchasePrice())
                .isEqualByComparingTo("100.01");
    }
}