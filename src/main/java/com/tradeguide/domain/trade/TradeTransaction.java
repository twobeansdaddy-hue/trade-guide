package com.tradeguide.domain.trade;

import com.tradeguide.domain.portfolio.Portfolio;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "trade_transactions")
public class TradeTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portfolio_id", nullable = false)
    private Portfolio portfolio;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Market market;

    @Column(nullable = false)
    private String ticker;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TradeType tradeType;

    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal quantity;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal executedPrice;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal fee;

    @Column(nullable = false)
    private Instant tradedAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected TradeTransaction() {
    }

    public TradeTransaction(
            Portfolio portfolio,
            Market market,
            String ticker,
            TradeType tradeType,
            BigDecimal quantity,
            BigDecimal executedPrice,
            BigDecimal fee,
            Instant tradedAt
    ) {
        this.portfolio = portfolio;
        this.market = market;
        this.ticker = ticker;
        this.tradeType = tradeType;
        this.quantity = quantity;
        this.executedPrice = executedPrice;
        this.fee = fee;
        this.tradedAt = tradedAt;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Portfolio getPortfolio() {
        return portfolio;
    }

    public Market getMarket() {
        return market;
    }

    public String getTicker() {
        return ticker;
    }

    public TradeType getTradeType() {
        return tradeType;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getExecutedPrice() {
        return executedPrice;
    }

    public BigDecimal getFee() {
        return fee;
    }

    public Instant getTradedAt() {
        return tradedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
