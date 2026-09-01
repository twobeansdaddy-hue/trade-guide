package com.tradeguide.service.auth;

import com.tradeguide.domain.auth.AuthIdentity;
import com.tradeguide.domain.auth.AuthProvider;
import com.tradeguide.domain.member.Member;
import com.tradeguide.repository.auth.AuthIdentityRepository;
import com.tradeguide.repository.member.MemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthIdentityService {
    private static final int MEMBER_FIELD_MAX_LENGTH = 255;

    private final AuthIdentityRepository authIdentityRepository;
    private final MemberRepository memberRepository;

    public AuthIdentityService(AuthIdentityRepository authIdentityRepository, MemberRepository memberRepository) {
        this.authIdentityRepository = authIdentityRepository;
        this.memberRepository = memberRepository;
    }

    @Transactional
    public Member resolveGoogleIdentity(String subject, String email, String displayName) {
        validateRequired(subject, "Google subject");
        validateRequired(email, "Google email");
        return authIdentityRepository.findByProviderAndProviderSubject(AuthProvider.GOOGLE, subject)
                .map(AuthIdentity::getMember)
                .orElseGet(() -> linkNewGoogleIdentity(subject, email, displayName));
    }

    @Transactional(readOnly = true)
    public Member getMember(AuthProvider provider, String subject) {
        return authIdentityRepository.findByProviderAndProviderSubject(provider, subject)
                .map(AuthIdentity::getMember)
                .orElseThrow(() -> new IllegalStateException("로그인 사용자 식별 정보를 찾을 수 없습니다."));
    }

    private Member linkNewGoogleIdentity(String subject, String email, String displayName) {
        Member member = memberRepository.findByEmail(email)
                .orElseGet(() -> memberRepository.save(new Member(email, nextAvailableNickname(displayName, email))));
        authIdentityRepository.save(new AuthIdentity(member, AuthProvider.GOOGLE, subject, email));
        return member;
    }

    private String nextAvailableNickname(String displayName, String email) {
        String base = preferredNickname(displayName, email);
        String candidate = base;
        int suffix = 2;
        while (memberRepository.existsByNickname(candidate)) {
            String suffixText = "-" + suffix++;
            candidate = truncate(base, MEMBER_FIELD_MAX_LENGTH - suffixText.length()) + suffixText;
        }
        return candidate;
    }

    private String preferredNickname(String displayName, String email) {
        String preferred = displayName == null ? "" : displayName.trim().replaceAll("\\s+", " ");
        if (preferred.isBlank()) {
            int atIndex = email.indexOf('@');
            preferred = atIndex > 0 ? email.substring(0, atIndex) : "google-user";
        }
        return truncate(preferred, MEMBER_FIELD_MAX_LENGTH);
    }

    private String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private void validateRequired(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
    }
}
