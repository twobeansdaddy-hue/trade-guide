package com.tradeguide.repository.trade;

import com.tradeguide.domain.member.Member;
import com.tradeguide.domain.portfolio.Portfolio;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.domain.trade.TradeTransaction;
import com.tradeguide.domain.trade.TradeType;
import com.tradeguide.repository.member.MemberRepository;
import com.tradeguide.repository.portfolio.PortfolioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class TradeTransactionRepositoryTest {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PortfolioRepository portfolioRepository;

    @Autowired
    private TradeTransactionRepository tradeTransactionRepository;

    @Test
    void findsTradeTransactionsByPortfolioInTradeTimeOrder() {
        Member member = memberRepository.save(
                new Member("beans@example.com", "beans")
        );
        Portfolio portfolio = portfolioRepository.save(
                new Portfolio(member, "US Stocks")
        );

        TradeTransaction laterTransaction = new TradeTransaction(
                portfolio,
                Market.US,
                "AAPL",
                TradeType.SELL,
                new BigDecimal("2"),
                new BigDecimal("120.00"),
                new BigDecimal("0.10"),
                Instant.parse("2026-07-30T15:30:00Z")
        );

        TradeTransaction earlierTransaction = new TradeTransaction(
                portfolio,
                Market.US,
                "AAPL",
                TradeType.BUY,
                new BigDecimal("10"),
                new BigDecimal("100.00"),
                new BigDecimal("0.10"),
                Instant.parse("2026-07-30T13:30:00Z")
        );

        tradeTransactionRepository.save(laterTransaction);
        tradeTransactionRepository.save(earlierTransaction);

        List<TradeTransaction> transactions =
                tradeTransactionRepository
                        .findAllByPortfolio_IdOrderByTradedAtAsc(
                                portfolio.getId()
                        );

        assertThat(transactions).hasSize(2);
        assertThat(transactions.get(0).getTradeType())
                .isEqualTo(TradeType.BUY);
        assertThat(transactions.get(1).getTradeType())
                .isEqualTo(TradeType.SELL);
    }
}