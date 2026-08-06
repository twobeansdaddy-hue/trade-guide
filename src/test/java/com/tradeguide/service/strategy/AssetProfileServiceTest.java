package com.tradeguide.service.strategy;

import com.tradeguide.domain.strategy.AssetProfile;
import com.tradeguide.domain.strategy.InvestmentTrack;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.exception.AssetProfileAlreadyExistsException;
import com.tradeguide.repository.strategy.AssetProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssetProfileServiceTest {

    @Mock
    private AssetProfileRepository assetProfileRepository;

    @InjectMocks
    private AssetProfileService assetProfileService;

    @Test
    void createsAssetProfileWithNormalizedTicker() {
        when(assetProfileRepository.existsByMarketAndTicker(
                Market.US,
                "SOXL"
        )).thenReturn(false);

        when(assetProfileRepository.save(any(AssetProfile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AssetProfile result = assetProfileService.createAssetProfile(
                Market.US,
                " soxl ",
                InvestmentTrack.TRACK_A
        );

        assertThat(result.getMarket()).isEqualTo(Market.US);
        assertThat(result.getTicker()).isEqualTo("SOXL");
        assertThat(result.getInvestmentTrack())
                .isEqualTo(InvestmentTrack.TRACK_A);

        verify(assetProfileRepository)
                .existsByMarketAndTicker(Market.US, "SOXL");
        verify(assetProfileRepository).save(result);
    }

    @Test
    void throwsExceptionWhenAssetProfileAlreadyExists() {
        when(assetProfileRepository.existsByMarketAndTicker(
                Market.US,
                "SOXL"
        )).thenReturn(true);

        assertThatThrownBy(() ->
                assetProfileService.createAssetProfile(
                        Market.US,
                        "soxl",
                        InvestmentTrack.TRACK_A
                ))
                .isInstanceOf(AssetProfileAlreadyExistsException.class)
                .hasMessage(
                        "이미 등록된 전략 프로필입니다: US / SOXL"
                );

        verify(assetProfileRepository, never())
                .save(any(AssetProfile.class));
    }
}