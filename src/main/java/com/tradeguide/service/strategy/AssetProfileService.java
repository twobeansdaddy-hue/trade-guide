package com.tradeguide.service.strategy;

import com.tradeguide.domain.strategy.AssetProfile;
import com.tradeguide.domain.strategy.InvestmentTrack;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.exception.AssetProfileAlreadyExistsException;
import com.tradeguide.repository.strategy.AssetProfileRepository;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class AssetProfileService {

    private final AssetProfileRepository assetProfileRepository;

    public AssetProfileService(AssetProfileRepository assetProfileRepository) {
        this.assetProfileRepository = assetProfileRepository;
    }

    public AssetProfile createAssetProfile(
            Market market,
            String ticker,
            InvestmentTrack investmentTrack
    ) {
        if (market == null) {
            throw new IllegalArgumentException("시장은 필수입니다.");
        }

        if (ticker == null || ticker.isBlank()) {
            throw new IllegalArgumentException("티커는 필수입니다.");
        }

        if (investmentTrack == null) {
            throw new IllegalArgumentException("투자 트랙은 필수입니다.");
        }

        String normalizedTicker = ticker.trim().toUpperCase(Locale.ROOT);

        if (assetProfileRepository.existsByMarketAndTicker(
                market,
                normalizedTicker
        )) {
            throw new AssetProfileAlreadyExistsException(
                    market,
                    normalizedTicker
            );
        }

        AssetProfile assetProfile = new AssetProfile(
                market,
                normalizedTicker,
                investmentTrack
        );

        return assetProfileRepository.save(assetProfile);
    }
}
