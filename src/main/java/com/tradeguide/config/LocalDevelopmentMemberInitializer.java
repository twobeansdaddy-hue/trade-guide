package com.tradeguide.config;

import com.tradeguide.repository.member.MemberRepository;
import com.tradeguide.service.member.MemberService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("local")
public class LocalDevelopmentMemberInitializer implements ApplicationRunner {

    private static final String LOCAL_MEMBER_EMAIL = "local@tradeguide.dev";
    private static final String LOCAL_MEMBER_NICKNAME = "로컬 개발";

    private final MemberRepository memberRepository;
    private final MemberService memberService;

    public LocalDevelopmentMemberInitializer(
            MemberRepository memberRepository,
            MemberService memberService
    ) {
        this.memberRepository = memberRepository;
        this.memberService = memberService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (memberRepository.findByEmail(LOCAL_MEMBER_EMAIL).isEmpty()) {
            memberService.createMember(LOCAL_MEMBER_EMAIL, LOCAL_MEMBER_NICKNAME);
        }
    }
}
