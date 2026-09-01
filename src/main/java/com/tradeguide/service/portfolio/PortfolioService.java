package com.tradeguide.service.portfolio;

import com.tradeguide.domain.member.Member;
import com.tradeguide.domain.portfolio.Portfolio;
import com.tradeguide.domain.risk.PortfolioRiskPolicy;
import com.tradeguide.repository.member.MemberRepository;
import com.tradeguide.repository.portfolio.PortfolioRepository;
import com.tradeguide.exception.PortfolioRiskPolicyNotFoundException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

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

    public PortfolioRiskPolicy updateRiskPolicy(
            Long memberId,
            Long portfolioId,
            BigDecimal maxLossPerTradeRatio,
            BigDecimal maxSingleAssetExposureRatio
    ) {
        Portfolio portfolio = findPortfolio(memberId, portfolioId);

        PortfolioRiskPolicy riskPolicy = new PortfolioRiskPolicy(
                maxLossPerTradeRatio, maxSingleAssetExposureRatio
        );

        portfolio.changeRiskPolicy(riskPolicy);
        portfolioRepository.save(portfolio);

        return riskPolicy;
    }

    public PortfolioRiskPolicy getRiskPolicy(Long memberId, Long portfolioId) {
        Portfolio portfolio = findPortfolio(memberId, portfolioId);
        PortfolioRiskPolicy riskPolicy = portfolio.getRiskPolicy();

        if (riskPolicy == null) {
            throw new PortfolioRiskPolicyNotFoundException("포트폴리오 위험 한도 정책이 설정되지 않았습니다.");
        }

        return riskPolicy;
    }

    private Portfolio findPortfolio(Long memberId, Long portfolioId) {
        return portfolioRepository.findByMember_IdAndId(memberId, portfolioId)
                .orElseThrow(() -> new IllegalArgumentException("포트폴리오를 찾을 수 없습니다.")
                );
    }
}
