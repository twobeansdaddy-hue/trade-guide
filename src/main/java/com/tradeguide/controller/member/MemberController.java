package com.tradeguide.controller.member;

import com.tradeguide.domain.member.Member;
import com.tradeguide.dto.member.MemberCreateRequest;
import com.tradeguide.dto.member.MemberResponse;
import com.tradeguide.service.member.MemberService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/members")
public class MemberController {

    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    @PostMapping
    public ResponseEntity<MemberResponse> createMember(
            @Valid @RequestBody MemberCreateRequest request
    ) {
        Member member = memberService.createMember(
                request.getEmail(),
                request.getNickname()
        );

        MemberResponse response = MemberResponse.from(member);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }
}