package com.tradeguide.controller.broker;

import com.tradeguide.domain.broker.BrokerAccount;
import com.tradeguide.domain.broker.BrokerConnection;
import com.tradeguide.domain.broker.BrokerProvider;
import com.tradeguide.domain.member.Member;
import com.tradeguide.exception.BrokerConnectionDeletionBlockedException;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
                eq(Map.of("clientId", "test-client-id", "clientSecret", "test-client-secret"))
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
                Map.of("clientId", "test-client-id", "clientSecret", "test-client-secret")
        );
    }

    @Test
    void createsBrokerConnectionFromDynamicCredentialMap() throws Exception {
        BrokerConnection connection = mock(BrokerConnection.class);
        when(connection.getProvider()).thenReturn(BrokerProvider.TOSS_SECURITIES);
        when(connection.getDisplayName()).thenReturn("개인 토스증권");
        when(brokerConnectionService.createBrokerConnection(
                eq(10L),
                eq(BrokerProvider.TOSS_SECURITIES),
                eq("개인 토스증권"),
                eq(Map.of("clientId", "test-client-id", "clientSecret", "test-client-secret"))
        )).thenReturn(connection);

        mockMvc.perform(post("/api/members/10/broker-connections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "provider": "TOSS_SECURITIES",
                                  "displayName": "개인 토스증권",
                                  "credentials": {
                                    "clientId": "test-client-id",
                                    "clientSecret": "test-client-secret"
                                  }
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.clientId").doesNotExist())
                .andExpect(jsonPath("$.clientSecret").doesNotExist())
                .andExpect(jsonPath("$.credentials").doesNotExist());
    }

    @Test
    void rejectsRequestThatMixesCredentialFormsWithoutLeakingValues() throws Exception {
        String marker = "leak-marker-value";

        String body = mockMvc.perform(post("/api/members/10/broker-connections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "provider": "TOSS_SECURITIES",
                                  "displayName": "개인 토스증권",
                                  "clientId": "%s",
                                  "clientSecret": "%s",
                                  "credentials": { "clientId": "%s" }
                                }
                                """.formatted(marker, marker, marker)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "자격 증명은 credentials 항목과 개별 항목 중 한 형태로만 보낼 수 있습니다."))
                .andReturn()
                .getResponse()
                .getContentAsString();

        // 오류 응답에 입력한 자격 증명 문자열이 어떤 형태로도 섞이지 않아야 한다.
        assertThat(body).doesNotContain(marker);
        verifyNoInteractions(brokerConnectionService);
    }

    @Test
    void rejectsRequestWithoutAnyCredentials() throws Exception {
        mockMvc.perform(post("/api/members/10/broker-connections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "provider": "TOSS_SECURITIES",
                                  "displayName": "개인 토스증권"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("자격 증명은 필수입니다."));

        verifyNoInteractions(brokerConnectionService);
    }

    @Test
    void deletesBrokerConnectionWithHistoricalSnapshots() throws Exception {
        mockMvc.perform(delete("/api/members/10/broker-connections/5"))
                .andExpect(status().isNoContent());

        verify(brokerConnectionService).deleteBrokerConnection(10L, 5L);
    }

    @Test
    void returnsConflictWithActionableMessageWhenActiveOpeningBalanceImportBlocksDelete() throws Exception {
        doThrow(new BrokerConnectionDeletionBlockedException(
                "이 증권사 연결로 반영한 개시 잔고 매매 기록이 1건 남아 있어 연결을 삭제할 수 없습니다. "
                        + "포트폴리오의 개시 잔고 이력에서 해당 승인을 먼저 취소한 뒤 다시 삭제해 주세요."
        )).when(brokerConnectionService).deleteBrokerConnection(10L, 5L);

        mockMvc.perform(delete("/api/members/10/broker-connections/5"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "이 증권사 연결로 반영한 개시 잔고 매매 기록이 1건 남아 있어 연결을 삭제할 수 없습니다. "
                                + "포트폴리오의 개시 잔고 이력에서 해당 승인을 먼저 취소한 뒤 다시 삭제해 주세요."
                ));
    }

    @Test
    void returnsServiceUnavailableWhenEncryptionKeyIsNotConfigured() throws Exception {
        when(brokerConnectionService.createBrokerConnection(
                eq(10L),
                eq(BrokerProvider.TOSS_SECURITIES),
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

    @Test
    void verifyResponseExposesOnlyProviderCurrentAccounts() throws Exception {
        BrokerConnection connection =
                new BrokerConnection(new Member("broker@example.com", "broker-user"),
                        BrokerProvider.TOSS_SECURITIES, "개인 토스증권");
        ReflectionTestUtils.setField(connection, "id", 5L);
        BrokerAccount current = account(100L, "*****5678");
        BrokerAccount closed = account(101L, "*****1234");
        connection.reconcileVerifiedAccounts(List.of(current, closed));
        // 재검증에서 증권사가 더 이상 반환하지 않는 계좌는 행만 남고 응답에서는 빠진다.
        connection.reconcileVerifiedAccounts(List.of(current));
        connection.markConnected("*****5678");
        when(brokerConnectionService.verifyBrokerConnection(10L, 5L)).thenReturn(connection);

        mockMvc.perform(post("/api/members/10/broker-connections/5/verify"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONNECTED"))
                .andExpect(jsonPath("$.maskedAccountLabel").value("*****5678"))
                .andExpect(jsonPath("$.accounts.length()").value(1))
                .andExpect(jsonPath("$.accounts[0].id").value(100))
                .andExpect(jsonPath("$.accounts[0].maskedAccountNumber").value("*****5678"))
                .andExpect(jsonPath("$.accounts[0].encryptedAccountSequence").doesNotExist());
    }

    private BrokerAccount account(Long id, String maskedAccountNumber) {
        BrokerAccount account = new BrokerAccount(
                "encrypted-sequence-" + id, "sequence-iv", maskedAccountNumber, "위탁", 1
        );
        ReflectionTestUtils.setField(account, "id", id);
        return account;
    }
}
