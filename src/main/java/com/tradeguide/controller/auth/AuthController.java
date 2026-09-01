package com.tradeguide.controller.auth;

import com.tradeguide.domain.auth.AuthProvider;
import com.tradeguide.domain.member.Member;
import com.tradeguide.dto.auth.AuthMeResponse;
import com.tradeguide.service.auth.AuthIdentityService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthIdentityService authIdentityService;

    public AuthController(AuthIdentityService authIdentityService) {
        this.authIdentityService = authIdentityService;
    }

    @GetMapping("/me")
    public ResponseEntity<AuthMeResponse> getCurrentMember(Authentication authentication) {
        if (!(authentication instanceof OAuth2AuthenticationToken oauthToken)
                || !(oauthToken.getPrincipal() instanceof OAuth2User user)) {
            return ResponseEntity.status(401).build();
        }
        AuthProvider provider = toProvider(oauthToken.getAuthorizedClientRegistrationId());
        String subject = user.getAttribute("sub");
        if (subject == null || subject.isBlank()) {
            return ResponseEntity.status(401).build();
        }
        Member member = authIdentityService.getMember(provider, subject);
        return ResponseEntity.ok(AuthMeResponse.from(member, provider));
    }

    private AuthProvider toProvider(String registrationId) {
        if ("google".equals(registrationId)) {
            return AuthProvider.GOOGLE;
        }
        throw new IllegalStateException("지원하지 않는 로그인 제공자입니다.");
    }
}
