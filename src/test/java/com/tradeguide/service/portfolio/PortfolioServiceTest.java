package com.tradeguide.service.portfolio;

import com.tradeguide.domain.member.Member;
import com.tradeguide.domain.portfolio.Portfolio;
import com.tradeguide.domain.risk.PortfolioRiskPolicy;
import com.tradeguide.repository.member.MemberRepository;
import com.tradeguide.repository.portfolio.PortfolioRepository;
import com.tradeguide.exception.PortfolioRiskPolicyNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.Optional;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

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

    @Test
    void updatesRiskPolicyWhenPortfolioExists() {
        Member member = new Member("risk@example.com", "risk-user");
        Portfolio portfolio = new Portfolio(member, "US Stocks");

        when(portfolioRepository.findByMember_IdAndId(1L, 10L))
                .thenReturn(Optional.of(portfolio));
        when(portfolioRepository.save(portfolio))
                .thenReturn(portfolio);

        PortfolioRiskPolicy riskPolicy = portfolioService.updateRiskPolicy(
                1L,
                10L,
                new BigDecimal("0.025"),
                new BigDecimal("0.125")
        );

        assertThat(riskPolicy.getMaxLossPerTradeRatio())
                .isEqualByComparingTo("0.025");
        assertThat(riskPolicy.getMaxSingleAssetExposureRatio())
                .isEqualByComparingTo("0.125");
        assertThat(portfolio.getRiskPolicy()).isSameAs(riskPolicy);
        verify(portfolioRepository).save(portfolio);
    }

    @Test
    void throwsExceptionWhenPortfolioDoesNotExistWhileUpdatingRiskPolicy() {
        when(portfolioRepository.findByMember_IdAndId(1L, 999L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> portfolioService.updateRiskPolicy(
                1L,
                999L,
                new BigDecimal("0.025"),
                new BigDecimal("0.125")
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("포트폴리오를 찾을 수 없습니다.");

        verify(portfolioRepository, never()).save(any(Portfolio.class));
    }

    @Test
    void getsRiskPolicyWhenConfigured() {
        Member member = new Member("risk@example.com", "risk-user");
        Portfolio portfolio = new Portfolio(member, "US Stocks");
        PortfolioRiskPolicy configuredPolicy = new PortfolioRiskPolicy(
                new BigDecimal("0.025"),
                new BigDecimal("0.125")
        );
        portfolio.changeRiskPolicy(configuredPolicy);

        when(portfolioRepository.findByMember_IdAndId(1L, 10L))
                .thenReturn(Optional.of(portfolio));

        PortfolioRiskPolicy riskPolicy = portfolioService.getRiskPolicy(1L, 10L);

        assertThat(riskPolicy).isSameAs(configuredPolicy);
    }

    @Test
    void throwsExceptionWhenRiskPolicyIsNotConfigured() {
        Member member = new Member("risk@example.com", "risk-user");
        Portfolio portfolio = new Portfolio(member, "US Stocks");

        when(portfolioRepository.findByMember_IdAndId(1L, 10L))
                .thenReturn(Optional.of(portfolio));

        assertThatThrownBy(() -> portfolioService.getRiskPolicy(1L, 10L))
                .isInstanceOf(PortfolioRiskPolicyNotFoundException.class)
                .hasMessage("포트폴리오 위험 한도 정책이 설정되지 않았습니다.");
    }
}