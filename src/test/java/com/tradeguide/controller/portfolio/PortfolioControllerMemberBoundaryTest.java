package com.tradeguide.controller.portfolio;

import com.tradeguide.domain.auth.AuthProvider;
import com.tradeguide.domain.member.Member;
import com.tradeguide.domain.portfolio.Portfolio;
import com.tradeguide.service.auth.AuthIdentityService;
import com.tradeguide.service.auth.MemberAccessService;
import com.tradeguide.service.holding.HoldingService;
import com.tradeguide.service.portfolio.PortfolioService;
import com.tradeguide.service.strategy.PortfolioCandidateStrategyGuideService;
import com.tradeguide.service.strategy.PortfolioStrategyGuideService;
import com.tradeguide.service.risk.PortfolioExposureService;
import com.tradeguide.service.risk.PortfolioRiskAlertService;
import com.tradeguide.service.valuation.PortfolioValuationService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * tradeguide.auth.enabled=true일 때 실제 MemberAccessService를 통해
 * URL의 memberId와 로그인 사용자의 memberId 소유권 경계를 검증한다.
 * 실제 Google 비밀값이나 네트워크 호출 없이 수동으로 만든 OAuth2AuthenticationToken만 사용한다.
 */
@WebMvcTest(PortfolioController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(MemberAccessService.class)
@TestPropertySource(properties = "tradeguide.auth.enabled=true")
class PortfolioControllerMemberBoundaryTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PortfolioService portfolioService;
    @MockitoBean
    private HoldingService holdingService;
    @MockitoBean
    private PortfolioValuationService portfolioValuationService;
    @MockitoBean
    private PortfolioStrategyGuideService portfolioStrategyGuideService;
    @MockitoBean
    private PortfolioExposureService portfolioExposureService;
    @MockitoBean
    private PortfolioCandidateStrategyGuideService portfolioCandidateStrategyGuideService;
    @MockitoBean
    private PortfolioRiskAlertService portfolioRiskAlertService;
    @MockitoBean
    private AuthIdentityService authIdentityService;

    @Test
    void allowsRequestWhenAuthenticatedMemberMatchesPathMemberId() throws Exception {
        Member member = mock(Member.class);
        when(member.getId()).thenReturn(10L);
        when(authIdentityService.getMember(AuthProvider.GOOGLE, "google-subject")).thenReturn(member);

        Portfolio portfolio = mock(Portfolio.class);
        when(portfolio.getId()).thenReturn(1L);
        when(portfolio.getName()).thenReturn("US Stocks");
        when(portfolioService.getPortfolios(10L)).thenReturn(List.of(portfolio));

        mockMvc.perform(get("/api/members/10/portfolios")
                        .principal(googleAuthentication("google-subject")))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsRequestWhenAuthenticatedMemberDiffersFromPathMemberId() throws Exception {
        Member member = mock(Member.class);
        when(member.getId()).thenReturn(10L);
        when(authIdentityService.getMember(AuthProvider.GOOGLE, "google-subject")).thenReturn(member);

        mockMvc.perform(get("/api/members/999/portfolios")
                        .principal(googleAuthentication("google-subject")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("다른 회원의 데이터에 접근할 수 없습니다."));
    }

    @Test
    void rejectsUnauthenticatedRequestToMemberScopedEndpoint() throws Exception {
        mockMvc.perform(get("/api/members/10/portfolios"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("로그인이 필요합니다."));
    }

    private OAuth2AuthenticationToken googleAuthentication(String subject) {
        DefaultOAuth2User user = new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                Map.of("sub", subject),
                "sub"
        );
        return new OAuth2AuthenticationToken(user, user.getAuthorities(), "google");
    }
}
