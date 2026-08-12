package com.tradeguide.repository.portfolio;

import com.tradeguide.domain.member.Member;
import com.tradeguide.domain.portfolio.Portfolio;
import com.tradeguide.repository.member.MemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
public class PortfolioRepositoryTest {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PortfolioRepository portfolioRepository;

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
}
