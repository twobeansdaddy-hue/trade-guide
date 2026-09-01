package com.tradeguide.controller.auth;

import com.tradeguide.config.SecurityConfig;
import com.tradeguide.domain.auth.AuthProvider;
import com.tradeguide.domain.member.Member;
import com.tradeguide.service.auth.AuthIdentityService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthIdentityService authIdentityService;

    @Test
    void returnsUnauthorizedWhenAuthenticationIsDisabledOrMissing() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returnsCurrentGoogleMember() throws Exception {
        Member member = new Member("member@example.com", "member");
        when(authIdentityService.getMember(AuthProvider.GOOGLE, "google-subject")).thenReturn(member);

        mockMvc.perform(get("/api/auth/me")
                        .with(oauth2Login()
                                .clientRegistration(googleRegistration())
                                .attributes(attributes -> attributes.put("sub", "google-subject"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("member@example.com"))
                .andExpect(jsonPath("$.nickname").value("member"))
                .andExpect(jsonPath("$.provider").value("GOOGLE"));
    }

    private ClientRegistration googleRegistration() {
        return ClientRegistration.withRegistrationId("google")
                .clientId("test-client")
                .clientSecret("test-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .tokenUri("https://oauth2.googleapis.com/token")
                .userInfoUri("https://openidconnect.googleapis.com/v1/userinfo")
                .userNameAttributeName("sub")
                .clientName("Google")
                .build();
    }
}
