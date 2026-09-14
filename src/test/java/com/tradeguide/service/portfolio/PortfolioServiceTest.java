package com.tradeguide.service.portfolio;

import com.tradeguide.domain.broker.BrokerAccount;
import com.tradeguide.domain.broker.BrokerConnection;
import com.tradeguide.domain.broker.BrokerConnectionStatus;
import com.tradeguide.domain.broker.BrokerProvider;
import com.tradeguide.domain.member.Member;
import com.tradeguide.domain.market.MarketDataProvider;
import com.tradeguide.domain.market.PortfolioMarketDataPreference;
import com.tradeguide.domain.portfolio.Portfolio;
import com.tradeguide.domain.portfolio.PortfolioBrokerLink;
import com.tradeguide.domain.risk.PortfolioRiskPolicy;
import com.tradeguide.exception.BrokerConnectionUnavailableException;
import com.tradeguide.repository.broker.PortfolioBrokerLinkRepository;
import com.tradeguide.service.market.MarketDataProviderCatalog;
import com.tradeguide.repository.member.MemberRepository;
import com.tradeguide.repository.portfolio.PortfolioRepository;
import com.tradeguide.exception.PortfolioRiskPolicyNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.time.LocalDateTime;
import java.util.Optional;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
class PortfolioServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private PortfolioRepository portfolioRepository;

    @Mock
    private MarketDataProviderCatalog marketDataProviderCatalog;

    @Mock
    private PortfolioBrokerLinkRepository portfolioBrokerLinkRepository;

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

    @Test
    void updatesMarketDataPreferenceWhenProviderIsSelectable() {
        Member member = new Member("provider@example.com", "provider-user");
        Portfolio portfolio = new Portfolio(member, "US Stocks");

        when(portfolioRepository.findByMember_IdAndId(1L, 10L))
                .thenReturn(Optional.of(portfolio));
        when(portfolioRepository.save(portfolio)).thenReturn(portfolio);

        PortfolioMarketDataPreference preference = portfolioService.updateMarketDataPreference(
                1L,
                10L,
                MarketDataProvider.TWELVE_DATA
        );

        assertThat(preference.getPriceProvider()).isEqualTo(MarketDataProvider.TWELVE_DATA);
        assertThat(preference.getCandleProvider()).isEqualTo(MarketDataProvider.TWELVE_DATA);
        assertThat(preference.getAssetReferenceProvider()).isEqualTo(MarketDataProvider.TWELVE_DATA);
        verify(marketDataProviderCatalog).requireSelectable(MarketDataProvider.TWELVE_DATA);
        verify(portfolioRepository).save(portfolio);
    }

    @Test
    void doesNotChangeMarketDataPreferenceWhenProviderIsUnavailable() {
        Member member = new Member("provider@example.com", "provider-user");
        Portfolio portfolio = new Portfolio(member, "US Stocks");

        doThrow(new IllegalArgumentException("현재 선택할 수 없는 시장 데이터 제공자입니다."))
                .when(marketDataProviderCatalog)
                .requireSelectable(MarketDataProvider.TOSS_SECURITIES);

        assertThatThrownBy(() -> portfolioService.updateMarketDataPreference(
                1L,
                10L,
                MarketDataProvider.TOSS_SECURITIES
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("현재 선택할 수 없는 시장 데이터 제공자입니다.");

        assertThat(portfolio.getMarketDataPreference().getPriceProvider())
                .isEqualTo(MarketDataProvider.TWELVE_DATA);
        verifyNoInteractions(portfolioRepository);
    }

    @Test
    void selectsTossSecuritiesWhenPortfolioHasVerifiedTossConnection() {
        Member member = new Member("toss@example.com", "toss-user");
        Portfolio portfolio = new Portfolio(member, "US Stocks");

        BrokerConnection connection = mock(BrokerConnection.class);
        when(connection.getProvider()).thenReturn(BrokerProvider.TOSS_SECURITIES);
        when(connection.getStatus()).thenReturn(BrokerConnectionStatus.CONNECTED);

        PortfolioBrokerLink link = new PortfolioBrokerLink(
                portfolio, connection, mock(BrokerAccount.class), LocalDateTime.now()
        );

        when(portfolioRepository.findByMember_IdAndId(1L, 10L))
                .thenReturn(Optional.of(portfolio));
        when(portfolioBrokerLinkRepository.findByPortfolio_Id(portfolio.getId()))
                .thenReturn(Optional.of(link));
        when(portfolioRepository.save(portfolio)).thenReturn(portfolio);

        PortfolioMarketDataPreference preference = portfolioService.updateMarketDataPreference(
                1L,
                10L,
                MarketDataProvider.TOSS_SECURITIES
        );

        assertThat(preference.getPriceProvider()).isEqualTo(MarketDataProvider.TOSS_SECURITIES);
        verify(portfolioRepository).save(portfolio);
    }

    @Test
    void rejectsTossSecuritiesWhenPortfolioHasNoVerifiedTossConnection() {
        Member member = new Member("toss@example.com", "toss-user");
        Portfolio portfolio = new Portfolio(member, "US Stocks");

        when(portfolioRepository.findByMember_IdAndId(1L, 10L))
                .thenReturn(Optional.of(portfolio));
        when(portfolioBrokerLinkRepository.findByPortfolio_Id(portfolio.getId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> portfolioService.updateMarketDataPreference(
                1L,
                10L,
                MarketDataProvider.TOSS_SECURITIES
        ))
                .isInstanceOf(BrokerConnectionUnavailableException.class);

        assertThat(portfolio.getMarketDataPreference().getPriceProvider())
                .isEqualTo(MarketDataProvider.TWELVE_DATA);
        verify(portfolioRepository, never()).save(any(Portfolio.class));
    }
}
