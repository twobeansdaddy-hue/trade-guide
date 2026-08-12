package com.tradeguide.dto.member;

import com.tradeguide.domain.member.Member;

public class MemberResponse {

    private final Long id;
    private final String email;
    private final String nickname;

    private MemberResponse(Long id, String email, String nickname) {
        this.id = id;
        this.email = email;
        this.nickname = nickname;
    }

    public static MemberResponse from(Member member) {
        return new MemberResponse(
                member.getId(),
                member.getEmail(),
                member.getNickname()
        );
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getNickname() {
        return nickname;
    }
}