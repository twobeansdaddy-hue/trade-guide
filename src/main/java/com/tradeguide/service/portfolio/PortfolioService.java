package com.tradeguide.service.portfolio;

import com.tradeguide.domain.member.Member;
import com.tradeguide.domain.portfolio.Portfolio;
import com.tradeguide.repository.member.MemberRepository;
import com.tradeguide.repository.portfolio.PortfolioRepository;
import org.springframework.stereotype.Service;

@Service
public class PortfolioService {

    private final MemberRepository memberRepository;
    private final PortfolioRepository portfolioRepository;

    public PortfolioService(
            MemberRepository memberRepository,
            PortfolioRepository portfolioRepository) {
        this.memberRepository = memberRepository;
        this.portfolioRepository = portfolioRepository;
    }

    public Portfolio createPortfolio(Long memberId, String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("포트폴리오 이름은 필수입니다.");
        }

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("회원을 찾을 수 없습니다."));

        Portfolio portfolio = new Portfolio(member, name);

        return portfolioRepository.save(portfolio);
    }
}
