package com.tradeguide.controller.broker;

import com.tradeguide.domain.broker.BrokerAccount;
import com.tradeguide.domain.broker.BrokerHistoryPage;
import com.tradeguide.domain.broker.BrokerHistoryPageRequest;
import com.tradeguide.domain.broker.BrokerConnection;
import com.tradeguide.domain.broker.BrokerOrderImportApprovalAssessment;
import com.tradeguide.domain.broker.BrokerOrderImportApprovalBlocker;
import com.tradeguide.domain.broker.BrokerOrderImportCounts;
import com.tradeguide.domain.broker.BrokerOrderImportItem;
import com.tradeguide.domain.broker.BrokerOrderImportItemFilter;
import com.tradeguide.domain.broker.BrokerOrderImportReconciliationLine;
import com.tradeguide.domain.broker.BrokerOrderImportRun;
import com.tradeguide.domain.broker.BrokerOrderImportRunStatus;
import com.tradeguide.domain.broker.BrokerOrderLifecycle;
import com.tradeguide.domain.broker.BrokerOrderReconciliationStatus;
import com.tradeguide.domain.broker.BrokerOrderSide;
import com.tradeguide.domain.broker.BrokerOrderSkipReason;
import com.tradeguide.domain.broker.BrokerOrderStagingStatus;
import com.tradeguide.domain.broker.BrokerProvider;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.dto.ApiErrorCode;
import com.tradeguide.exception.BrokerConnectionUnavailableException;
import com.tradeguide.exception.BrokerOrderApprovalConflictException;
import com.tradeguide.exception.BrokerOrderImportNotFoundException;
import com.tradeguide.exception.BrokerOrderImportUnprocessableException;
import com.tradeguide.exception.PortfolioNotFoundException;
import com.tradeguide.service.auth.MemberAccessService;
import com.tradeguide.service.broker.BrokerOrderImportApprovalService;
import com.tradeguide.service.broker.BrokerOrderImportApprovalWriter;
import com.tradeguide.service.broker.BrokerOrderImportService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BrokerOrderImportController.class)
@AutoConfigureMockMvc(addFilters = false)
class BrokerOrderImportControllerTest {

