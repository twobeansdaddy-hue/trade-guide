package com.tradeguide.controller.strategy;

import com.tradeguide.dto.strategy.PremarketGuideResponse;
import com.tradeguide.service.auth.MemberAccessService;
import com.tradeguide.service.strategy.PremarketGuideService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PremarketGuideController.class)
@AutoConfigureMockMvc(addFilters = false)
class PremarketGuideControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PremarketGuideService premarketGuideService;

    @MockitoBean
    private MemberAccessService memberAccessService;

    @Test
    void getsNotGeneratedStateWithoutCallingMarketData() throws Exception {
        when(premarketGuideService.getToday(1L, 10L))
                .thenReturn(PremarketGuideResponse.notGenerated(LocalDate.of(2026, 9, 14)));

        mockMvc.perform(get("/api/members/1/portfolios/10/premarket-guide/today")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NOT_GENERATED"))
                .andExpect(jsonPath("$.guideDate").value("2026-09-14"));

        verify(premarketGuideService).getToday(1L, 10L);
    }

    @Test
    void generatesTodayGuideWithForceFlag() throws Exception {
        when(premarketGuideService.generateToday(1L, 10L, true))
                .thenReturn(PremarketGuideResponse.notGenerated(LocalDate.of(2026, 9, 14)));

        mockMvc.perform(post("/api/members/1/portfolios/10/premarket-guide/today")
                        .queryParam("force", "true")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NOT_GENERATED"));

        verify(premarketGuideService).generateToday(1L, 10L, true);
    }
}
