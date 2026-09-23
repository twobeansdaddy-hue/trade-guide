package com.tradeguide.service.market;

import com.tradeguide.domain.market.CandleInterval;
import com.tradeguide.domain.market.MarketCandle;
import com.tradeguide.domain.market.MarketDataProvider;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

@Component
public class MarketCandleDigest {

    public String sha256(MarketDataProvider provider, CandleInterval interval, List<MarketCandle> candles) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeByte(1);
            output.writeUTF(provider.name());
            output.writeUTF(interval.name());
            output.writeInt(candles.size());
            for (MarketCandle candle : candles) {
                output.writeUTF(candle.getMarket().name());
                output.writeUTF(candle.getTicker());
                output.writeLong(candle.getTradingDate().toEpochDay());
                output.writeUTF(candle.getOpen().toPlainString());
                output.writeUTF(candle.getHigh().toPlainString());
                output.writeUTF(candle.getLow().toPlainString());
                output.writeUTF(candle.getClose().toPlainString());
                output.writeLong(candle.getVolume());
            }
            output.flush();
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("시세 입력 해시를 계산할 수 없습니다.", exception);
        }
    }
}
