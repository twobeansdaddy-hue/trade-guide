package com.tradeguide.repository.strategy;

import com.tradeguide.domain.strategy.AssetProfile;
import com.tradeguide.domain.strategy.InvestmentTrack;
import com.tradeguide.domain.trade.Market;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class AssetProfileRepositoryTest {

    @Autowired
    private AssetProfileRepository assetProfileRepository;

    @Test
    void findsAssetProfileByMarketAndTicker() {
        AssetProfile savedProfile = assetProfileRepository.save(
                new AssetProfile(
                        Market.US,
                        "SOXL",
                        InvestmentTrack.TRACK_A
                )
        );

        Optional<AssetProfile> result =
                assetProfileRepository.findByMarketAndTicker(
                        Market.US,
                        "SOXL"
                );

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(savedProfile.getId());
        assertThat(result.get().getInvestmentTrack())
                .isEqualTo(InvestmentTrack.TRACK_A);
    }

    @Test
    void returnsEmptyWhenAssetProfileDoesNotExist() {
        Optional<AssetProfile> result =
                assetProfileRepository.findByMarketAndTicker(
                        Market.US,
                        "AAPL"
                );

        assertThat(result).isEmpty();
    }

    @Test
    void findsAllAssetProfilesByInvestmentTrack() {
        assetProfileRepository.saveAll(List.of(
                new AssetProfile(Market.US, "SOXL", InvestmentTrack.TRACK_A),
                new AssetProfile(Market.US, "TQQQ", InvestmentTrack.TRACK_A),
                new AssetProfile(Market.US, "AAPL", InvestmentTrack.TRACK_B)
        ));

        List<AssetProfile> profiles =
                assetProfileRepository.findAllByInvestmentTrack(
                        InvestmentTrack.TRACK_A
                );

        assertThat(profiles)
                .extracting(AssetProfile::getTicker)
                .containsExactlyInAnyOrder("SOXL", "TQQQ");
    }
}