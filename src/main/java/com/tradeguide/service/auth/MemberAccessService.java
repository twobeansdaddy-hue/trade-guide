package com.tradeguide.service.auth;

import com.tradeguide.domain.auth.AuthProvider;
import com.tradeguide.domain.member.Member;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

@Service
public class MemberAccessService {
    private final boolean authenticationEnabled;
    private final AuthIdentityService authIdentityService;

    public MemberAccessService(
            @Value("${tradeguide.auth.enabled:false}") boolean authenticationEnabled,
            AuthIdentityService authIdentityService
    ) {
        this.authenticationEnabled = authenticationEnabled;
        this.authIdentityService = authIdentityService;
    }

    public void requireMemberAccess(Authentication authentication, Long memberId) {
        if (!authenticationEnabled) {
            return;
        }

        if (!(authentication instanceof OAuth2AuthenticationToken oauthToken)
                || !(oauthToken.getPrincipal() instanceof OAuth2User user)) {
            throw new AccessDeniedException("로그인이 필요합니다.");
        }

        String subject = user.getAttribute("sub");
        Member member = authIdentityService.getMember(toProvider(oauthToken.getAuthorizedClientRegistrationId()), subject);
        if (!memberId.equals(member.getId())) {
            throw new AccessDeniedException("다른 회원의 데이터에 접근할 수 없습니다.");
        }
    }

    private AuthProvider toProvider(String registrationId) {
        if ("google".equals(registrationId)) {
            return AuthProvider.GOOGLE;
        }
        throw new AccessDeniedException("지원하지 않는 로그인 제공자입니다.");
    }
}
