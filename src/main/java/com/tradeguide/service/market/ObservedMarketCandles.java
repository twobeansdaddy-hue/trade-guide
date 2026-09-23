package com.tradeguide.service.market;

import com.tradeguide.domain.market.MarketCandle;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Objects;

public record ObservedMarketCandles(List<MarketCandle> candles, Optional<SourceReceipt> sourceReceipt) {
    public ObservedMarketCandles {
        candles = List.copyOf(candles);
        Objects.requireNonNull(sourceReceipt, "시세 수신 근거 상태가 필요합니다.");
    }

    public record SourceReceipt(List<Instant> pageReceivedAt, Boolean adjustedRequested) {
        public SourceReceipt {
            pageReceivedAt = List.copyOf(pageReceivedAt);
            if (pageReceivedAt.isEmpty()) {
                throw new IllegalArgumentException("시세 응답 수신 시각이 필요합니다.");
            }
        }
    }
}
