package com.tradeguide.dto.trade;

import com.tradeguide.domain.trade.Market;
import com.tradeguide.domain.trade.TradeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.Instant;

public class TradeTransactionCreateRequest {

    @NotNull(message = "시장은 필수입니다.")
    private final Market market;

    @NotBlank(message = "종목 코드는 필수입니다.")
    private final String ticker;

    @NotNull(message = "거래 유형은 필수입니다.")
    private final TradeType tradeType;

    @Positive(message = "거래 수량은 0보다 커야 합니다.")
    private final BigDecimal quantity;

    @Positive(message = "체결 단가는 0보다 커야 합니다.")
    private final BigDecimal executedPrice;

    @PositiveOrZero(message = "수수료는 0 이상이어야 합니다.")
    private final BigDecimal fee;

    @NotNull(message = "체결 시각은 필수입니다.")
    private final Instant tradedAt;

    public TradeTransactionCreateRequest(
            Market market,
            String ticker,
            TradeType tradeType,
            BigDecimal quantity,
            BigDecimal executedPrice,
            BigDecimal fee,
            Instant tradedAt
    ) {
        this.market = market;
        this.ticker = ticker;
        this.tradeType = tradeType;
        this.quantity = quantity;
        this.executedPrice = executedPrice;
        this.fee = fee;
        this.tradedAt = tradedAt;
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