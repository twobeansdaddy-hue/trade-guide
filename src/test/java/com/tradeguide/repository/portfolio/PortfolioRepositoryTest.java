package com.tradeguide.repository.portfolio;

import com.tradeguide.domain.member.Member;
import com.tradeguide.domain.portfolio.Portfolio;
import com.tradeguide.domain.risk.PortfolioRiskPolicy;
import com.tradeguide.repository.member.MemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
public class PortfolioRepositoryTest {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PortfolioRepository portfolioRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void findsPortfoliosByMemberId() {
        Member member = memberRepository.save(
                new Member("beansdaddy@example.com", "beansdaddy")
        );

        Portfolio portfolio = portfolioRepository.save(
                new Portfolio(member, "US Stocks")
        );

        List<Portfolio> portfolios = portfolioRepository.findAllByMember_Id(member.getId());

        assertThat(portfolios).hasSize(1);
        assertThat(portfolios.get(0).getId()).isEqualTo(portfolio.getId());
        assertThat(portfolios.get(0).getName()).isEqualTo(portfolio.getName());

    }

    @Test
    void persistsRiskPolicyWithPortfolio() {
        Member member = memberRepository.save(
                new Member("risk@example.com", "risk-user")
        );

        Portfolio portfolio = new Portfolio(member, "US Stocks");
        portfolio.changeRiskPolicy(new PortfolioRiskPolicy(
                new BigDecimal("0.025"),
                new BigDecimal("0.125")
        ));

        Portfolio savedPortfolio = portfolioRepository.saveAndFlush(portfolio);

        entityManager.clear();

        Portfolio foundPortfolio = portfolioRepository.findById(savedPortfolio.getId())
                .orElseThrow();

        assertThat(foundPortfolio.getRiskPolicy().getMaxLossPerTradeRatio())
                .isEqualByComparingTo("0.025");
        assertThat(foundPortfolio.getRiskPolicy().getMaxSingleAssetExposureRatio())
                .isEqualByComparingTo("0.125");
    }
}
