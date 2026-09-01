package com.tradeguide.service.auth;

import com.tradeguide.domain.auth.AuthIdentity;
import com.tradeguide.domain.auth.AuthProvider;
import com.tradeguide.domain.member.Member;
import com.tradeguide.repository.auth.AuthIdentityRepository;
import com.tradeguide.repository.member.MemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthIdentityServiceTest {

    @Mock
    private AuthIdentityRepository authIdentityRepository;
    @Mock
    private MemberRepository memberRepository;
    @InjectMocks
    private AuthIdentityService authIdentityService;

    @Test
    void returnsExistingMemberWhenGoogleIdentityAlreadyExists() {
        Member member = new Member("existing@example.com", "existing");
        AuthIdentity identity = new AuthIdentity(member, AuthProvider.GOOGLE, "google-subject", "existing@example.com");
        when(authIdentityRepository.findByProviderAndProviderSubject(AuthProvider.GOOGLE, "google-subject"))
                .thenReturn(Optional.of(identity));

        Member resolved = authIdentityService.resolveGoogleIdentity("google-subject", "other@example.com", "Other");

        assertThat(resolved).isSameAs(member);
        verify(memberRepository, never()).findByEmail(any());
        verify(authIdentityRepository, never()).save(any());
    }

    @Test
    void linksGoogleIdentityToExistingMemberMatchedByEmail() {
        Member member = new Member("member@example.com", "member");
        when(authIdentityRepository.findByProviderAndProviderSubject(AuthProvider.GOOGLE, "google-subject"))
                .thenReturn(Optional.empty());
        when(memberRepository.findByEmail("member@example.com")).thenReturn(Optional.of(member));

        authIdentityService.resolveGoogleIdentity("google-subject", "member@example.com", "New name");

        ArgumentCaptor<AuthIdentity> identityCaptor = ArgumentCaptor.forClass(AuthIdentity.class);
        verify(authIdentityRepository).save(identityCaptor.capture());
        assertThat(identityCaptor.getValue().getMember()).isSameAs(member);
        assertThat(identityCaptor.getValue().getProvider()).isEqualTo(AuthProvider.GOOGLE);
        verify(memberRepository, never()).save(any());
    }

    @Test
    void createsNewMemberWithStableUniqueNicknameWhenProfileNameIsTaken() {
        when(authIdentityRepository.findByProviderAndProviderSubject(AuthProvider.GOOGLE, "google-subject"))
                .thenReturn(Optional.empty());
        when(memberRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());
        when(memberRepository.existsByNickname("Alex Kim")).thenReturn(true);
        when(memberRepository.existsByNickname("Alex Kim-2")).thenReturn(false);
        when(memberRepository.save(any(Member.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Member resolved = authIdentityService.resolveGoogleIdentity("google-subject", "new@example.com", " Alex   Kim ");

        assertThat(resolved.getEmail()).isEqualTo("new@example.com");
        assertThat(resolved.getNickname()).isEqualTo("Alex Kim-2");
        verify(authIdentityRepository).save(any(AuthIdentity.class));
    }

    @Test
    void usesEmailLocalPartWhenGoogleDoesNotProvideAName() {
        when(authIdentityRepository.findByProviderAndProviderSubject(eq(AuthProvider.GOOGLE), any()))
                .thenReturn(Optional.empty());
        when(memberRepository.findByEmail("new.user@example.com")).thenReturn(Optional.empty());
        when(memberRepository.existsByNickname("new.user")).thenReturn(false);
        when(memberRepository.save(any(Member.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Member resolved = authIdentityService.resolveGoogleIdentity("google-subject", "new.user@example.com", " ");

        assertThat(resolved.getNickname()).isEqualTo("new.user");
    }
}
