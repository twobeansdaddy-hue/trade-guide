package com.tradeguide.dto.member;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class MemberCreateRequest {

    @NotBlank(message = "이메일은 필수입니다.")
    @Email(message = "올바른 이메일 형식이 아닙니다.")
    private final String email;

    @NotBlank(message = "닉네임은 필수입니다.")
    private final String nickname;

    public MemberCreateRequest(String email, String nickname) {
        this.email = email;
        this.nickname = nickname;
    }

    public String getEmail() {
        return email;
    }

    public String getNickname() {
        return nickname;
    }
}
