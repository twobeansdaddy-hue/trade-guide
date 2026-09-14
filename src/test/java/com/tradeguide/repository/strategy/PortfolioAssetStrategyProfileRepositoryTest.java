package com.tradeguide.repository.strategy;

import com.tradeguide.domain.member.Member;
import com.tradeguide.domain.portfolio.Portfolio;
import com.tradeguide.domain.strategy.InvestmentTrack;
import com.tradeguide.domain.strategy.PortfolioAssetStrategyProfile;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.repository.member.MemberRepository;
import com.tradeguide.repository.portfolio.PortfolioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class PortfolioAssetStrategyProfileRepositoryTest {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PortfolioRepository portfolioRepository;

    @Autowired
    private PortfolioAssetStrategyProfileRepository portfolioAssetStrategyProfileRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Portfolio portfolioA;
    private Portfolio portfolioB;

    @BeforeEach
    void setUp() {
        Member memberA = memberRepository.save(new Member("member-a@example.com", "member-a"));
        Member memberB = memberRepository.save(new Member("member-b@example.com", "member-b"));

        portfolioA = portfolioRepository.save(new Portfolio(memberA, "A의 포트폴리오"));
        portfolioB = portfolioRepository.save(new Portfolio(memberB, "B의 포트폴리오"));
    }

    @Test
    void findsOverrideByPortfolioMarketAndTicker() {
        PortfolioAssetStrategyProfile saved = portfolioAssetStrategyProfileRepository.saveAndFlush(
                new PortfolioAssetStrategyProfile(
                        portfolioA,
                        Market.US,
                        "SOXL",
                        InvestmentTrack.TRACK_B,
                        LocalDateTime.of(2026, 9, 1, 9, 0)
                )
        );
        entityManager.clear();

        Optional<PortfolioAssetStrategyProfile> found = portfolioAssetStrategyProfileRepository
                .findByPortfolio_IdAndMarketAndTicker(portfolioA.getId(), Market.US, "SOXL");

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());
        assertThat(found.get().getInvestmentTrack()).isEqualTo(InvestmentTrack.TRACK_B);
    }

    @Test
    void doesNotLeakOverridesAcrossPortfolios() {
        portfolioAssetStrategyProfileRepository.saveAndFlush(
                new PortfolioAssetStrategyProfile(
                        portfolioA,
                        Market.US,
                        "SOXL",
                        InvestmentTrack.TRACK_B,
                        LocalDateTime.of(2026, 9, 1, 9, 0)
                )
        );
        entityManager.clear();

        Optional<PortfolioAssetStrategyProfile> foundForOtherPortfolio = portfolioAssetStrategyProfileRepository
                .findByPortfolio_IdAndMarketAndTicker(portfolioB.getId(), Market.US, "SOXL");

        assertThat(foundForOtherPortfolio).isEmpty();
    }

    @Test
    void findsAllOverridesForPortfolio() {
        portfolioAssetStrategyProfileRepository.saveAll(List.of(
                new PortfolioAssetStrategyProfile(
                        portfolioA,
                        Market.US,
                        "SOXL",
                        InvestmentTrack.TRACK_B,
                        LocalDateTime.of(2026, 9, 1, 9, 0)
                ),
                new PortfolioAssetStrategyProfile(
                        portfolioA,
                        Market.US,
                        "TQQQ",
                        InvestmentTrack.TRACK_A,
                        LocalDateTime.of(2026, 9, 1, 9, 0)
                ),
                new PortfolioAssetStrategyProfile(
                        portfolioB,
                        Market.US,
                        "SOXL",
                        InvestmentTrack.TRACK_A,
                        LocalDateTime.of(2026, 9, 1, 9, 0)
                )
        ));
        entityManager.clear();

        List<PortfolioAssetStrategyProfile> results = portfolioAssetStrategyProfileRepository
                .findAllByPortfolio_Id(portfolioA.getId());

        assertThat(results)
                .extracting(PortfolioAssetStrategyProfile::getTicker)
                .containsExactlyInAnyOrder("SOXL", "TQQQ");
    }

    @Test
    void deletesOverrideByPortfolioMarketAndTicker() {
        portfolioAssetStrategyProfileRepository.saveAndFlush(
                new PortfolioAssetStrategyProfile(
                        portfolioA,
                        Market.US,
                        "SOXL",
                        InvestmentTrack.TRACK_B,
                        LocalDateTime.of(2026, 9, 1, 9, 0)
                )
        );

        portfolioAssetStrategyProfileRepository.deleteByPortfolio_IdAndMarketAndTicker(
                portfolioA.getId(),
                Market.US,
                "SOXL"
        );
        entityManager.flush();
        entityManager.clear();

        Optional<PortfolioAssetStrategyProfile> found = portfolioAssetStrategyProfileRepository
                .findByPortfolio_IdAndMarketAndTicker(portfolioA.getId(), Market.US, "SOXL");

        assertThat(found).isEmpty();
    }

    @Test
    void rejectsDuplicateOverrideForSamePortfolioMarketAndTicker() {
        portfolioAssetStrategyProfileRepository.saveAndFlush(
                new PortfolioAssetStrategyProfile(
                        portfolioA,
                        Market.US,
                        "SOXL",
                        InvestmentTrack.TRACK_B,
                        LocalDateTime.of(2026, 9, 1, 9, 0)
                )
        );

        org.junit.jupiter.api.Assertions.assertThrows(
                Exception.class,
                () -> portfolioAssetStrategyProfileRepository.saveAndFlush(
                        new PortfolioAssetStrategyProfile(
                                portfolioA,
                                Market.US,
                                "SOXL",
                                InvestmentTrack.TRACK_A,
                                LocalDateTime.of(2026, 9, 2, 9, 0)
                        )
                )
        );
    }
}
