package com.tradeguide.service.auth;

import com.tradeguide.domain.auth.AuthProvider;
import com.tradeguide.domain.member.Member;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MemberAccessServiceTest {

    @Test
    void allowsExistingLocalRequestsWhenAuthenticationIsDisabled() {
        MemberAccessService service = new MemberAccessService(false, mock(AuthIdentityService.class));

        assertThatCode(() -> service.requireMemberAccess(null, 1L))
                .doesNotThrowAnyException();
    }

    @Test
    void allowsOnlyTheMemberConnectedToTheGoogleIdentity() {
        AuthIdentityService authIdentityService = mock(AuthIdentityService.class);
        Member member = mock(Member.class);
        when(member.getId()).thenReturn(1L);
        when(authIdentityService.getMember(AuthProvider.GOOGLE, "google-subject")).thenReturn(member);
        MemberAccessService service = new MemberAccessService(true, authIdentityService);

        assertThatCode(() -> service.requireMemberAccess(googleAuthentication(), 1L))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsAccessToAnotherMembersPath() {
        AuthIdentityService authIdentityService = mock(AuthIdentityService.class);
        Member member = mock(Member.class);
        when(member.getId()).thenReturn(1L);
        when(authIdentityService.getMember(AuthProvider.GOOGLE, "google-subject")).thenReturn(member);
        MemberAccessService service = new MemberAccessService(true, authIdentityService);

        assertThatThrownBy(() -> service.requireMemberAccess(googleAuthentication(), 2L))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("다른 회원의 데이터에 접근할 수 없습니다.");
    }

    private OAuth2AuthenticationToken googleAuthentication() {
        DefaultOAuth2User user = new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                Map.of("sub", "google-subject"),
                "sub"
        );
        return new OAuth2AuthenticationToken(user, user.getAuthorities(), "google");
    }
}
