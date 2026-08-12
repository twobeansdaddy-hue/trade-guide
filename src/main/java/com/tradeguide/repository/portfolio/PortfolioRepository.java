package com.tradeguide.repository.portfolio;

import com.tradeguide.domain.portfolio.Portfolio;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PortfolioRepository extends JpaRepository<Portfolio, Long> {
    List<Portfolio> findAllByMember_Id(Long memberId);

    Optional<Portfolio> findByMember_IdAndId(
            Long memberId,
            Long portfolioId
    );
}
