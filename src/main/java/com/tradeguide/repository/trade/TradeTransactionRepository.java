package com.tradeguide.repository.trade;

import com.tradeguide.domain.trade.TradeTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TradeTransactionRepository extends JpaRepository<TradeTransaction, Long> {
    List<TradeTransaction> findAllByPortfolio_IdOrderByTradedAtAsc(Long portfolioId);

    List<TradeTransaction> findAllByPortfolio_IdOrderByTradedAtDesc(Long portfolioId);

}
