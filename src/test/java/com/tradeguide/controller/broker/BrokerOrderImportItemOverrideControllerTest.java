package com.tradeguide.controller.broker;

import com.tradeguide.domain.broker.BrokerHistoryPage;
import com.tradeguide.domain.broker.BrokerHistoryPageRequest;
import com.tradeguide.domain.broker.BrokerOrderImportItem;
import com.tradeguide.domain.broker.BrokerOrderImportItemOverride;
import com.tradeguide.domain.broker.BrokerOrderImportRun;
import com.tradeguide.domain.broker.BrokerOrderOverrideDecision;
import com.tradeguide.domain.broker.BrokerOrderSkipReason;
import com.tradeguide.domain.broker.BrokerOrderStagingStatus;
import com.tradeguide.dto.ApiErrorCode;
import com.tradeguide.exception.BrokerOrderImportNotFoundException;
import com.tradeguide.exception.BrokerOrderOverrideConflictException;
import com.tradeguide.service.auth.MemberAccessService;
import com.tradeguide.service.broker.BrokerOrderImportItemOverrideService;
import com.tradeguide.service.broker.BrokerOrderImportItemOverrideWriter;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BrokerOrderImportItemOverrideController.class)
@AutoConfigureMockMvc(addFilters = false)
class BrokerOrderImportItemOverrideControllerTest {

    private static final String RUN_PATH = "/api/members/10/portfolios/20/broker-order-imports/7";
    private static final String OVERRIDE_PATH = RUN_PATH + "/items/102/override";
    private static final String REASON = "증권사 화면에서 수기 기록과 별개 체결로 확인";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BrokerOrderImportItemOverrideService brokerOrderImportItemOverrideService;

    @MockitoBean
    private MemberAccessService memberAccessService;

