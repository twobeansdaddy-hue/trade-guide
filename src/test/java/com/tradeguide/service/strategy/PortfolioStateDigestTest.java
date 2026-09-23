package com.tradeguide.service.strategy;

import com.tradeguide.domain.holding.Holding;
import com.tradeguide.domain.strategy.InvestmentTrack;
import com.tradeguide.domain.strategy.PremarketGuideCandidateSource;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.domain.trade.TradeTransaction;
import com.tradeguide.domain.trade.TradeTransactionSource;
import com.tradeguide.domain.trade.TradeType;
import com.tradeguide.service.strategy.PortfolioDecisionInputs.AssetKey;
import com.tradeguide.service.strategy.PortfolioDecisionInputs.CandidateRef;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PortfolioStateDigestTest {

    private final PortfolioStateDigest digest = new PortfolioStateDigest();

    @Test
    void holdingsDigestIgnoresDecimalScaleAndOrder() {
        Holding soxl = new Holding(Market.US, "SOXL", new BigDecimal("5"), new BigDecimal("100"));
        Holding tqqq = new Holding(Market.US, "TQQQ", new BigDecimal("2"), new BigDecimal("80.5"));

        assertThat(digest.holdings(List.of(soxl, tqqq))).hasSize(64).isEqualTo(digest.holdings(List.of(
                new Holding(Market.US, "TQQQ", new BigDecimal("2.000"), new BigDecimal("80.50")),
                new Holding(Market.US, "SOXL", new BigDecimal("5.0"), new BigDecimal("100.00")))));
        assertThat(digest.holdings(List.of(soxl))).isNotEqualTo(digest.holdings(List.of(
                new Holding(Market.US, "SOXL", new BigDecimal("6"), new BigDecimal("100")))));
    }

    @Test
    void ledgerDigestIgnoresOrderButTreatsReRegisteredTransactionAsChange() {
        TradeTransaction first = transaction(1L, "SOXL");
        TradeTransaction second = transaction(2L, "TQQQ");

        assertThat(digest.ledger(List.of(first, second))).isEqualTo(digest.ledger(List.of(second, first)));
        // 같은 내용을 삭제 후 다시 등록하면 id가 바뀌므로 다른 원장이다.
        assertThat(digest.ledger(List.of(first))).isNotEqualTo(digest.ledger(List.of(transaction(3L, "SOXL"))));
    }

    @Test
    void settingsAndCandidateDigestsReflectValuesAndSource() {
        AssetKey soxl = new AssetKey(Market.US, "SOXL");

        assertThat(digest.riskSettings(null, Map.of()))
                .isNotEqualTo(digest.riskSettings(new BigDecimal("0.1"), Map.of()));
        assertThat(digest.riskSettings(new BigDecimal("0.10"), Map.of(soxl, new BigDecimal("0.20"))))
                .isEqualTo(digest.riskSettings(new BigDecimal("0.1"), Map.of(soxl, new BigDecimal("0.2"))));
        assertThat(digest.strategyOverrides(Map.of(soxl, InvestmentTrack.TRACK_A)))
                .isNotEqualTo(digest.strategyOverrides(Map.of(soxl, InvestmentTrack.TRACK_B)));

        List<CandidateRef> candidates = List.of(new CandidateRef(Market.US, "TQQQ", null));
        assertThat(digest.candidates(PremarketGuideCandidateSource.GLOBAL_CATALOG, candidates))
                .isNotEqualTo(digest.candidates(PremarketGuideCandidateSource.PORTFOLIO, candidates));
    }

    private TradeTransaction transaction(Long id, String ticker) {
        TradeTransaction transaction = mock(TradeTransaction.class);
        when(transaction.getId()).thenReturn(id);
        when(transaction.getMarket()).thenReturn(Market.US);
        when(transaction.getTicker()).thenReturn(ticker);
        when(transaction.getTradeType()).thenReturn(TradeType.BUY);
        when(transaction.getQuantity()).thenReturn(new BigDecimal("5"));
        when(transaction.getExecutedPrice()).thenReturn(new BigDecimal("100"));
        when(transaction.getFee()).thenReturn(BigDecimal.ZERO);
        when(transaction.getTradedAt()).thenReturn(Instant.parse("2026-09-01T14:00:00Z"));
        when(transaction.getSource()).thenReturn(TradeTransactionSource.values()[0]);
        return transaction;
    }
}
