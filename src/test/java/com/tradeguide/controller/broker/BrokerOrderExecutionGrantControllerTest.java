package com.tradeguide.controller.broker;

import com.tradeguide.domain.broker.BrokerOrderExecutionGrant;
import com.tradeguide.domain.broker.BrokerOrderExecutionGrantStatus;
import com.tradeguide.service.auth.MemberAccessService;
import com.tradeguide.service.broker.BrokerOrderExecutionGrantService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BrokerOrderExecutionGrantController.class)
@AutoConfigureMockMvc(addFilters = false)
class BrokerOrderExecutionGrantControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BrokerOrderExecutionGrantService brokerOrderExecutionGrantService;

    @MockitoBean
    private MemberAccessService memberAccessService;

    @Test
    void createsGrantAndReturnsCreated() throws Exception {
        BrokerOrderExecutionGrant grant = fakeGrant(BrokerOrderExecutionGrantStatus.ACTIVE);
        when(brokerOrderExecutionGrantService.createGrant(
                eq(10L), eq(20L), eq("track-a-weekly-ma-crossover"), any(BigDecimal.class), eq(5), eq("v1")))
                .thenReturn(grant);

        mockMvc.perform(post("/api/members/10/broker-connections/20/order-execution-grant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "strategyId": "track-a-weekly-ma-crossover",
                                  "maxPositionSizePerOrderPercent": 0.10,
                                  "maxDailyOrderCount": 5,
                                  "consentVersion": "v1"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.strategyId").value("track-a-weekly-ma-crossover"));
    }

    @Test
    void rejectsCreateWhenMemberAccessDenied() throws Exception {
        doThrow(new AccessDeniedException("다른 회원의 데이터에 접근할 수 없습니다."))
                .when(memberAccessService).requireMemberAccess(any(), eq(10L));

        mockMvc.perform(post("/api/members/10/broker-connections/20/order-execution-grant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "strategyId": "track-a-weekly-ma-crossover",
                                  "maxPositionSizePerOrderPercent": 0.10,
                                  "maxDailyOrderCount": 5,
                                  "consentVersion": "v1"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsCreateWhenPositionSizeExceedsDtoCap() throws Exception {
        mockMvc.perform(post("/api/members/10/broker-connections/20/order-execution-grant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "strategyId": "track-a-weekly-ma-crossover",
                                  "maxPositionSizePerOrderPercent": 0.50,
                                  "maxDailyOrderCount": 5,
                                  "consentVersion": "v1"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    // 킬스위치(pause) 엔드포인트를 재개(reactivate) 엔드포인트보다 먼저 검증한다.

    @Test
    void pauseEndpointSwitchesGrantToPaused() throws Exception {
        BrokerOrderExecutionGrant activeGrant = fakeGrant(BrokerOrderExecutionGrantStatus.ACTIVE);
        BrokerOrderExecutionGrant pausedGrant = fakeGrant(BrokerOrderExecutionGrantStatus.PAUSED);
        when(brokerOrderExecutionGrantService.getGrant(10L, 20L)).thenReturn(activeGrant);
        when(brokerOrderExecutionGrantService.pauseGrant(eq(10L), anyLong())).thenReturn(pausedGrant);

        mockMvc.perform(post("/api/members/10/broker-connections/20/order-execution-grant/pause"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAUSED"));
    }

    @Test
    void revokeEndpointSwitchesGrantToRevoked() throws Exception {
        BrokerOrderExecutionGrant activeGrant = fakeGrant(BrokerOrderExecutionGrantStatus.ACTIVE);
        BrokerOrderExecutionGrant revokedGrant = fakeGrant(BrokerOrderExecutionGrantStatus.REVOKED);
        when(brokerOrderExecutionGrantService.getGrant(10L, 20L)).thenReturn(activeGrant);
        when(brokerOrderExecutionGrantService.revokeGrant(eq(10L), anyLong())).thenReturn(revokedGrant);

        mockMvc.perform(post("/api/members/10/broker-connections/20/order-execution-grant/revoke"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVOKED"));
    }

    @Test
    void reactivateEndpointSwitchesGrantBackToActive() throws Exception {
        BrokerOrderExecutionGrant pausedGrant = fakeGrant(BrokerOrderExecutionGrantStatus.PAUSED);
        BrokerOrderExecutionGrant activeGrant = fakeGrant(BrokerOrderExecutionGrantStatus.ACTIVE);
        when(brokerOrderExecutionGrantService.getGrant(10L, 20L)).thenReturn(pausedGrant);
        when(brokerOrderExecutionGrantService.reactivateGrant(eq(10L), anyLong())).thenReturn(activeGrant);

        mockMvc.perform(post("/api/members/10/broker-connections/20/order-execution-grant/reactivate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void getEndpointReturnsCurrentGrant() throws Exception {
        BrokerOrderExecutionGrant grant = fakeGrant(BrokerOrderExecutionGrantStatus.ACTIVE);
        when(brokerOrderExecutionGrantService.getGrant(10L, 20L)).thenReturn(grant);

        mockMvc.perform(get("/api/members/10/broker-connections/20/order-execution-grant"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    private BrokerOrderExecutionGrant fakeGrant(BrokerOrderExecutionGrantStatus status) {
        BrokerOrderExecutionGrant grant = mock(BrokerOrderExecutionGrant.class);
        var connection = mock(com.tradeguide.domain.broker.BrokerConnection.class);
        when(connection.getId()).thenReturn(20L);
        when(grant.getId()).thenReturn(99L);
        when(grant.getBrokerConnection()).thenReturn(connection);
        when(grant.getStrategyId()).thenReturn("track-a-weekly-ma-crossover");
        when(grant.getMaxPositionSizePerOrderPercent()).thenReturn(new BigDecimal("0.10"));
        when(grant.getMaxDailyOrderCount()).thenReturn(5);
        when(grant.getStatus()).thenReturn(status);
        when(grant.getConsentedAt()).thenReturn(LocalDateTime.now());
        when(grant.getConsentVersion()).thenReturn("v1");
        when(grant.getCreatedAt()).thenReturn(LocalDateTime.now());
        when(grant.getUpdatedAt()).thenReturn(LocalDateTime.now());
        return grant;
    }
}
