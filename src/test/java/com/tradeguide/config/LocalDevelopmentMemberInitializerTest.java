package com.tradeguide.config;

import com.tradeguide.domain.member.Member;
import com.tradeguide.repository.member.MemberRepository;
import com.tradeguide.service.member.MemberService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocalDevelopmentMemberInitializerTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private MemberService memberService;

    @InjectMocks
    private LocalDevelopmentMemberInitializer initializer;

    @Test
    void createsLocalMemberWhenItDoesNotExist() throws Exception {
        when(memberRepository.findByEmail("local@tradeguide.dev"))
                .thenReturn(Optional.empty());

        initializer.run(null);

        verify(memberService).createMember("local@tradeguide.dev", "로컬 개발");
    }

    @Test
    void doesNotCreateLocalMemberWhenItAlreadyExists() throws Exception {
        when(memberRepository.findByEmail("local@tradeguide.dev"))
                .thenReturn(Optional.of(new Member("local@tradeguide.dev", "로컬 개발")));

        initializer.run(null);

        verify(memberService, never()).createMember("local@tradeguide.dev", "로컬 개발");
    }
}
