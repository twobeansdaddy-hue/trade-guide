package com.tradeguide.service.portfolio;

import com.tradeguide.domain.member.Member;
import com.tradeguide.domain.portfolio.Portfolio;
import com.tradeguide.repository.member.MemberRepository;
import com.tradeguide.repository.portfolio.PortfolioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private PortfolioRepository portfolioRepository;

    @InjectMocks
    private PortfolioService portfolioService;

    @Test
    void createsPortfolioWhenMemberExists() {
        Member member = new Member("beansdaddy@example.com", "beansdaddy");

        when(memberRepository.findById(1L))
                .thenReturn(Optional.of(member));
        when(portfolioRepository.save(any(Portfolio.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Portfolio portfolio = portfolioService.createPortfolio(1L, "US Stocks");

        assertThat(portfolio.getName()).isEqualTo("US Stocks");
        assertThat(portfolio.getMember()).isSameAs(member);
        verify(portfolioRepository).save(any(Portfolio.class));
    }

    @Test
    void throwsExceptionWhenMemberDoesNotExist() {
        when(memberRepository.findById(999L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                portfolioService.createPortfolio(999L, "US Stocks")
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("회원을 찾을 수 없습니다.");

        verifyNoInteractions(portfolioRepository);
    }
}