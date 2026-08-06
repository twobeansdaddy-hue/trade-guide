package com.tradeguide.controller.strategy;

import com.tradeguide.domain.strategy.AssetProfile;
import com.tradeguide.domain.strategy.InvestmentTrack;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.exception.AssetProfileAlreadyExistsException;
import com.tradeguide.service.strategy.AssetProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AssetProfileAdminController.class)
class AssetProfileAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AssetProfileService assetProfileService;

    @Test
    void createsAssetProfile() throws Exception {
        AssetProfile assetProfile = mock(AssetProfile.class);
        when(assetProfile.getId()).thenReturn(1L);
        when(assetProfile.getMarket()).thenReturn(Market.US);
        when(assetProfile.getTicker()).thenReturn("SOXL");
        when(assetProfile.getInvestmentTrack())
                .thenReturn(InvestmentTrack.TRACK_A);

        when(assetProfileService.createAssetProfile(
                Market.US,
                "SOXL",
                InvestmentTrack.TRACK_A
        )).thenReturn(assetProfile);

        mockMvc.perform(
                        post("/api/admin/asset-profiles")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "market": "US",
                                          "ticker": "SOXL",
                                          "investmentTrack": "TRACK_A"
                                        }
                                        """)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.market").value("US"))
                .andExpect(jsonPath("$.ticker").value("SOXL"))
                .andExpect(jsonPath("$.investmentTrack")
                        .value("TRACK_A"));

        verify(assetProfileService).createAssetProfile(
                Market.US,
                "SOXL",
                InvestmentTrack.TRACK_A
        );
    }

    @Test
    void returnsBadRequestWhenTickerIsMissing() throws Exception {
        mockMvc.perform(
                        post("/api/admin/asset-profiles")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "market": "US",
                                          "investmentTrack": "TRACK_A"
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("티커는 필수입니다."));

        verifyNoInteractions(assetProfileService);
    }

    @Test
    void returnsConflictWhenAssetProfileAlreadyExists() throws Exception {
        when(assetProfileService.createAssetProfile(
                Market.US,
                "SOXL",
                InvestmentTrack.TRACK_A
        )).thenThrow(new AssetProfileAlreadyExistsException(
                Market.US,
                "SOXL"
        ));

        mockMvc.perform(
                        post("/api/admin/asset-profiles")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "market": "US",
                                          "ticker": "SOXL",
                                          "investmentTrack": "TRACK_A"
                                        }
                                        """)
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "이미 등록된 전략 프로필입니다: US / SOXL"
                ));
    }
}