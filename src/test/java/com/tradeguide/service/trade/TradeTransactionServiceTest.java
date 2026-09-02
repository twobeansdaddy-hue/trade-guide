package com.tradeguide.service.trade;

import com.tradeguide.domain.member.Member;
import com.tradeguide.domain.portfolio.Portfolio;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.domain.trade.TradeTransaction;
import com.tradeguide.domain.trade.TradeType;
import com.tradeguide.repository.portfolio.PortfolioRepository;
import com.tradeguide.repository.trade.TradeTransactionRepository;
import com.tradeguide.service.holding.HoldingCalculator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TradeTransactionServiceTest {

    @Mock
    private PortfolioRepository portfolioRepository;

    @Mock
    private TradeTransactionRepository tradeTransactionRepository;

    @Mock
    private HoldingCalculator holdingCalculator;

    @InjectMocks
    private TradeTransactionService tradeTransactionService;

    @Test
    void createsTradeTransactionWhenPortfolioIsOwnedByMember() {
        Portfolio portfolio = new Portfolio(
                new Member("beans@example.com", "beans"),
                "US Stocks"
        );

        when(portfolioRepository.findByMember_IdAndId(10L, 100L))
                .thenReturn(Optional.of(portfolio));
        when(tradeTransactionRepository
                .findAllByPortfolio_IdOrderByTradedAtAsc(100L))
                .thenReturn(List.of());
        when(tradeTransactionRepository.save(any(TradeTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TradeTransaction transaction =
                tradeTransactionService.createTradeTransaction(
                        10L,
                        100L,
                        Market.US,
                        " aapl ",
                        TradeType.BUY,
                        new BigDecimal("10"),
                        new BigDecimal("100.00"),
                        new BigDecimal("0.10"),
                        Instant.parse("2026-08-03T13:30:00Z")
                );

        assertThat(transaction.getTicker()).isEqualTo("AAPL");
        assertThat(transaction.getTradeType()).isEqualTo(TradeType.BUY);

        verify(holdingCalculator).calculate(anyList());
        verify(tradeTransactionRepository)
                .save(any(TradeTransaction.class));
    }

    @Test
    void doesNotSaveTransactionWhenHoldingCalculatorDetectsOversell() {
        Portfolio portfolio = new Portfolio(
                new Member("beans@example.com", "beans"),
                "US Stocks"
        );

        when(portfolioRepository.findByMember_IdAndId(10L, 100L))
                .thenReturn(Optional.of(portfolio));
        when(tradeTransactionRepository
                .findAllByPortfolio_IdOrderByTradedAtAsc(100L))
                .thenReturn(List.of());
        when(holdingCalculator.calculate(anyList()))
                .thenThrow(new IllegalArgumentException(
                        "매도 수량이 보유 수량보다 많습니다."
                ));

        assertThatThrownBy(() ->
                tradeTransactionService.createTradeTransaction(
                        10L,
                        100L,
                        Market.US,
                        "AAPL",
                        TradeType.SELL,
                        new BigDecimal("1"),
                        new BigDecimal("120.00"),
                        BigDecimal.ZERO,
                        Instant.parse("2026-08-03T15:30:00Z")
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("매도 수량이 보유 수량보다 많습니다.");

        verify(tradeTransactionRepository, never())
                .save(any(TradeTransaction.class));
    }

    @Test
    void throwsExceptionWhenPortfolioIsNotOwnedByMember() {
        when(portfolioRepository.findByMember_IdAndId(777L, 999L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                tradeTransactionService.createTradeTransaction(
                        777L,
                        999L,
                        Market.US,
                        "AAPL",
                        TradeType.BUY,
                        new BigDecimal("1"),
                        new BigDecimal("100.00"),
                        BigDecimal.ZERO,
                        Instant.parse("2026-08-03T13:30:00Z")
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("포트폴리오를 찾을 수 없습니다.");

        verifyNoInteractions(
                tradeTransactionRepository,
                holdingCalculator
        );
    }

    @Test
    void getsTradeTransactionsWhenPortfolioIsOwnedByMember() {
        Portfolio portfolio = new Portfolio(
                new Member("beans@example.com", "beans"),
                "US Stocks"
        );
        TradeTransaction transaction = new TradeTransaction(
                portfolio,
                Market.US,
                "AAPL",
                TradeType.BUY,
                new BigDecimal("10"),
                new BigDecimal("100.00"),
                BigDecimal.ZERO,
                Instant.parse("2026-08-03T13:30:00Z")
        );

        when(portfolioRepository.findByMember_IdAndId(10L, 100L))
                .thenReturn(Optional.of(portfolio));
        when(tradeTransactionRepository
                .findAllByPortfolio_IdOrderByTradedAtDesc(100L))
                .thenReturn(List.of(transaction));

        assertThat(tradeTransactionService.getTradeTransactions(10L, 100L))
                .containsExactly(transaction);
    }

    @Test
    void deletesTradeTransactionWhenRemainingHistoryIsValid() {
        Portfolio portfolio = new Portfolio(
                new Member("beans@example.com", "beans"),
                "US Stocks"
        );
        TradeTransaction transaction = org.mockito.Mockito.mock(
                TradeTransaction.class
        );
        when(transaction.getId()).thenReturn(500L);

        when(portfolioRepository.findByMember_IdAndId(10L, 100L))
                .thenReturn(Optional.of(portfolio));
        when(tradeTransactionRepository.findByPortfolio_IdAndId(100L, 500L))
                .thenReturn(Optional.of(transaction));
        when(tradeTransactionRepository
                .findAllByPortfolio_IdOrderByTradedAtAsc(100L))
                .thenReturn(List.of(transaction));

        tradeTransactionService.deleteTradeTransaction(10L, 100L, 500L);

        verify(holdingCalculator).calculate(argThat(List::isEmpty));
        verify(tradeTransactionRepository).delete(transaction);
    }

    @Test
    void doesNotDeleteTradeTransactionWhenRemainingHistoryBecomesInvalid() {
        Portfolio portfolio = new Portfolio(
                new Member("beans@example.com", "beans"),
                "US Stocks"
        );
        TradeTransaction transaction = org.mockito.Mockito.mock(
                TradeTransaction.class
        );
        TradeTransaction laterTransaction = org.mockito.Mockito.mock(
                TradeTransaction.class
        );
        when(transaction.getId()).thenReturn(500L);
        when(laterTransaction.getId()).thenReturn(501L);

        when(portfolioRepository.findByMember_IdAndId(10L, 100L))
                .thenReturn(Optional.of(portfolio));
        when(tradeTransactionRepository.findByPortfolio_IdAndId(100L, 500L))
                .thenReturn(Optional.of(transaction));
        when(tradeTransactionRepository
                .findAllByPortfolio_IdOrderByTradedAtAsc(100L))
                .thenReturn(List.of(transaction, laterTransaction));
        when(holdingCalculator.calculate(anyList()))
                .thenThrow(new IllegalArgumentException(
                        "매도 수량이 보유 수량보다 많습니다."
                ));

        assertThatThrownBy(() ->
                tradeTransactionService.deleteTradeTransaction(10L, 100L, 500L)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("매도 수량이 보유 수량보다 많습니다.");

        verify(tradeTransactionRepository, never()).delete(transaction);
    }
}
