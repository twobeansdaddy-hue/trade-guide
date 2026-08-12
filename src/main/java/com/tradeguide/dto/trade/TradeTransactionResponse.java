package com.tradeguide.dto.trade;

import com.tradeguide.domain.trade.Market;
import com.tradeguide.domain.trade.TradeTransaction;
import com.tradeguide.domain.trade.TradeType;

import java.math.BigDecimal;
import java.time.Instant;

public class TradeTransactionResponse {

    private final Long id;
    private final Market market;
    private final String ticker;
    private final TradeType tradeType;
    private final BigDecimal quantity;
    private final BigDecimal executedPrice;
    private final BigDecimal fee;
    private final Instant tradedAt;

    private TradeTransactionResponse(
            Long id,
            Market market,
            String ticker,
            TradeType tradeType,
            BigDecimal quantity,
            BigDecimal executedPrice,
            BigDecimal fee,
            Instant tradedAt
    ) {
        this.id = id;
        this.market = market;
        this.ticker = ticker;
        this.tradeType = tradeType;
        this.quantity = quantity;
        this.executedPrice = executedPrice;
        this.fee = fee;
        this.tradedAt = tradedAt;
    }

    public static TradeTransactionResponse from(
            TradeTransaction transaction
    ) {
        return new TradeTransactionResponse(
                transaction.getId(),
                transaction.getMarket(),
                transaction.getTicker(),
                transaction.getTradeType(),
                transaction.getQuantity(),
                transaction.getExecutedPrice(),
                transaction.getFee(),
                transaction.getTradedAt()
        );
    }

    public Long getId() {
        return id;
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
}