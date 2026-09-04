package com.tradeguide.controller.broker;

import com.tradeguide.domain.auth.AuthProvider;
import com.tradeguide.domain.member.Member;
import com.tradeguide.service.auth.AuthIdentityService;
import com.tradeguide.service.auth.MemberAccessService;
import com.tradeguide.service.broker.BrokerConnectionService;
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

@WebMvcTest(BrokerConnectionController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(MemberAccessService.class)
@TestPropertySource(properties = "tradeguide.auth.enabled=true")
class BrokerConnectionControllerMemberBoundaryTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BrokerConnectionService brokerConnectionService;

    @MockitoBean
    private AuthIdentityService authIdentityService;

    @Test
    void rejectsOtherMembersBrokerConnectionRequest() throws Exception {
        Member member = mock(Member.class);
        when(member.getId()).thenReturn(10L);
        when(authIdentityService.getMember(AuthProvider.GOOGLE, "google-subject"))
                .thenReturn(member);

        mockMvc.perform(get("/api/members/999/broker-connections")
                        .principal(googleAuthentication("google-subject")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("다른 회원의 데이터에 접근할 수 없습니다."));
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
