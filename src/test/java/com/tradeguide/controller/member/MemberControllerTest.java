package com.tradeguide.controller.member;

import com.tradeguide.domain.member.Member;
import com.tradeguide.service.member.MemberService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MemberController.class)
@AutoConfigureMockMvc(addFilters = false)
class MemberControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MemberService memberService;

    @Test
    void createsMember() throws Exception {
        Member member = mock(Member.class);
        when(member.getId()).thenReturn(1L);
        when(member.getEmail()).thenReturn("beans@example.com");
        when(member.getNickname()).thenReturn("beans");

        when(memberService.createMember("beans@example.com", "beans"))
                .thenReturn(member);

        mockMvc.perform(post("/api/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "beans@example.com",
                                  "nickname": "beans"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.email").value("beans@example.com"))
                .andExpect(jsonPath("$.nickname").value("beans"));

        verify(memberService).createMember(
                "beans@example.com",
                "beans"
        );
    }

    @Test
    void returnsBadRequestWhenEmailIsInvalid() throws Exception {
        mockMvc.perform(post("/api/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "invalid-email",
                                  "nickname": "beans"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("올바른 이메일 형식이 아닙니다."));

        verifyNoInteractions(memberService);
    }

    @Test
    void returnsBadRequestWhenEmailAlreadyExists() throws Exception {
        when(memberService.createMember("beans@example.com", "beans"))
                .thenThrow(new IllegalArgumentException(
                        "이미 사용 중인 이메일입니다."
                ));

        mockMvc.perform(post("/api/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "beans@example.com",
                                  "nickname": "beans"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("이미 사용 중인 이메일입니다."));
    }
}
