package com.tradeguide.controller.broker;

import com.tradeguide.domain.broker.BrokerConnection;
import com.tradeguide.domain.broker.BrokerProvider;
import com.tradeguide.domain.member.Member;
import com.tradeguide.exception.BrokerConnectionUnavailableException;
import com.tradeguide.service.auth.MemberAccessService;
import com.tradeguide.service.broker.BrokerConnectionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BrokerConnectionController.class)
@AutoConfigureMockMvc(addFilters = false)
class BrokerConnectionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BrokerConnectionService brokerConnectionService;

    @MockitoBean
    private MemberAccessService memberAccessService;

    @Test
    void getsSafeBrokerConnectionResponsesWithoutCredentials() throws Exception {
        BrokerConnection connection = mock(BrokerConnection.class);
        when(connection.getId()).thenReturn(1L);
        when(connection.getProvider()).thenReturn(BrokerProvider.TOSS_SECURITIES);
        when(connection.getDisplayName()).thenReturn("개인 토스증권");
        when(brokerConnectionService.getBrokerConnections(10L)).thenReturn(List.of(connection));

        mockMvc.perform(get("/api/members/10/broker-connections"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].provider").value("TOSS_SECURITIES"))
                .andExpect(jsonPath("$[0].displayName").value("개인 토스증권"))
                .andExpect(jsonPath("$[0].clientId").doesNotExist())
                .andExpect(jsonPath("$[0].clientSecret").doesNotExist());
    }

    @Test
    void createsBrokerConnection() throws Exception {
        BrokerConnection connection = mock(BrokerConnection.class);
        when(connection.getProvider()).thenReturn(BrokerProvider.TOSS_SECURITIES);
        when(connection.getDisplayName()).thenReturn("개인 토스증권");
        when(brokerConnectionService.createBrokerConnection(
                eq(10L),
                eq(BrokerProvider.TOSS_SECURITIES),
                eq("개인 토스증권"),
                eq("test-client-id"),
                eq("test-client-secret")
        )).thenReturn(connection);

        mockMvc.perform(post("/api/members/10/broker-connections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "provider": "TOSS_SECURITIES",
                                  "displayName": "개인 토스증권",
                                  "clientId": "test-client-id",
                                  "clientSecret": "test-client-secret"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.provider").value("TOSS_SECURITIES"))
                .andExpect(jsonPath("$.displayName").value("개인 토스증권"))
                .andExpect(jsonPath("$.clientId").doesNotExist())
                .andExpect(jsonPath("$.clientSecret").doesNotExist());

        verify(brokerConnectionService).createBrokerConnection(
                10L,
                BrokerProvider.TOSS_SECURITIES,
                "개인 토스증권",
                "test-client-id",
                "test-client-secret"
        );
    }

    @Test
    void rejectsBlankBrokerCredentialRequest() throws Exception {
        mockMvc.perform(post("/api/members/10/broker-connections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "provider": "TOSS_SECURITIES",
                                  "displayName": "개인 토스증권",
                                  "clientId": "",
                                  "clientSecret": "test-client-secret"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Client ID는 필수입니다."));

        verifyNoInteractions(brokerConnectionService);
    }

    @Test
    void returnsServiceUnavailableWhenEncryptionKeyIsNotConfigured() throws Exception {
        when(brokerConnectionService.createBrokerConnection(
                eq(10L),
                eq(BrokerProvider.TOSS_SECURITIES),
                any(),
                any(),
                any()
        )).thenThrow(new BrokerConnectionUnavailableException(
                "증권사 연결 암호화 키가 설정되지 않았습니다."
        ));

        mockMvc.perform(post("/api/members/10/broker-connections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "provider": "TOSS_SECURITIES",
                                  "displayName": "개인 토스증권",
                                  "clientId": "test-client-id",
                                  "clientSecret": "test-client-secret"
                                }
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message")
                        .value("증권사 연결 암호화 키가 설정되지 않았습니다."));
    }
}