    private static final String BASE_PATH = "/api/members/10/portfolios/20/broker-order-imports";
    private static final String CREATE_BODY = """
            {"orderedFrom":"2026-09-01","orderedTo":"2026-09-05"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BrokerOrderImportService brokerOrderImportService;

    @MockitoBean
    private BrokerOrderImportApprovalService brokerOrderImportApprovalService;

    @MockitoBean
    private MemberAccessService memberAccessService;

    @Test
    void createsAPreviewAndReturnsItsApprovalAssessmentAndReconciliation() throws Exception {
        // 목을 만드는 것 자체가 스터빙이므로, 바깥 when(...) 안에서 만들면 스터빙이 중첩돼 깨진다.
        BrokerOrderImportRun run = run();
        BrokerOrderImportService.RunDetail detail = runDetail(run);
        when(brokerOrderImportService.createPreview(10L, 20L, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 5)))
                .thenReturn(run);
        when(brokerOrderImportService.getRun(10L, 20L, 7L)).thenReturn(detail);

        mockMvc.perform(post(BASE_PATH).contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.run.id").value(7))
                .andExpect(jsonPath("$.run.provider").value("TOSS_SECURITIES"))
                .andExpect(jsonPath("$.run.status").value("STAGED"))
                .andExpect(jsonPath("$.run.requestedOrderedFrom").value("2026-09-01"))
                .andExpect(jsonPath("$.run.queriedOrderedFrom").value("2026-08-30"))
                .andExpect(jsonPath("$.run.counts.fetchedCount").value(2))
                .andExpect(jsonPath("$.run.counts.stagedCount").value(1))
                .andExpect(jsonPath("$.run.counts.pendingSettlementCount").value(1))
                .andExpect(jsonPath("$.run.reconciliationStatus").value("MISMATCHED"))
                .andExpect(jsonPath("$.approval.stagedCount").value(1))
                .andExpect(jsonPath("$.approval.eligibleCount").value(1))
                .andExpect(jsonPath("$.approval.writableCount").value(1))
                .andExpect(jsonPath("$.reconciliation[0].quantityDifference").value(-2));
    }

    /**
     * 생성 응답도 상세와 같은 DTO다. 그쪽만 줄이고 여기를 놓치면 실행 직후 응답 하나로
     * 여전히 수천 건이 나간다. 응답 크기 상한이 없어지는 지점이 바로 여기다.
     */
    @Test
    void neverReturnsTheFullItemListFromThePreviewOrDetailResponse() throws Exception {
        BrokerOrderImportRun run = run();
        BrokerOrderImportService.RunDetail detail = runDetail(run);
        when(brokerOrderImportService.createPreview(any(), any(), any(), any())).thenReturn(run);
        when(brokerOrderImportService.getRun(10L, 20L, 7L)).thenReturn(detail);

        mockMvc.perform(post(BASE_PATH).contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items").doesNotExist());

        mockMvc.perform(get(BASE_PATH + "/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").doesNotExist());
    }

    /**
     * 승인 버튼의 판정 근거가 전부 응답에 있어야 한다. 하나라도 빠지면 화면은 다시 항목을
     * 세는 쪽으로 돌아가고, 항목이 나뉘어 오는 지금 그 셈은 조용히 틀린다.
     */
    @Test
    void reportsWhyARunCannotBeApprovedWithoutItsItems() throws Exception {
        BrokerOrderImportService.RunDetail detail = new BrokerOrderImportService.RunDetail(
                run(),
                new BrokerOrderImportApprovalAssessment(
                        4, 0, 4, 0, 0, 6, 2, 0, 0, 2, 1,
                        LocalDateTime.of(2026, 9, 3, 0, 0),
                        true, false,
                        false,
                        BrokerOrderImportApprovalBlocker.ALL_BEFORE_BASELINE),
                List.of(reconciliationLine())
        );
        when(brokerOrderImportService.getRun(10L, 20L, 7L)).thenReturn(detail);

        mockMvc.perform(get(BASE_PATH + "/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approval.stagedCount").value(4))
                .andExpect(jsonPath("$.approval.eligibleCount").value(0))
                .andExpect(jsonPath("$.approval.baselineExcludedCount").value(4))
                .andExpect(jsonPath("$.approval.alreadyLinkedCount").value(0))
                .andExpect(jsonPath("$.approval.writableCount").value(0))
                .andExpect(jsonPath("$.approval.excludedCount").value(6))
                .andExpect(jsonPath("$.approval.suspectedCount").value(2))
                .andExpect(jsonPath("$.approval.amountMismatchCount").value(1))
                .andExpect(jsonPath("$.approval.baselineAt").value("2026-09-03T00:00:00"))
                .andExpect(jsonPath("$.approval.approvable").value(false))
                .andExpect(jsonPath("$.approval.blocker").value("ALL_BEFORE_BASELINE"));
    }

    /**
     * 마스킹된 계좌번호 외에는 어떤 식별 정보도 나가면 안 된다. 응답에 한 번 새면 로그·캐시·화면
     * 어디로든 퍼진다.
     */
    @Test
    void neverExposesCredentialsAccountSequenceOrRawProviderPayloads() throws Exception {
        BrokerOrderImportService.RunDetail detail = runDetail(run());
        when(brokerOrderImportService.getRun(10L, 20L, 7L)).thenReturn(detail);

        mockMvc.perform(get(BASE_PATH + "/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.run.maskedAccountNumber").value("*****1234"))
                .andExpect(jsonPath("$.run.accountSequence").doesNotExist())
                .andExpect(jsonPath("$.run.clientId").doesNotExist())
                .andExpect(jsonPath("$.run.clientSecret").doesNotExist())
                .andExpect(jsonPath("$.run.accessToken").doesNotExist())
                .andExpect(jsonPath("$.run.providerResponse").doesNotExist());
    }

    @Test
    void listsRunsWithoutTheirItemsUsingDefaultPaging() throws Exception {
        // 목 생성이 끝난 뒤에 스텁을 건다. when(...) 인자 안에서 다시 목을 만들면 스텁이 꼬인다.
        BrokerHistoryPage<BrokerOrderImportRun> page =
                new BrokerHistoryPage<>(List.of(run()), 0, 20, 1L, false);
        when(brokerOrderImportService.getRuns(eq(10L), eq(20L), any(BrokerHistoryPageRequest.class)))
                .thenReturn(page);

        mockMvc.perform(get(BASE_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(7))
                .andExpect(jsonPath("$.items[0].counts.fetchedCount").value(2))
                .andExpect(jsonPath("$.items[0].executedByMemberId").value(10))
                .andExpect(jsonPath("$.items[0].items").doesNotExist())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.hasNext").value(false));

        ArgumentCaptor<BrokerHistoryPageRequest> pageRequest =
                ArgumentCaptor.forClass(BrokerHistoryPageRequest.class);
        verify(brokerOrderImportService).getRuns(eq(10L), eq(20L), pageRequest.capture());
        assertThat(pageRequest.getValue().page()).isZero();
        assertThat(pageRequest.getValue().size()).isEqualTo(BrokerHistoryPageRequest.DEFAULT_SIZE);
    }

    @Test
    void passesRequestedPageAndSizeThroughAndReportsMorePagesAhead() throws Exception {
        BrokerHistoryPage<BrokerOrderImportRun> page =
                new BrokerHistoryPage<>(List.of(run()), 2, 5, 42L, true);
        when(brokerOrderImportService.getRuns(eq(10L), eq(20L), any(BrokerHistoryPageRequest.class)))
                .thenReturn(page);

        mockMvc.perform(get(BASE_PATH).param("page", "2").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.totalElements").value(42))
                .andExpect(jsonPath("$.hasNext").value(true));

        ArgumentCaptor<BrokerHistoryPageRequest> pageRequest =
                ArgumentCaptor.forClass(BrokerHistoryPageRequest.class);
        verify(brokerOrderImportService).getRuns(eq(10L), eq(20L), pageRequest.capture());
        assertThat(pageRequest.getValue().page()).isEqualTo(2);
        assertThat(pageRequest.getValue().size()).isEqualTo(5);
    }

    /** 상한을 넘는 크기는 조용히 잘라 전체 조회로 되돌리지 않고 잘못된 요청으로 알린다. */
    @Test
    void rejectsARunPageSizeAboveTheServerLimit() throws Exception {
        mockMvc.perform(get(BASE_PATH)
                        .param("size", String.valueOf(BrokerHistoryPageRequest.MAX_SIZE + 1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "페이지 크기는 1 이상 " + BrokerHistoryPageRequest.MAX_SIZE + " 이하여야 합니다."));

        verifyNoInteractions(brokerOrderImportService);
    }

    @Test
    void rejectsANegativeRunPageNumber() throws Exception {
        mockMvc.perform(get(BASE_PATH).param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("페이지 번호는 0 이상이어야 합니다."));

        verifyNoInteractions(brokerOrderImportService);
    }

    @Test
    void readsRunItemsOnePageAtATimeWithTotalCountAndNextPageFlag() throws Exception {
        BrokerHistoryPage<BrokerOrderImportItem> page =
                new BrokerHistoryPage<>(List.of(stagedItem(), pendingItem()), 1, 2, 42L, true);
        when(brokerOrderImportService.getRunItems(
                eq(10L), eq(20L), eq(7L),
                any(BrokerOrderImportItemFilter.class), any(BrokerHistoryPageRequest.class)))
                .thenReturn(page);

        mockMvc.perform(get(BASE_PATH + "/7/items").param("page", "1").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].externalOrderId").value("order-1"))
                .andExpect(jsonPath("$.items[0].ticker").value("AAPL"))
                .andExpect(jsonPath("$.items[0].stagingStatus").value("STAGED"))
                // 제외 사유와 신호는 상세가 아니라 이 정본 조회에서 읽는다.
                .andExpect(jsonPath("$.items[1].skipReasonCode").value("PARTIAL_FILL_PENDING"))
                .andExpect(jsonPath("$.items[1].feeUnknown").value(true))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(42))
                .andExpect(jsonPath("$.hasNext").value(true));

        ArgumentCaptor<BrokerHistoryPageRequest> pageRequest =
                ArgumentCaptor.forClass(BrokerHistoryPageRequest.class);
        verify(brokerOrderImportService).getRunItems(
                eq(10L), eq(20L), eq(7L), any(BrokerOrderImportItemFilter.class), pageRequest.capture());
        assertThat(pageRequest.getValue().page()).isEqualTo(1);
        assertThat(pageRequest.getValue().size()).isEqualTo(2);
    }

    @Test
    void readsRunItemsWithDefaultPagingWhenNoneIsRequested() throws Exception {
        BrokerHistoryPage<BrokerOrderImportItem> page =
                new BrokerHistoryPage<>(List.of(stagedItem()), 0, 20, 1L, false);
        when(brokerOrderImportService.getRunItems(
                eq(10L), eq(20L), eq(7L),
                any(BrokerOrderImportItemFilter.class), any(BrokerHistoryPageRequest.class)))
                .thenReturn(page);

        mockMvc.perform(get(BASE_PATH + "/7/items"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasNext").value(false));

        ArgumentCaptor<BrokerHistoryPageRequest> pageRequest =
                ArgumentCaptor.forClass(BrokerHistoryPageRequest.class);
        verify(brokerOrderImportService).getRunItems(
                eq(10L), eq(20L), eq(7L), any(BrokerOrderImportItemFilter.class), pageRequest.capture());
        assertThat(pageRequest.getValue().page()).isZero();
        assertThat(pageRequest.getValue().size()).isEqualTo(BrokerHistoryPageRequest.DEFAULT_SIZE);
    }

    /** 거르기는 서버로 넘어가야 한다. 전부 내려보내고 화면에서 거르면 페이지를 나눈 뜻이 없다. */
    @Test
    void passesTheStatusAndSymbolFiltersToTheServerSideQuery() throws Exception {
        BrokerHistoryPage<BrokerOrderImportItem> page =
                new BrokerHistoryPage<>(List.of(), 0, 20, 0L, false);
        when(brokerOrderImportService.getRunItems(
                eq(10L), eq(20L), eq(7L),
                any(BrokerOrderImportItemFilter.class), any(BrokerHistoryPageRequest.class)))
                .thenReturn(page);

        mockMvc.perform(get(BASE_PATH + "/7/items").param("status", "staged").param("symbol", "aapl"))
                .andExpect(status().isOk());

        ArgumentCaptor<BrokerOrderImportItemFilter> filter =
                ArgumentCaptor.forClass(BrokerOrderImportItemFilter.class);
        verify(brokerOrderImportService).getRunItems(
                eq(10L), eq(20L), eq(7L), filter.capture(), any(BrokerHistoryPageRequest.class));
        assertThat(filter.getValue().stagingStatus()).isEqualTo(BrokerOrderStagingStatus.STAGED);
        assertThat(filter.getValue().ticker()).isEqualTo("AAPL");
    }

    @Test
    void rejectsAnItemPageSizeAboveTheServerLimit() throws Exception {
        mockMvc.perform(get(BASE_PATH + "/7/items")
                        .param("size", String.valueOf(BrokerHistoryPageRequest.MAX_SIZE + 1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "페이지 크기는 1 이상 " + BrokerHistoryPageRequest.MAX_SIZE + " 이하여야 합니다."));

        verifyNoInteractions(brokerOrderImportService);
    }

    @Test
    void rejectsANegativeItemPageNumber() throws Exception {
        mockMvc.perform(get(BASE_PATH + "/7/items").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("페이지 번호는 0 이상이어야 합니다."));

        verifyNoInteractions(brokerOrderImportService);
    }

    /** 알 수 없는 상태를 조용히 무시하면 호출자는 조건이 걸린 결과를 받았다고 오해한다. */
    @Test
    void rejectsAnUnknownItemStatusFilter() throws Exception {
        mockMvc.perform(get(BASE_PATH + "/7/items").param("status", "NOT_A_STATUS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("알 수 없는 주문 항목 상태입니다."));

        verifyNoInteractions(brokerOrderImportService);
    }

    @Test
    void reportsAMissingRunOnItemsAsNotFound() throws Exception {
        when(brokerOrderImportService.getRunItems(
                eq(10L), eq(20L), eq(7L),
                any(BrokerOrderImportItemFilter.class), any(BrokerHistoryPageRequest.class)))
                .thenThrow(new BrokerOrderImportNotFoundException("주문 이력 가져오기 실행을 찾을 수 없습니다."));

        mockMvc.perform(get(BASE_PATH + "/7/items"))
                .andExpect(status().isNotFound());
    }

    @Test
    void blocksItemAccessToAnotherMembersPortfolio() throws Exception {
        doThrow(new AccessDeniedException("다른 회원의 데이터에 접근할 수 없습니다."))
                .when(memberAccessService).requireMemberAccess(any(), any());

        mockMvc.perform(get(BASE_PATH + "/7/items"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(brokerOrderImportService);
    }

    @Test
    void rejectsARequestWithoutADateRange() throws Exception {
        mockMvc.perform(post(BASE_PATH).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(brokerOrderImportService);
    }

    @Test
    void reportsAMissingRunAsNotFound() throws Exception {
        when(brokerOrderImportService.getRun(10L, 20L, 7L))
                .thenThrow(new BrokerOrderImportNotFoundException("주문 이력 가져오기 실행을 찾을 수 없습니다."));

        mockMvc.perform(get(BASE_PATH + "/7"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("주문 이력 가져오기 실행을 찾을 수 없습니다."));
    }

    /** 조회를 끝까지 하지 못한 경우는 요청 오류가 아니라 이 조건으로는 처리할 수 없다는 뜻이다. */
    @Test
    void reportsAnUnfinishableTraversalAsUnprocessable() throws Exception {
        when(brokerOrderImportService.createPreview(any(), any(), any(), any()))
                .thenThrow(new BrokerOrderImportUnprocessableException(
                        "조회 구간의 주문이 너무 많습니다. 기간을 나눠 다시 시도하세요.", "PAGE_LIMIT_EXCEEDED"));

        mockMvc.perform(post(BASE_PATH).contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("조회 구간의 주문이 너무 많습니다. 기간을 나눠 다시 시도하세요."));
    }

    @Test
    void reportsAProviderFailureAsServiceUnavailable() throws Exception {
        when(brokerOrderImportService.createPreview(any(), any(), any(), any()))
                .thenThrow(new BrokerConnectionUnavailableException("토스증권 주문 이력 조회에 실패했습니다."));

        mockMvc.perform(post(BASE_PATH).contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isServiceUnavailable());
    }

    /** 같은 포트폴리오의 가져오기가 이미 진행 중이면 409로 거부하고, 원인 코드를 함께 준다. */
    @Test
    void reportsAConcurrentImportCallAsConflict() throws Exception {
        when(brokerOrderImportService.createPreview(any(), any(), any(), any()))
                .thenThrow(new com.tradeguide.exception.BrokerCallInProgressException(
                        "같은 포트폴리오의 주문 이력 가져오기 조회가 이미 진행 중입니다. 잠시 후 다시 시도하세요."));

        mockMvc.perform(post(BASE_PATH).contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BROKER_CALL_IN_PROGRESS"));
    }

    /** 직전 호출 이후 쿨다운이 지나지 않았으면 429로 거부하고, 원인 코드를 함께 준다. */
    @Test
    void reportsARepeatedImportCallWithinTheCooldownAsTooManyRequests() throws Exception {
        when(brokerOrderImportService.createPreview(any(), any(), any(), any()))
                .thenThrow(new com.tradeguide.exception.BrokerCallCooldownException("3초 후 다시 시도할 수 있습니다.", 3));

        mockMvc.perform(post(BASE_PATH).contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("BROKER_CALL_COOLDOWN"))
                .andExpect(jsonPath("$.message").value("3초 후 다시 시도할 수 있습니다."));
    }

    @Test
    void blocksAccessToAnotherMembersPortfolio() throws Exception {
        doThrow(new AccessDeniedException("다른 회원의 데이터에 접근할 수 없습니다."))
                .when(memberAccessService).requireMemberAccess(any(), any());

        mockMvc.perform(get(BASE_PATH))
                .andExpect(status().isForbidden());

        verifyNoInteractions(brokerOrderImportService);
    }

    @Test
    void approvesARunAndReturnsCreatedWhenNewRowsAreWritten() throws Exception {
        BrokerOrderImportApprovalWriter.ApprovalResult result = new BrokerOrderImportApprovalWriter.ApprovalResult(
                7L, 2, 1, 0, 2, LocalDateTime.of(2026, 9, 8, 10, 0), 10L,
                LocalDateTime.of(2026, 9, 3, 0, 0), false, 0);
        when(brokerOrderImportApprovalService.approve(10L, 20L, 7L, false)).thenReturn(result);

        mockMvc.perform(post(BASE_PATH + "/7/approval"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.runId").value(7))
                .andExpect(jsonPath("$.eligibleCount").value(2))
                .andExpect(jsonPath("$.baselineExcludedCount").value(1))
                .andExpect(jsonPath("$.writtenCount").value(2))
                .andExpect(jsonPath("$.approvedByMemberId").value(10))
                .andExpect(jsonPath("$.baselineAt").value("2026-09-03T00:00:00"));
    }

    @Test
    void approvesAnAlreadyFullyLinkedRunAndReturnsOkWithoutNewWrites() throws Exception {
        BrokerOrderImportApprovalWriter.ApprovalResult result = new BrokerOrderImportApprovalWriter.ApprovalResult(
                7L, 1, 0, 1, 0, LocalDateTime.of(2026, 9, 8, 10, 0), 10L, null, false, 0);
        when(brokerOrderImportApprovalService.approve(10L, 20L, 7L, false)).thenReturn(result);

        mockMvc.perform(post(BASE_PATH + "/7/approval"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.writtenCount").value(0))
                .andExpect(jsonPath("$.alreadyLinkedCount").value(1));
    }

    /** 확인 파라미터를 붙여 요청하면 서비스로 그대로 전달된다. */
    @Test
    void passesTheAcknowledgementQueryParameterThroughToTheService() throws Exception {
        BrokerOrderImportApprovalWriter.ApprovalResult result = new BrokerOrderImportApprovalWriter.ApprovalResult(
                7L, 1, 0, 0, 1, LocalDateTime.of(2026, 9, 8, 10, 0), 10L, null, true, 0);
        when(brokerOrderImportApprovalService.approve(10L, 20L, 7L, true)).thenReturn(result);

        mockMvc.perform(post(BASE_PATH + "/7/approval").param("acknowledgeIncompleteCoverage", "true"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.coverageAcknowledged").value(true));

        verify(brokerOrderImportApprovalService).approve(10L, 20L, 7L, true);
    }

    /** 확인 파라미터가 없는 요청은 기존과 같이 false로 처리한다 — 하위 호환. */
    @Test
    void defaultsTheAcknowledgementQueryParameterToFalseWhenOmitted() throws Exception {
        BrokerOrderImportApprovalWriter.ApprovalResult result = new BrokerOrderImportApprovalWriter.ApprovalResult(
                7L, 1, 0, 1, 0, LocalDateTime.of(2026, 9, 8, 10, 0), 10L, null, false, 0);
        when(brokerOrderImportApprovalService.approve(10L, 20L, 7L, false)).thenReturn(result);

        mockMvc.perform(post(BASE_PATH + "/7/approval"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coverageAcknowledged").value(false));
    }

    /** 불완전 이력 미확인 승인 시도는 409와 전용 코드로 거부된다. */
    @Test
    void reportsAnIncompleteCoverageConflictAsConflictWithItsOwnCode() throws Exception {
        when(brokerOrderImportApprovalService.approve(10L, 20L, 7L, false))
                .thenThrow(new BrokerOrderApprovalConflictException(
                        "이 실행은 2026-09-03까지만 이력을 가져왔습니다. 불완전한 상태로 반영하려면 확인이 필요합니다.",
                        ApiErrorCode.ORDER_IMPORT_COVERAGE_ACKNOWLEDGEMENT_REQUIRED));

        mockMvc.perform(post(BASE_PATH + "/7/approval"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_IMPORT_COVERAGE_ACKNOWLEDGEMENT_REQUIRED"));
    }

    /** 부분 커버 실행의 상세 응답에 coverage 필드가 그대로 노출된다. */
    @Test
    void exposesPartialCoverageInTheRunDetailResponse() throws Exception {
        BrokerOrderImportRun run = run();
        when(run.getCoveredOrderedTo()).thenReturn(LocalDate.of(2026, 9, 3));
        BrokerOrderImportService.RunDetail detail = runDetail(run);
        when(brokerOrderImportService.getRun(10L, 20L, 7L)).thenReturn(detail);

        mockMvc.perform(get(BASE_PATH + "/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.run.coverage.fullyCovered").value(false))
                .andExpect(jsonPath("$.run.coverage.coveredOrderedTo").value("2026-09-03"))
                .andExpect(jsonPath("$.run.coverage.nextOrderedFrom").value("2026-09-04"));
    }

    /** 요청 구간을 끝까지 커버한 실행은 coverage.fullyCovered가 참이고 나머지 두 필드는 없다. */
    @Test
    void exposesFullCoverageInTheRunDetailResponse() throws Exception {
        BrokerOrderImportService.RunDetail detail = runDetail(run());
        when(brokerOrderImportService.getRun(10L, 20L, 7L)).thenReturn(detail);

        mockMvc.perform(get(BASE_PATH + "/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.run.coverage.fullyCovered").value(true))
                .andExpect(jsonPath("$.run.coverage.coveredOrderedTo").doesNotExist())
                .andExpect(jsonPath("$.run.coverage.nextOrderedFrom").doesNotExist());
    }

    @Test
    void reportsAnApprovalConflictAsConflict() throws Exception {
        when(brokerOrderImportApprovalService.approve(10L, 20L, 7L, false))
                .thenThrow(new BrokerOrderApprovalConflictException(
                        "활성 개시 잔고 기준 시각 이후에 체결된 주문이 없어 반영할 수 없습니다.",
                        ApiErrorCode.BASELINE_EXCLUDED));

        mockMvc.perform(post(BASE_PATH + "/7/approval"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "활성 개시 잔고 기준 시각 이후에 체결된 주문이 없어 반영할 수 없습니다."))
                .andExpect(jsonPath("$.code").value("BASELINE_EXCLUDED"));
    }

    @Test
    void reportsAMissingRunOnApprovalAsNotFound() throws Exception {
        when(brokerOrderImportApprovalService.approve(10L, 20L, 7L, false))
                .thenThrow(new BrokerOrderImportNotFoundException("요청한 주문 이력 가져오기 실행을 찾을 수 없습니다."));

        mockMvc.perform(post(BASE_PATH + "/7/approval"))
                .andExpect(status().isNotFound());
    }

    @Test
    void reportsAMissingPortfolioOnApprovalAsNotFoundWithCode() throws Exception {
        when(brokerOrderImportApprovalService.approve(10L, 20L, 7L, false))
                .thenThrow(new PortfolioNotFoundException("포트폴리오를 찾을 수 없습니다."));

        mockMvc.perform(post(BASE_PATH + "/7/approval"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("포트폴리오를 찾을 수 없습니다."))
                .andExpect(jsonPath("$.code").value("PORTFOLIO_NOT_FOUND"));
    }

    @Test
    void revokesARunApprovalAndReturnsNoContent() throws Exception {
        mockMvc.perform(delete(BASE_PATH + "/7/approval"))
                .andExpect(status().isNoContent());

        org.mockito.Mockito.verify(brokerOrderImportApprovalService).revoke(10L, 20L, 7L);
    }

    @Test
    void reportsARunWithNoActiveApprovalOnRevokeAsNotFound() throws Exception {
        doThrow(new BrokerOrderImportNotFoundException("취소할 수 있는 주문 이력 반영 승인을 찾을 수 없습니다."))
                .when(brokerOrderImportApprovalService).revoke(10L, 20L, 7L);

        mockMvc.perform(delete(BASE_PATH + "/7/approval"))
                .andExpect(status().isNotFound());
    }

    @Test
    void blocksApprovalAccessToAnotherMembersPortfolio() throws Exception {
        doThrow(new AccessDeniedException("다른 회원의 데이터에 접근할 수 없습니다."))
                .when(memberAccessService).requireMemberAccess(any(), any());

        mockMvc.perform(post(BASE_PATH + "/7/approval"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(brokerOrderImportApprovalService);
    }

    private BrokerOrderImportService.RunDetail runDetail(BrokerOrderImportRun run) {
        return new BrokerOrderImportService.RunDetail(
                run,
                BrokerOrderImportApprovalAssessment.of(
                        BrokerOrderImportRunStatus.STAGED,
                        BrokerOrderReconciliationStatus.MISMATCHED,
                        run.getCounts(),
                        1,
                        0,
                        0,
                        0,
                        null,
                        true,
                        false),
                List.of(reconciliationLine())
        );
    }

    private BrokerOrderImportRun run() {
        BrokerConnection connection = mock(BrokerConnection.class);
        when(connection.getId()).thenReturn(1L);
        BrokerAccount account = mock(BrokerAccount.class);
        when(account.getMaskedAccountNumber()).thenReturn("*****1234");

        BrokerOrderImportRun run = mock(BrokerOrderImportRun.class);
        when(run.getId()).thenReturn(7L);
        when(run.getProvider()).thenReturn(BrokerProvider.TOSS_SECURITIES);
        when(run.getBrokerConnection()).thenReturn(connection);
        when(run.getBrokerAccount()).thenReturn(account);
        when(run.getRequestedOrderedFrom()).thenReturn(LocalDate.of(2026, 9, 1));
        when(run.getRequestedOrderedTo()).thenReturn(LocalDate.of(2026, 9, 5));
        when(run.getQueriedOrderedFrom()).thenReturn(LocalDate.of(2026, 8, 30));
        when(run.getQueriedOrderedTo()).thenReturn(LocalDate.of(2026, 9, 7));
        when(run.getStartedAt()).thenReturn(LocalDateTime.of(2026, 9, 8, 9, 0));
        when(run.getFinishedAt()).thenReturn(LocalDateTime.of(2026, 9, 8, 9, 0, 3));
        when(run.getStatus()).thenReturn(BrokerOrderImportRunStatus.STAGED);
        when(run.getCounts()).thenReturn(new BrokerOrderImportCounts(
                2, 1, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 3));
        when(run.getReconciliationStatus()).thenReturn(BrokerOrderReconciliationStatus.MISMATCHED);
        when(run.getReconciliationSnapshotSyncedAt()).thenReturn(LocalDateTime.of(2026, 9, 7, 9, 0));
        when(run.getExecutedByMemberId()).thenReturn(10L);
        return run;
    }

    private BrokerOrderImportItem stagedItem() {
        BrokerOrderImportItem item = mock(BrokerOrderImportItem.class);
        when(item.getId()).thenReturn(101L);
        when(item.getExternalOrderId()).thenReturn("order-1");
        when(item.getMarket()).thenReturn(Market.US);
        when(item.getTicker()).thenReturn("AAPL");
        when(item.getDisplayName()).thenReturn("Apple Inc.");
        when(item.getOrderSide()).thenReturn(BrokerOrderSide.BUY);
        when(item.getProviderStatusCode()).thenReturn("FILLED");
        when(item.getLifecycle()).thenReturn(BrokerOrderLifecycle.TERMINAL_WITH_FILL);
        when(item.getOrderedQuantity()).thenReturn(new BigDecimal("10"));
        when(item.getFilledQuantity()).thenReturn(new BigDecimal("10"));
        when(item.getAverageFilledPrice()).thenReturn(new BigDecimal("100.25"));
        when(item.getCurrencyCode()).thenReturn("USD");
        when(item.getOrderedAt()).thenReturn(Instant.parse("2026-09-01T00:30:00Z"));
        when(item.getFilledAt()).thenReturn(Instant.parse("2026-09-01T13:30:00Z"));
        when(item.getStagingStatus()).thenReturn(BrokerOrderStagingStatus.STAGED);
        return item;
    }

    private BrokerOrderImportItem pendingItem() {
        BrokerOrderImportItem item = mock(BrokerOrderImportItem.class);
        when(item.getId()).thenReturn(102L);
        when(item.getExternalOrderId()).thenReturn("order-2");
        when(item.getMarket()).thenReturn(Market.US);
        when(item.getTicker()).thenReturn("AAPL");
        when(item.getDisplayName()).thenReturn("Apple Inc.");
        when(item.getOrderSide()).thenReturn(BrokerOrderSide.BUY);
        when(item.getProviderStatusCode()).thenReturn("PARTIAL_FILLED");
        when(item.getLifecycle()).thenReturn(BrokerOrderLifecycle.PARTIALLY_FILLED_OPEN);
        when(item.getOrderedQuantity()).thenReturn(new BigDecimal("10"));
        when(item.getFilledQuantity()).thenReturn(new BigDecimal("5"));
        when(item.getCurrencyCode()).thenReturn("USD");
        when(item.getOrderedAt()).thenReturn(Instant.parse("2026-09-01T00:30:00Z"));
        when(item.getStagingStatus()).thenReturn(BrokerOrderStagingStatus.PENDING_SETTLEMENT);
        when(item.getSkipReasonCode()).thenReturn(BrokerOrderSkipReason.PARTIAL_FILL_PENDING);
        when(item.isFeeUnknown()).thenReturn(true);
        return item;
    }

    private BrokerOrderImportReconciliationLine reconciliationLine() {
        BrokerOrderImportReconciliationLine line = mock(BrokerOrderImportReconciliationLine.class);
        when(line.getMarket()).thenReturn(Market.US);
        when(line.getTicker()).thenReturn("AAPL");
        when(line.getReconstructedQuantity()).thenReturn(new BigDecimal("10"));
        when(line.getSnapshotQuantity()).thenReturn(new BigDecimal("12"));
        when(line.getQuantityDifference()).thenReturn(new BigDecimal("-2"));
        return line;
    }
}
