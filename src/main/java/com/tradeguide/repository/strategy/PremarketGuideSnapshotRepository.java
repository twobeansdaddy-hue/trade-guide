package com.tradeguide.repository.strategy;

import com.tradeguide.domain.strategy.PremarketGuideSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface PremarketGuideSnapshotRepository extends JpaRepository<PremarketGuideSnapshot, Long> {

    Optional<PremarketGuideSnapshot> findByPortfolio_IdAndGuideDate(
            Long portfolioId,
            LocalDate guideDate
    );
}
