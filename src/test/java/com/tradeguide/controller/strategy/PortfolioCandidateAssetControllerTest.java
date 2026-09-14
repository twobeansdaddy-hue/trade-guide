package com.tradeguide.controller.strategy;

import com.tradeguide.domain.member.Member;
import com.tradeguide.domain.portfolio.Portfolio;
import com.tradeguide.domain.strategy.InvestmentTrack;
import com.tradeguide.domain.strategy.PortfolioCandidateAsset;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.exception.PortfolioCandidateAssetAlreadyExistsException;
import com.tradeguide.exception.PortfolioCandidateAssetNotFoundException;
import com.tradeguide.exception.PortfolioNotFoundException;
import com.tradeguide.service.auth.MemberAccessService;
import com.tradeguide.service.strategy.PortfolioCandidateAssetService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PortfolioCandidateAssetController.class)
@AutoConfigureMockMvc(addFilters = false)
class PortfolioCandidateAssetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PortfolioCandidateAssetService portfolioCandidateAssetService;

    @MockitoBean
    private MemberAccessService memberAccessService;

    private PortfolioCandidateAsset candidateAsset() {
        Member member = new Member("owner@example.com", "owner");
        Portfolio portfolio = new Portfolio(member, "테스트 포트폴리오");

        return new PortfolioCandidateAsset(
                portfolio,
                Market.US,
                "SOXL",
                "디렉시온 반도체 불3배",
                InvestmentTrack.TRACK_A,
                LocalDateTime.of(2026, 9, 11, 9, 0)
        );
    }

    @Test
    void getsCandidateAssets() throws Exception {
        when(portfolioCandidateAssetService.getCandidateAssets(1L, 10L))
                .thenReturn(List.of(candidateAsset()));

        mockMvc.perform(get("/api/members/1/portfolios/10/candidate-assets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].market").value("US"))
                .andExpect(jsonPath("$[0].ticker").value("SOXL"))
                .andExpect(jsonPath("$[0].displayName").value("디렉시온 반도체 불3배"))
                .andExpect(jsonPath("$[0].investmentTrack").value("TRACK_A"));
    }

    @Test
    void createsCandidateAsset() throws Exception {
        when(portfolioCandidateAssetService.create(1L, 10L, Market.US, "SOXL", "디렉시온 반도체 불3배"))
                .thenReturn(candidateAsset());

        mockMvc.perform(post("/api/members/1/portfolios/10/candidate-assets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "market": "US",
                                  "ticker": "SOXL",
                                  "displayName": "디렉시온 반도체 불3배"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.market").value("US"))
                .andExpect(jsonPath("$.ticker").value("SOXL"))
                .andExpect(jsonPath("$.investmentTrack").value("TRACK_A"));

        verify(portfolioCandidateAssetService)
                .create(1L, 10L, Market.US, "SOXL", "디렉시온 반도체 불3배");
    }

    @Test
    void returnsBadRequestWhenDisplayNameIsMissing() throws Exception {
        mockMvc.perform(post("/api/members/1/portfolios/10/candidate-assets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "market": "US",
                                  "ticker": "SOXL"
                                }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(portfolioCandidateAssetService);
    }

    @Test
    void returnsConflictWhenCandidateAssetAlreadyExists() throws Exception {
        doThrow(new PortfolioCandidateAssetAlreadyExistsException("이미 등록된 후보 종목입니다: US / SOXL"))
                .when(portfolioCandidateAssetService)
                .create(eq(1L), eq(10L), eq(Market.US), eq("SOXL"), any());

        mockMvc.perform(post("/api/members/1/portfolios/10/candidate-assets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "market": "US",
                                  "ticker": "SOXL",
                                  "displayName": "디렉시온 반도체 불3배"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PORTFOLIO_CANDIDATE_ASSET_ALREADY_EXISTS"));
    }

    @Test
    void returnsNotFoundWhenPortfolioIsNotOwnedByMember() throws Exception {
        doThrow(new PortfolioNotFoundException("포트폴리오를 찾을 수 없습니다."))
                .when(portfolioCandidateAssetService)
                .create(eq(1L), eq(10L), eq(Market.US), eq("SOXL"), any());

        mockMvc.perform(post("/api/members/1/portfolios/10/candidate-assets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "market": "US",
                                  "ticker": "SOXL",
                                  "displayName": "디렉시온 반도체 불3배"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PORTFOLIO_NOT_FOUND"));
    }

    @Test
    void deletesCandidateAsset() throws Exception {
        mockMvc.perform(delete("/api/members/1/portfolios/10/candidate-assets/US/SOXL"))
                .andExpect(status().isNoContent());

        verify(portfolioCandidateAssetService).delete(1L, 10L, Market.US, "SOXL");
    }

    @Test
    void returnsNotFoundWhenDeletingMissingCandidateAsset() throws Exception {
        doThrow(new PortfolioCandidateAssetNotFoundException("포트폴리오 후보 종목을 찾을 수 없습니다: US / SOXL"))
                .when(portfolioCandidateAssetService)
                .delete(1L, 10L, Market.US, "SOXL");

        mockMvc.perform(delete("/api/members/1/portfolios/10/candidate-assets/US/SOXL"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PORTFOLIO_CANDIDATE_ASSET_NOT_FOUND"));
    }
}
