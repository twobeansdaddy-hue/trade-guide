package com.tradeguide.repository.portfolio;

import com.tradeguide.domain.portfolio.Portfolio;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PortfolioRepository extends JpaRepository<Portfolio, Long> {
    List<Portfolio> findAllByMember_Id(Long memberId);
}
