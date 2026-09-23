package com.tradeguide.service.market;

import com.tradeguide.domain.market.CandleInterval;
import com.tradeguide.domain.market.MarketCandle;
import com.tradeguide.domain.market.MarketDataProvider;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

@Component
public class MarketCandleDigest {

    /**
     * 직렬화 형식 버전. v2부터 가격을 표기(scale)가 아니라 값으로 직렬화해
     * {@code 100}과 {@code 100.0}이 같은 해시가 된다. v1 해시와는 호환되지 않는다.
     */
    private static final int FORMAT_VERSION = 2;

    public String sha256(MarketDataProvider provider, CandleInterval interval, List<MarketCandle> candles) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeByte(FORMAT_VERSION);
            output.writeUTF(provider.name());
            output.writeUTF(interval.name());
            output.writeInt(candles.size());
            for (MarketCandle candle : candles) {
                output.writeUTF(candle.getMarket().name());
                output.writeUTF(candle.getTicker());
                output.writeLong(candle.getTradingDate().toEpochDay());
                output.writeUTF(canonical(candle.getOpen()));
                output.writeUTF(canonical(candle.getHigh()));
                output.writeUTF(canonical(candle.getLow()));
                output.writeUTF(canonical(candle.getClose()));
                output.writeLong(candle.getVolume());
            }
            output.flush();
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("시세 입력 해시를 계산할 수 없습니다.", exception);
        }
    }

    private String canonical(BigDecimal price) {
        return price.stripTrailingZeros().toPlainString();
    }
}