    @Test
    void recordsAnOverrideAndReturnsCreatedWithItsAuditFields() throws Exception {
        BrokerOrderImportItemOverride override = override();
        when(brokerOrderImportItemOverrideService.createOverride(
                10L, 20L, 7L, 102L, BrokerOrderOverrideDecision.ALLOW_LEDGER_WRITE, REASON))
                .thenReturn(new BrokerOrderImportItemOverrideWriter.OverrideResult(override, true));

        mockMvc.perform(post(OVERRIDE_PATH).contentType(MediaType.APPLICATION_JSON).content(body("ALLOW_LEDGER_WRITE", REASON)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(30))
                .andExpect(jsonPath("$.runId").value(7))
                .andExpect(jsonPath("$.itemId").value(102))
                .andExpect(jsonPath("$.externalOrderId").value("order-2"))
                .andExpect(jsonPath("$.originalStagingStatus").value("MANUAL_OVERLAP_SUSPECTED"))
                .andExpect(jsonPath("$.originalSkipReasonCode").value("MANUAL_OVERLAP"))
                .andExpect(jsonPath("$.decision").value("ALLOW_LEDGER_WRITE"))
                .andExpect(jsonPath("$.resultingStagingStatus").value("STAGED"))
                .andExpect(jsonPath("$.reason").value(REASON))
                .andExpect(jsonPath("$.createdByMemberId").value(10))
                .andExpect(jsonPath("$.createdAt").value("2026-09-08T10:00:00"));
    }

    /** 같은 결정의 재요청은 새 기록 없이 기존 기록을 200으로 돌려준다. */
    @Test
    void returnsOkWhenTheSameDecisionWasAlreadyRecorded() throws Exception {
        BrokerOrderImportItemOverride override = override();
        when(brokerOrderImportItemOverrideService.createOverride(
                10L, 20L, 7L, 102L, BrokerOrderOverrideDecision.ALLOW_LEDGER_WRITE, REASON))
                .thenReturn(new BrokerOrderImportItemOverrideWriter.OverrideResult(override, false));

        mockMvc.perform(post(OVERRIDE_PATH).contentType(MediaType.APPLICATION_JSON).content(body("ALLOW_LEDGER_WRITE", REASON)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(30));
    }

    @Test
    void rejectsABlankReasonWithoutCallingTheService() throws Exception {
        mockMvc.perform(post(OVERRIDE_PATH).contentType(MediaType.APPLICATION_JSON).content(body("KEEP_EXCLUDED", "   ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("재판정 사유는 필수입니다."));

        verifyNoInteractions(brokerOrderImportItemOverrideService);
    }

    @Test
    void rejectsAReasonLongerThanTheLimitWithoutCallingTheService() throws Exception {
        mockMvc.perform(post(OVERRIDE_PATH).contentType(MediaType.APPLICATION_JSON)
                        .content(body("KEEP_EXCLUDED", "가".repeat(501))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("재판정 사유는 500자 이하여야 합니다."));

        verifyNoInteractions(brokerOrderImportItemOverrideService);
    }

    @Test
    void rejectsAMissingDecisionWithoutCallingTheService() throws Exception {
        mockMvc.perform(post(OVERRIDE_PATH).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"" + REASON + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("재판정 결정은 필수입니다."));

        verifyNoInteractions(brokerOrderImportItemOverrideService);
    }

    @Test
    void rejectsAnUnknownDecisionWithoutCallingTheService() throws Exception {
        mockMvc.perform(post(OVERRIDE_PATH).contentType(MediaType.APPLICATION_JSON).content(body("MAYBE", REASON)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(brokerOrderImportItemOverrideService);
    }

    @Test
    void reportsAnItemOutsideTheRunAsNotFound() throws Exception {
        when(brokerOrderImportItemOverrideService.createOverride(any(), any(), any(), any(), any(), any()))
                .thenThrow(new BrokerOrderImportNotFoundException("요청한 주문 항목을 찾을 수 없습니다."));

        mockMvc.perform(post(OVERRIDE_PATH).contentType(MediaType.APPLICATION_JSON).content(body("ALLOW_LEDGER_WRITE", REASON)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("요청한 주문 항목을 찾을 수 없습니다."));
    }

    @Test
    void reportsANonSuspectedItemAsConflictWithItsOwnCode() throws Exception {
        when(brokerOrderImportItemOverrideService.createOverride(any(), any(), any(), any(), any(), any()))
                .thenThrow(new BrokerOrderOverrideConflictException(
                        "사람 판단이 필요한 의심 항목만 재판정할 수 있습니다.",
                        ApiErrorCode.ORDER_IMPORT_ITEM_NOT_OVERRIDABLE));

        mockMvc.perform(post(OVERRIDE_PATH).contentType(MediaType.APPLICATION_JSON).content(body("ALLOW_LEDGER_WRITE", REASON)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_IMPORT_ITEM_NOT_OVERRIDABLE"));
    }

    @Test
    void reportsAConflictingDecisionAsConflictWithItsOwnCode() throws Exception {
        when(brokerOrderImportItemOverrideService.createOverride(any(), any(), any(), any(), any(), any()))
                .thenThrow(new BrokerOrderOverrideConflictException(
                        "이 주문 항목은 이미 다른 결정으로 재판정됐습니다.",
                        ApiErrorCode.ORDER_IMPORT_OVERRIDE_CONFLICT));

        mockMvc.perform(post(OVERRIDE_PATH).contentType(MediaType.APPLICATION_JSON).content(body("KEEP_EXCLUDED", REASON)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_IMPORT_OVERRIDE_CONFLICT"));
    }

    @Test
    void blocksOverridesOnAnotherMembersPortfolio() throws Exception {
        doThrow(new AccessDeniedException("다른 회원의 데이터에 접근할 수 없습니다."))
                .when(memberAccessService).requireMemberAccess(any(), any());

        mockMvc.perform(post(OVERRIDE_PATH).contentType(MediaType.APPLICATION_JSON).content(body("ALLOW_LEDGER_WRITE", REASON)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(RUN_PATH + "/item-overrides"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(brokerOrderImportItemOverrideService);
    }

    @Test
    void listsTheOverrideHistoryWithDefaultPagingAndNoAccountIdentifiers() throws Exception {
        BrokerOrderImportItemOverride override = override();
        BrokerHistoryPage<BrokerOrderImportItemOverride> page =
                new BrokerHistoryPage<>(List.of(override), 0, 20, 1L, false);
        when(brokerOrderImportItemOverrideService.getOverrides(eq(10L), eq(20L), eq(7L), any(BrokerHistoryPageRequest.class)))
                .thenReturn(page);

        mockMvc.perform(get(RUN_PATH + "/item-overrides"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(30))
                .andExpect(jsonPath("$.items[0].decision").value("ALLOW_LEDGER_WRITE"))
                .andExpect(jsonPath("$.items[0].accountSequence").doesNotExist())
                .andExpect(jsonPath("$.items[0].maskedAccountNumber").doesNotExist())
                .andExpect(jsonPath("$.items[0].clientSecret").doesNotExist())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.hasNext").value(false));

        ArgumentCaptor<BrokerHistoryPageRequest> pageRequest = ArgumentCaptor.forClass(BrokerHistoryPageRequest.class);
        verify(brokerOrderImportItemOverrideService).getOverrides(eq(10L), eq(20L), eq(7L), pageRequest.capture());
        assertThat(pageRequest.getValue()).isEqualTo(new BrokerHistoryPageRequest(0, 20));
    }

    @Test
    void rejectsAHistoryPageSizeAboveTheServerLimit() throws Exception {
        mockMvc.perform(get(RUN_PATH + "/item-overrides").param("size", "101"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(brokerOrderImportItemOverrideService);
    }

    private String body(String decision, String reason) {
        return "{\"decision\":\"" + decision + "\",\"reason\":\"" + reason + "\"}";
    }

    private BrokerOrderImportItemOverride override() {
        BrokerOrderImportRun run = mock(BrokerOrderImportRun.class);
        when(run.getId()).thenReturn(7L);
        BrokerOrderImportItem item = mock(BrokerOrderImportItem.class);
        when(item.getId()).thenReturn(102L);

        BrokerOrderImportItemOverride override = mock(BrokerOrderImportItemOverride.class);
        when(override.getId()).thenReturn(30L);
        when(override.getRun()).thenReturn(run);
        when(override.getItem()).thenReturn(item);
        when(override.getExternalOrderId()).thenReturn("order-2");
        when(override.getOriginalStagingStatus()).thenReturn(BrokerOrderStagingStatus.MANUAL_OVERLAP_SUSPECTED);
        when(override.getOriginalSkipReasonCode()).thenReturn(BrokerOrderSkipReason.MANUAL_OVERLAP);
        when(override.getDecision()).thenReturn(BrokerOrderOverrideDecision.ALLOW_LEDGER_WRITE);
        when(override.getResultingStagingStatus()).thenReturn(BrokerOrderStagingStatus.STAGED);
        when(override.getReason()).thenReturn(REASON);
        when(override.getCreatedByMemberId()).thenReturn(10L);
        when(override.getCreatedAt()).thenReturn(LocalDateTime.of(2026, 9, 8, 10, 0));
        return override;
    }
}
