package com.tradeguide.controller.strategy;

import com.tradeguide.domain.strategy.InvestmentTrack;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.exception.PortfolioAssetNotHeldException;
import com.tradeguide.exception.PortfolioAssetStrategyProfileNotFoundException;
import com.tradeguide.exception.PortfolioNotFoundException;
import com.tradeguide.exception.UnsupportedInvestmentTrackException;
import com.tradeguide.service.auth.MemberAccessService;
import com.tradeguide.service.strategy.PortfolioAssetStrategyProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PortfolioAssetStrategyProfileController.class)
@AutoConfigureMockMvc(addFilters = false)
class PortfolioAssetStrategyProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PortfolioAssetStrategyProfileService portfolioAssetStrategyProfileService;

    @MockitoBean
    private MemberAccessService memberAccessService;

    @Test
    void getsHeldAssetStrategyProfiles() throws Exception {
        when(portfolioAssetStrategyProfileService.getHeldAssetStrategyProfiles(1L, 10L))
                .thenReturn(List.of(
                        new PortfolioAssetStrategyProfileService.PortfolioAssetStrategyProfileResult(
                                Market.US,
                                "SOXL",
                                InvestmentTrack.TRACK_B,
                                InvestmentTrack.TRACK_A,
                                LocalDateTime.of(2026, 9, 11, 9, 0)
                        )
                ));

        mockMvc.perform(get("/api/members/1/portfolios/10/strategy-profiles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].market").value("US"))
                .andExpect(jsonPath("$[0].ticker").value("SOXL"))
                .andExpect(jsonPath("$[0].overrideTrack").value("TRACK_B"))
                .andExpect(jsonPath("$[0].globalTrack").value("TRACK_A"))
                .andExpect(jsonPath("$[0].effectiveTrack").value("TRACK_B"));
    }

    @Test
    void upsertsOverrideForHeldAsset() throws Exception {
        when(portfolioAssetStrategyProfileService.upsert(1L, 10L, Market.US, "SOXL", InvestmentTrack.TRACK_A))
                .thenReturn(new PortfolioAssetStrategyProfileService.PortfolioAssetStrategyProfileResult(
                        Market.US,
                        "SOXL",
                        InvestmentTrack.TRACK_A,
                        InvestmentTrack.TRACK_A,
                        LocalDateTime.of(2026, 9, 11, 9, 0)
                ));

        mockMvc.perform(put("/api/members/1/portfolios/10/strategy-profiles/US/SOXL")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "investmentTrack": "TRACK_A"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overrideTrack").value("TRACK_A"))
                .andExpect(jsonPath("$.effectiveTrack").value("TRACK_A"));

        verify(portfolioAssetStrategyProfileService)
                .upsert(1L, 10L, Market.US, "SOXL", InvestmentTrack.TRACK_A);
    }

    @Test
    void returnsBadRequestWhenInvestmentTrackIsMissing() throws Exception {
        mockMvc.perform(put("/api/members/1/portfolios/10/strategy-profiles/US/SOXL")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(portfolioAssetStrategyProfileService);
    }

    @Test
    void returnsUnprocessableEntityWhenAssetIsNotHeld() throws Exception {
        doThrow(new PortfolioAssetNotHeldException("현재 보유하지 않은 종목에는 전략 프로필을 설정할 수 없습니다: US / SOXL"))
                .when(portfolioAssetStrategyProfileService)
                .upsert(1L, 10L, Market.US, "SOXL", InvestmentTrack.TRACK_B);

        mockMvc.perform(put("/api/members/1/portfolios/10/strategy-profiles/US/SOXL")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "investmentTrack": "TRACK_B"
                                }
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PORTFOLIO_ASSET_NOT_HELD"));
    }

    @Test
    void returnsUnprocessableEntityWhenTrackHasNoSupportedStrategy() throws Exception {
        doThrow(new UnsupportedInvestmentTrackException("지원하지 않는 투자 트랙입니다: TRACK_B"))
                .when(portfolioAssetStrategyProfileService)
                .upsert(1L, 10L, Market.US, "SOXL", InvestmentTrack.TRACK_B);

        mockMvc.perform(put("/api/members/1/portfolios/10/strategy-profiles/US/SOXL")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "investmentTrack": "TRACK_B"
                                }
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_INVESTMENT_TRACK"));
    }

    @Test
    void returnsNotFoundWhenPortfolioIsNotOwnedByMember() throws Exception {
        doThrow(new PortfolioNotFoundException("포트폴리오를 찾을 수 없습니다."))
                .when(portfolioAssetStrategyProfileService)
                .upsert(1L, 10L, Market.US, "SOXL", InvestmentTrack.TRACK_B);

        mockMvc.perform(put("/api/members/1/portfolios/10/strategy-profiles/US/SOXL")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "investmentTrack": "TRACK_B"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PORTFOLIO_NOT_FOUND"));
    }

    @Test
    void deletesOverride() throws Exception {
        mockMvc.perform(delete("/api/members/1/portfolios/10/strategy-profiles/US/SOXL"))
                .andExpect(status().isNoContent());

        verify(portfolioAssetStrategyProfileService).delete(1L, 10L, Market.US, "SOXL");
    }

    @Test
    void returnsNotFoundWhenDeletingMissingOverride() throws Exception {
        doThrow(new PortfolioAssetStrategyProfileNotFoundException("포트폴리오 전략 프로필 재정의를 찾을 수 없습니다: US / SOXL"))
                .when(portfolioAssetStrategyProfileService)
                .delete(1L, 10L, Market.US, "SOXL");

        mockMvc.perform(delete("/api/members/1/portfolios/10/strategy-profiles/US/SOXL"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PORTFOLIO_ASSET_STRATEGY_PROFILE_NOT_FOUND"));
    }
}
