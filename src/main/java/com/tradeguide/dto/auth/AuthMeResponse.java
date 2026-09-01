package com.tradeguide.dto.auth;

import com.tradeguide.domain.auth.AuthProvider;
import com.tradeguide.domain.member.Member;

public record AuthMeResponse(Long id, String email, String nickname, AuthProvider provider) {
    public static AuthMeResponse from(Member member, AuthProvider provider) {
        return new AuthMeResponse(member.getId(), member.getEmail(), member.getNickname(), provider);
    }
}
