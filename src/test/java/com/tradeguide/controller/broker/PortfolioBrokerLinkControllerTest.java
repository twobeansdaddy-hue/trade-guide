package com.tradeguide.controller.broker;

import com.tradeguide.domain.broker.BrokerAccount;
import com.tradeguide.domain.broker.BrokerConnection;
import com.tradeguide.domain.broker.BrokerHoldingComparison;
import com.tradeguide.domain.broker.BrokerHoldingPreview;
import com.tradeguide.domain.broker.BrokerHoldingPreviewItem;
import com.tradeguide.domain.broker.BrokerProvider;
import com.tradeguide.domain.portfolio.PortfolioBrokerLink;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.exception.BrokerConnectionUnavailableException;
import com.tradeguide.service.auth.MemberAccessService;
import com.tradeguide.service.broker.BrokerHoldingPreviewService;
import com.tradeguide.service.broker.PortfolioBrokerLinkService;
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
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PortfolioBrokerLinkController.class)
@AutoConfigureMockMvc(addFilters = false)
class PortfolioBrokerLinkControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PortfolioBrokerLinkService portfolioBrokerLinkService;

    @MockitoBean
    private BrokerHoldingPreviewService brokerHoldingPreviewService;

    @MockitoBean
    private MemberAccessService memberAccessService;

    @Test
    void listsLinkCandidatesWithMaskedAccountOnly() throws Exception {
        PortfolioBrokerLinkService.BrokerLinkCandidate candidate =
                new PortfolioBrokerLinkService.BrokerLinkCandidate(connection(), account());
        when(portfolioBrokerLinkService.getLinkCandidates(10L, 20L)).thenReturn(List.of(candidate));

        mockMvc.perform(get("/api/members/10/portfolios/20/broker-link-candidates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].brokerConnectionId").value(1))
                .andExpect(jsonPath("$[0].provider").value("TOSS_SECURITIES"))
                .andExpect(jsonPath("$[0].brokerAccountId").value(100))
                .andExpect(jsonPath("$[0].maskedAccountNumber").value("*****1234"))
                .andExpect(jsonPath("$[0].accountSequence").doesNotExist())
                .andExpect(jsonPath("$[0].encryptedAccountSequence").doesNotExist())
                .andExpect(jsonPath("$[0].clientId").doesNotExist())
                .andExpect(jsonPath("$[0].clientSecret").doesNotExist());
    }

    @Test
    void linksBrokerAccountToPortfolio() throws Exception {
        PortfolioBrokerLink link = link();
        when(portfolioBrokerLinkService.linkBrokerAccount(10L, 20L, 1L, 100L)).thenReturn(link);

        mockMvc.perform(put("/api/members/10/portfolios/20/broker-links/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"brokerAccountId": 100}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.brokerConnectionId").value(1))
                .andExpect(jsonPath("$.brokerAccountId").value(100))
                .andExpect(jsonPath("$.maskedAccountNumber").value("*****1234"))
                .andExpect(jsonPath("$.encryptedAccountSequence").doesNotExist());

        verify(portfolioBrokerLinkService).linkBrokerAccount(10L, 20L, 1L, 100L);
    }

    @Test
    void rejectsLinkRequestWithoutAccountSelection() throws Exception {
        mockMvc.perform(put("/api/members/10/portfolios/20/broker-links/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("증권사 계좌 선택은 필수입니다."));

        verifyNoInteractions(portfolioBrokerLinkService);
    }

    @Test
    void unlinksBrokerAccount() throws Exception {
        mockMvc.perform(delete("/api/members/10/portfolios/20/broker-links/1"))
                .andExpect(status().isNoContent());

        verify(portfolioBrokerLinkService).unlinkBrokerAccount(10L, 20L, 1L);
    }

    @Test
    void returnsReadOnlyHoldingPreviewWithSourceAndSyncTime() throws Exception {
        when(brokerHoldingPreviewService.getHoldingPreview(10L, 20L)).thenReturn(new BrokerHoldingPreview(
                BrokerProvider.TOSS_SECURITIES,
                1L,
                "*****1234",
                LocalDateTime.of(2026, 9, 4, 9, 30),
                List.of(new BrokerHoldingPreviewItem(
                        Market.US,
                        "SOXL",
                        new BigDecimal("30"),
                        new BigDecimal("20.50"),
                        new BigDecimal("25"),
                        BrokerHoldingComparison.QUANTITY_MISMATCH
                )),
                1
        ));

        mockMvc.perform(post("/api/members/10/portfolios/20/broker-sync-preview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("TOSS_SECURITIES"))
                .andExpect(jsonPath("$.maskedAccountNumber").value("*****1234"))
                .andExpect(jsonPath("$.syncedAt").value("2026-09-04T09:30:00"))
                .andExpect(jsonPath("$.unsupportedMarketCount").value(1))
                .andExpect(jsonPath("$.items[0].ticker").value("SOXL"))
                .andExpect(jsonPath("$.items[0].brokerQuantity").value(30))
                .andExpect(jsonPath("$.items[0].tradeGuideQuantity").value(25))
                .andExpect(jsonPath("$.items[0].comparison").value("QUANTITY_MISMATCH"))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.accountSequence").doesNotExist());
    }

    @Test
    void returnsServiceUnavailableWhenBrokerPreviewIsNotAvailable() throws Exception {
        when(brokerHoldingPreviewService.getHoldingPreview(10L, 20L))
                .thenThrow(new BrokerConnectionUnavailableException("증권사 연결 암호화 키가 설정되지 않았습니다."));

        mockMvc.perform(post("/api/members/10/portfolios/20/broker-sync-preview"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("증권사 연결 암호화 키가 설정되지 않았습니다."));
    }

    @Test
    void deniesAccessToAnotherMembersPortfolioBrokerLinks() throws Exception {
        doThrow(new AccessDeniedException("다른 회원의 데이터에 접근할 수 없습니다."))
                .when(memberAccessService).requireMemberAccess(any(), any());

        mockMvc.perform(get("/api/members/99/portfolios/20/broker-links"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(portfolioBrokerLinkService);
    }

    @Test
    void deniesPreviewForAnotherMembersPortfolio() throws Exception {
        doThrow(new AccessDeniedException("다른 회원의 데이터에 접근할 수 없습니다."))
                .when(memberAccessService).requireMemberAccess(any(), any());

        mockMvc.perform(post("/api/members/99/portfolios/20/broker-sync-preview"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(brokerHoldingPreviewService);
    }

    private BrokerConnection connection() {
        BrokerConnection connection = mock(BrokerConnection.class);
        when(connection.getId()).thenReturn(1L);
        when(connection.getProvider()).thenReturn(BrokerProvider.TOSS_SECURITIES);
        when(connection.getDisplayName()).thenReturn("개인 토스증권");
        return connection;
    }

    private BrokerAccount account() {
        BrokerAccount account = mock(BrokerAccount.class);
        when(account.getId()).thenReturn(100L);
        when(account.getMaskedAccountNumber()).thenReturn("*****1234");
        when(account.getAccountType()).thenReturn("위탁");
        return account;
    }

    private PortfolioBrokerLink link() {
        BrokerConnection connection = connection();
        BrokerAccount account = account();

        PortfolioBrokerLink link = mock(PortfolioBrokerLink.class);
        when(link.getId()).thenReturn(5L);
        when(link.getBrokerConnection()).thenReturn(connection);
        when(link.getBrokerAccount()).thenReturn(account);
        return link;
    }
}
