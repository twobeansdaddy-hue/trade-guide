package com.tradeguide.service.holding;

import com.tradeguide.domain.holding.Holding;
import com.tradeguide.domain.member.Member;
import com.tradeguide.domain.portfolio.Portfolio;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.domain.trade.TradeTransaction;
import com.tradeguide.repository.portfolio.PortfolioRepository;
import com.tradeguide.repository.trade.TradeTransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HoldingServiceTest {

    @Mock
    private PortfolioRepository portfolioRepository;

    @Mock
    private TradeTransactionRepository tradeTransactionRepository;

    @Mock
    private HoldingCalculator holdingCalculator;

    @InjectMocks
    private HoldingService holdingService;

    @Test
    void getsHoldingsWhenPortfolioExists() {
        Portfolio portfolio = new Portfolio(
                new Member("beans@example.com", "beans"),
                "US Stocks"
        );
        List<TradeTransaction> transactions = List.of(
                mock(TradeTransaction.class)
        );
        List<Holding> expectedHoldings = List.of(
                new Holding(
                        Market.US,
                        "AAPL",
                        new BigDecimal("8"),
                        new BigDecimal("100.01")
                )
        );

        when(portfolioRepository.findByMember_IdAndId(10L, 100L))
                .thenReturn(Optional.of(portfolio));
        when(tradeTransactionRepository
                .findAllByPortfolio_IdOrderByTradedAtAsc(100L))
                .thenReturn(transactions);
        when(holdingCalculator.calculate(transactions))
                .thenReturn(expectedHoldings);

        List<Holding> holdings = holdingService.getHoldings(10L, 100L);

        assertThat(holdings).isSameAs(expectedHoldings);
        verify(tradeTransactionRepository)
                .findAllByPortfolio_IdOrderByTradedAtAsc(100L);
        verify(holdingCalculator).calculate(transactions);
    }

    @Test
    void throwsExceptionWhenPortfolioDoesNotExist() {
        when(portfolioRepository.findByMember_IdAndId(777L, 999L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> holdingService.getHoldings(777L, 999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("포트폴리오를 찾을 수 없습니다.");

        verifyNoInteractions(
                tradeTransactionRepository,
                holdingCalculator
        );
    }
}