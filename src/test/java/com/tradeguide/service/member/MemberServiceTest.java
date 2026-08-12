package com.tradeguide.service.member;

import com.tradeguide.domain.member.Member;
import com.tradeguide.repository.member.MemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @InjectMocks
    private MemberService memberService;

    @Test
    void createsMemberWhenEmailAndNicknameAreAvailable() {
        when(memberRepository.existsByEmail("beans@example.com"))
                .thenReturn(false);
        when(memberRepository.existsByNickname("beans"))
                .thenReturn(false);
        when(memberRepository.save(any(Member.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Member member = memberService.createMember(
                "beans@example.com",
                "beans"
        );

        assertThat(member.getEmail()).isEqualTo("beans@example.com");
        assertThat(member.getNickname()).isEqualTo("beans");
        verify(memberRepository).save(any(Member.class));
    }

    @Test
    void throwsExceptionWhenEmailAlreadyExists() {
        when(memberRepository.existsByEmail("beans@example.com"))
                .thenReturn(true);

        assertThatThrownBy(() ->
                memberService.createMember("beans@example.com", "beans")
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이미 사용 중인 이메일입니다.");

        verify(memberRepository, never()).save(any(Member.class));
    }

    @Test
    void throwsExceptionWhenNicknameAlreadyExists() {
        when(memberRepository.existsByEmail("beans@example.com"))
                .thenReturn(false);
        when(memberRepository.existsByNickname("beans"))
                .thenReturn(true);

        assertThatThrownBy(() ->
                memberService.createMember("beans@example.com", "beans")
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이미 사용 중인 닉네임입니다.");

        verify(memberRepository, never()).save(any(Member.class));
    }
}