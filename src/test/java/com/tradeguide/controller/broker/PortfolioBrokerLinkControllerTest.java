package com.tradeguide.controller.broker;

import com.tradeguide.domain.broker.BrokerAccount;
import com.tradeguide.domain.broker.BrokerConnection;
import com.tradeguide.domain.broker.BrokerConnectionSecretValue;
import com.tradeguide.domain.broker.BrokerHistoryPage;
import com.tradeguide.domain.broker.BrokerHistoryPageRequest;
import com.tradeguide.domain.broker.BrokerHolding;
import com.tradeguide.domain.broker.BrokerHoldingComparison;
import com.tradeguide.domain.broker.BrokerHoldingPreview;
import com.tradeguide.domain.broker.BrokerHoldingPreviewItem;
import com.tradeguide.domain.broker.BrokerOpeningBalanceBatchResult;
import com.tradeguide.domain.broker.BrokerOpeningBalanceSkip;
import com.tradeguide.domain.broker.BrokerOpeningBalanceSkipReason;
import com.tradeguide.domain.broker.BrokerProvider;
import com.tradeguide.domain.broker.PortfolioBrokerHoldingAdjustment;
import com.tradeguide.domain.broker.PortfolioBrokerHoldingAdjustmentStatus;
import com.tradeguide.domain.broker.PortfolioBrokerHoldingImport;
import com.tradeguide.domain.broker.PortfolioBrokerHoldingImportStatus;
import com.tradeguide.domain.broker.PortfolioBrokerHoldingSnapshot;
import com.tradeguide.domain.broker.PortfolioBrokerHoldingSnapshotItem;
import com.tradeguide.domain.member.Member;
import com.tradeguide.domain.portfolio.Portfolio;
import com.tradeguide.domain.portfolio.PortfolioBrokerLink;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.dto.ApiErrorCode;
import com.tradeguide.exception.BrokerConnectionReverificationRequiredException;
import com.tradeguide.exception.BrokerConnectionUnavailableException;
import com.tradeguide.exception.BrokerHoldingAdjustmentConflictException;
import com.tradeguide.exception.BrokerHoldingAdjustmentUnprocessableException;
import com.tradeguide.exception.BrokerHoldingImportConflictException;
import com.tradeguide.exception.BrokerHoldingImportUnprocessableException;
import com.tradeguide.exception.BrokerHoldingSnapshotItemNotFoundException;
import com.tradeguide.exception.BrokerHoldingSnapshotNotFoundException;
import com.tradeguide.exception.PortfolioBrokerHoldingAdjustmentNotFoundException;
import com.tradeguide.exception.PortfolioBrokerHoldingImportNotFoundException;
import com.tradeguide.exception.PortfolioBrokerLinkNotFoundException;
import com.tradeguide.exception.PortfolioNotFoundException;
import com.tradeguide.service.auth.MemberAccessService;
import com.tradeguide.service.broker.BrokerHoldingPreviewService;
import com.tradeguide.service.broker.PortfolioBrokerHoldingAdjustmentService;
import com.tradeguide.service.broker.PortfolioBrokerHoldingImportService;
import com.tradeguide.service.broker.PortfolioBrokerHoldingSnapshotService;
import com.tradeguide.service.broker.PortfolioBrokerLinkService;
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
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
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
    private PortfolioBrokerHoldingSnapshotService portfolioBrokerHoldingSnapshotService;

    @MockitoBean
    private PortfolioBrokerHoldingImportService portfolioBrokerHoldingImportService;

    @MockitoBean
    private PortfolioBrokerHoldingAdjustmentService portfolioBrokerHoldingAdjustmentService;

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

    /** 같은 포트폴리오의 미리보기가 이미 진행 중이면 409로 거부하고, 원인 코드를 함께 준다. */
    @Test
    void returnsConflictWhenAConcurrentPreviewCallIsAlreadyInProgress() throws Exception {
        when(brokerHoldingPreviewService.getHoldingPreview(10L, 20L))
                .thenThrow(new com.tradeguide.exception.BrokerCallInProgressException(
                        "같은 포트폴리오의 보유 종목 미리보기 조회가 이미 진행 중입니다. 잠시 후 다시 시도하세요."));

        mockMvc.perform(post("/api/members/10/portfolios/20/broker-sync-preview"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BROKER_CALL_IN_PROGRESS"));
    }

    /** 직전 호출 이후 쿨다운이 지나지 않았으면 429로 거부하고, 원인 코드를 함께 준다. */
    @Test
    void returnsTooManyRequestsWhenPreviewIsCalledWithinTheCooldownWindow() throws Exception {
        when(brokerHoldingPreviewService.getHoldingPreview(10L, 20L))
                .thenThrow(new com.tradeguide.exception.BrokerCallCooldownException("3초 후 다시 시도할 수 있습니다.", 3));

        mockMvc.perform(post("/api/members/10/portfolios/20/broker-sync-preview"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("BROKER_CALL_COOLDOWN"))
                .andExpect(jsonPath("$.message").value("3초 후 다시 시도할 수 있습니다."));
    }

    @Test
    void refreshesBrokerHoldingSnapshotThroughAdapter() throws Exception {
        when(portfolioBrokerHoldingSnapshotService.refreshSnapshot(10L, 20L)).thenReturn(snapshot());

        mockMvc.perform(post("/api/members/10/portfolios/20/broker-holding-snapshots"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("TOSS_SECURITIES"))
                .andExpect(jsonPath("$.brokerConnectionId").value(1))
                .andExpect(jsonPath("$.maskedAccountNumber").value("*****1234"))
                .andExpect(jsonPath("$.syncedAt").value("2026-09-04T09:30:00"))
                .andExpect(jsonPath("$.unsupportedMarketCount").value(1))
                .andExpect(jsonPath("$.items[0].ticker").value("SOXL"))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.accountSequence").doesNotExist());

        verify(portfolioBrokerHoldingSnapshotService).refreshSnapshot(10L, 20L);
    }

    @Test
    void returnsServiceUnavailableWhenRefreshHasNoSupportedProvider() throws Exception {
        when(portfolioBrokerHoldingSnapshotService.refreshSnapshot(10L, 20L))
                .thenThrow(new BrokerConnectionUnavailableException("해당 증권사의 보유 종목 조회를 아직 지원하지 않습니다."));

        mockMvc.perform(post("/api/members/10/portfolios/20/broker-holding-snapshots"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("해당 증권사의 보유 종목 조회를 아직 지원하지 않습니다."));
    }

    /** 같은 포트폴리오의 스냅샷 갱신이 이미 진행 중이면 409로 거부하고, 원인 코드를 함께 준다. */
    @Test
    void returnsConflictWhenAConcurrentRefreshCallIsAlreadyInProgress() throws Exception {
        when(portfolioBrokerHoldingSnapshotService.refreshSnapshot(10L, 20L))
                .thenThrow(new com.tradeguide.exception.BrokerCallInProgressException(
                        "같은 포트폴리오의 보유 종목 스냅샷 갱신 조회가 이미 진행 중입니다. 잠시 후 다시 시도하세요."));

        mockMvc.perform(post("/api/members/10/portfolios/20/broker-holding-snapshots"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BROKER_CALL_IN_PROGRESS"));
    }

    /** 직전 호출 이후 쿨다운이 지나지 않았으면 429로 거부하고, 원인 코드를 함께 준다. */
    @Test
    void returnsTooManyRequestsWhenRefreshIsCalledWithinTheCooldownWindow() throws Exception {
        when(portfolioBrokerHoldingSnapshotService.refreshSnapshot(10L, 20L))
                .thenThrow(new com.tradeguide.exception.BrokerCallCooldownException("3초 후 다시 시도할 수 있습니다.", 3));

        mockMvc.perform(post("/api/members/10/portfolios/20/broker-holding-snapshots"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("BROKER_CALL_COOLDOWN"))
                .andExpect(jsonPath("$.message").value("3초 후 다시 시도할 수 있습니다."));
    }

    /**
     * 증권사 호출을 트랜잭션 밖으로 빼면서 조회 시점과 저장 시점 사이가 벌어졌다. 그 사이에
     * 링크가 바뀌면 저장이 거부되는데, 이 경로가 <b>기존 400 계약 안에</b> 머무는지 고정한다.
     * 새 상태 코드가 생기면 프론트엔드가 처리하지 못한다.
     */
    @Test
    void returnsBadRequestWhenTheLinkedAccountChangedWhileTheBrokerCallWasInFlight() throws Exception {
        when(portfolioBrokerHoldingSnapshotService.refreshSnapshot(10L, 20L))
                .thenThrow(new IllegalArgumentException(
                        "조회하는 동안 포트폴리오의 증권사 계좌 연결이 바뀌었습니다. 다시 시도해 주세요."));

        mockMvc.perform(post("/api/members/10/portfolios/20/broker-holding-snapshots"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("조회하는 동안 포트폴리오의 증권사 계좌 연결이 바뀌었습니다. 다시 시도해 주세요."))
                .andExpect(jsonPath("$.accountSequence").doesNotExist());
    }

    /** 포트폴리오 부재는 요청 형식 오류(400)가 아니라 리소스 부재(404)다. */
    @Test
    void returnsNotFoundWithCodeWhenThePortfolioDoesNotExistDuringRefresh() throws Exception {
        when(portfolioBrokerHoldingSnapshotService.refreshSnapshot(10L, 20L))
                .thenThrow(new PortfolioNotFoundException("포트폴리오를 찾을 수 없습니다."));

        mockMvc.perform(post("/api/members/10/portfolios/20/broker-holding-snapshots"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("포트폴리오를 찾을 수 없습니다."))
                .andExpect(jsonPath("$.code").value("PORTFOLIO_NOT_FOUND"));
    }

    /** 링크 부재도 요청 형식 오류가 아니라 리소스 부재(404)다. */
    @Test
    void returnsNotFoundWithCodeWhenThePortfolioHasNoBrokerLinkDuringRefresh() throws Exception {
        when(portfolioBrokerHoldingSnapshotService.refreshSnapshot(10L, 20L))
                .thenThrow(new PortfolioBrokerLinkNotFoundException("포트폴리오에 연결된 증권사 계좌가 없습니다."));

        mockMvc.perform(post("/api/members/10/portfolios/20/broker-holding-snapshots"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("포트폴리오에 연결된 증권사 계좌가 없습니다."))
                .andExpect(jsonPath("$.code").value("PORTFOLIO_BROKER_LINK_NOT_FOUND"));
    }

    /** 재검증이 필요한 상태는 재시도로 풀릴 수 있는 충돌(409)이지, 요청 형식 오류(400)가 아니다. */
    @Test
    void returnsConflictWithCodeWhenTheConnectionNeedsReverificationDuringRefresh() throws Exception {
        when(portfolioBrokerHoldingSnapshotService.refreshSnapshot(10L, 20L))
                .thenThrow(new BrokerConnectionReverificationRequiredException("증권사 연결을 다시 검증해야 합니다."));

        mockMvc.perform(post("/api/members/10/portfolios/20/broker-holding-snapshots"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("증권사 연결을 다시 검증해야 합니다."))
                .andExpect(jsonPath("$.code").value("BROKER_CONNECTION_REVERIFICATION_REQUIRED"));
    }

    /** 증권사 호출이 실패하면 스냅샷 응답 대신 503이 나가고, 응답에 자격 증명은 담기지 않는다. */
    @Test
    void returnsServiceUnavailableWhenTheBrokerCallFailsDuringRefresh() throws Exception {
        when(portfolioBrokerHoldingSnapshotService.refreshSnapshot(10L, 20L))
                .thenThrow(new BrokerConnectionUnavailableException("증권사 응답을 받지 못했습니다."));

        mockMvc.perform(post("/api/members/10/portfolios/20/broker-holding-snapshots"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("증권사 응답을 받지 못했습니다."))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.accountSequence").doesNotExist());
    }

    @Test
    void returnsLatestBrokerHoldingSnapshotWithoutExternalCall() throws Exception {
        when(portfolioBrokerHoldingSnapshotService.getLatestSnapshot(10L, 20L)).thenReturn(snapshot());

        mockMvc.perform(get("/api/members/10/portfolios/20/broker-holding-snapshots/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].ticker").value("SOXL"))
                .andExpect(jsonPath("$.unsupportedMarketCount").value(1));

        verify(portfolioBrokerHoldingSnapshotService).getLatestSnapshot(10L, 20L);
        verifyNoInteractions(brokerHoldingPreviewService);
    }

    @Test
    void returnsNotFoundWhenNoBrokerHoldingSnapshotIsSavedYet() throws Exception {
        when(portfolioBrokerHoldingSnapshotService.getLatestSnapshot(10L, 20L))
                .thenThrow(new BrokerHoldingSnapshotNotFoundException("저장된 증권사 보유 종목 스냅샷이 없습니다."));

        mockMvc.perform(get("/api/members/10/portfolios/20/broker-holding-snapshots/latest"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("저장된 증권사 보유 종목 스냅샷이 없습니다."));
    }

    @Test
    void returnsLatestBrokerHoldingSnapshotComparisonWithoutExternalCall() throws Exception {
        when(portfolioBrokerHoldingSnapshotService.getLatestSnapshotComparison(10L, 20L)).thenReturn(
                new BrokerHoldingPreview(
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
                )
        );

        mockMvc.perform(get("/api/members/10/portfolios/20/broker-holding-snapshots/latest/comparison"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("TOSS_SECURITIES"))
                .andExpect(jsonPath("$.maskedAccountNumber").value("*****1234"))
                .andExpect(jsonPath("$.syncedAt").value("2026-09-04T09:30:00"))
                .andExpect(jsonPath("$.unsupportedMarketCount").value(1))
                .andExpect(jsonPath("$.items[0].ticker").value("SOXL"))
                .andExpect(jsonPath("$.items[0].comparison").value("QUANTITY_MISMATCH"))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.accountSequence").doesNotExist());

        verify(portfolioBrokerHoldingSnapshotService).getLatestSnapshotComparison(10L, 20L);
        verifyNoInteractions(brokerHoldingPreviewService);
    }

    @Test
    void returnsNotFoundWhenNoBrokerHoldingSnapshotIsSavedYetForComparison() throws Exception {
        when(portfolioBrokerHoldingSnapshotService.getLatestSnapshotComparison(10L, 20L))
                .thenThrow(new BrokerHoldingSnapshotNotFoundException("저장된 증권사 보유 종목 스냅샷이 없습니다."));

        mockMvc.perform(get("/api/members/10/portfolios/20/broker-holding-snapshots/latest/comparison"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("저장된 증권사 보유 종목 스냅샷이 없습니다."));
    }

    @Test
    void approvesOpeningBalanceAndReturnsCreated() throws Exception {
        PortfolioBrokerHoldingImport importRecord = importRecord();
        when(portfolioBrokerHoldingImportService.approveOpeningBalance(10L, 20L, 55L))
                .thenReturn(new PortfolioBrokerHoldingImportService.ApprovalResult(importRecord, true));

        mockMvc.perform(post("/api/members/10/portfolios/20/broker-holding-imports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"snapshotItemId": 55}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.snapshotItemId").value(55))
                .andExpect(jsonPath("$.ticker").value("SOXL"))
                .andExpect(jsonPath("$.tradeTransactionId").value(900))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        verify(portfolioBrokerHoldingImportService).approveOpeningBalance(10L, 20L, 55L);
    }

    @Test
    void returnsOkWhenOpeningBalanceApprovalIsRetried() throws Exception {
        PortfolioBrokerHoldingImport importRecord = importRecord();
        when(portfolioBrokerHoldingImportService.approveOpeningBalance(10L, 20L, 55L))
                .thenReturn(new PortfolioBrokerHoldingImportService.ApprovalResult(importRecord, false));

        mockMvc.perform(post("/api/members/10/portfolios/20/broker-holding-imports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"snapshotItemId": 55}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snapshotItemId").value(55));
    }

    @Test
    void returnsConflictWhenOpeningBalanceComparisonStatusDisallowsImport() throws Exception {
        when(portfolioBrokerHoldingImportService.approveOpeningBalance(10L, 20L, 55L))
                .thenThrow(new BrokerHoldingImportConflictException(
                        "증권사에만 있는 종목만 개시 잔고로 반영할 수 있습니다: MATCHED"
                ));

        mockMvc.perform(post("/api/members/10/portfolios/20/broker-holding-imports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"snapshotItemId": 55}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void returnsNotFoundWhenOpeningBalanceSnapshotItemIsStaleOrForeign() throws Exception {
        when(portfolioBrokerHoldingImportService.approveOpeningBalance(10L, 20L, 55L))
                .thenThrow(new BrokerHoldingSnapshotItemNotFoundException(
                        "최신 스냅샷에서 해당 증권사 보유 종목을 찾을 수 없습니다."
                ));

        mockMvc.perform(post("/api/members/10/portfolios/20/broker-holding-imports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"snapshotItemId": 55}
                                """))
                .andExpect(status().isNotFound());
    }

    /**
     * D1 계약. 원장이 표현할 수 없는 시장의 단건 승인은 422이며, 화면이 메시지 문구를
     * 파싱하지 않도록 응답에 오류 코드를 함께 담는다.
     */
    @Test
    void returnsUnprocessableEntityWithErrorCodeWhenOpeningBalanceMarketIsNotLedgerWritable() throws Exception {
        when(portfolioBrokerHoldingImportService.approveOpeningBalance(10L, 20L, 66L))
                .thenThrow(new BrokerHoldingImportUnprocessableException(
                        "현재 매매 원장에 반영할 수 있는 시장이 아닙니다: KR",
                        ApiErrorCode.BROKER_LEDGER_MARKET_UNSUPPORTED
                ));

        mockMvc.perform(post("/api/members/10/portfolios/20/broker-holding-imports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"snapshotItemId": 66}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("BROKER_LEDGER_MARKET_UNSUPPORTED"));
    }

    /** 원장에 쓸 수 있는 US 종목의 단건 승인 계약은 그대로다. 오류 코드가 붙지 않는다. */
    @Test
    void createsOpeningBalanceForLedgerWritableUsHolding() throws Exception {
        PortfolioBrokerHoldingImport importRecord = importRecord();
        when(portfolioBrokerHoldingImportService.approveOpeningBalance(10L, 20L, 55L))
                .thenReturn(new PortfolioBrokerHoldingImportService.ApprovalResult(importRecord, true));

        mockMvc.perform(post("/api/members/10/portfolios/20/broker-holding-imports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"snapshotItemId": 55}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.market").value("US"))
                .andExpect(jsonPath("$.code").doesNotExist());
    }

    @Test
    void returnsUnprocessableEntityWhenOpeningBalanceAssetIsNotActive() throws Exception {
        when(portfolioBrokerHoldingImportService.approveOpeningBalance(10L, 20L, 55L))
                .thenThrow(new BrokerHoldingImportUnprocessableException(
                        "비활성 상장 종목은 거래를 등록할 수 없습니다: US / SOXL"
                ));

        mockMvc.perform(post("/api/members/10/portfolios/20/broker-holding-imports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"snapshotItemId": 55}
                                """))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void listsOpeningBalanceImportHistoryOnePageAtATime() throws Exception {
        PortfolioBrokerHoldingImport importRecord = importRecord();
        when(portfolioBrokerHoldingImportService.getImportHistory(
                eq(10L), eq(20L), any(BrokerHistoryPageRequest.class)))
                .thenReturn(new BrokerHistoryPage<>(List.of(importRecord), 0, 20, 1L, false));

        mockMvc.perform(get("/api/members/10/portfolios/20/broker-holding-imports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].ticker").value("SOXL"))
                .andExpect(jsonPath("$.items[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.hasNext").value(false));

        ArgumentCaptor<BrokerHistoryPageRequest> pageRequest =
                ArgumentCaptor.forClass(BrokerHistoryPageRequest.class);
        verify(portfolioBrokerHoldingImportService)
                .getImportHistory(eq(10L), eq(20L), pageRequest.capture());
        assertThat(pageRequest.getValue().page()).isZero();
        assertThat(pageRequest.getValue().size()).isEqualTo(BrokerHistoryPageRequest.DEFAULT_SIZE);
    }

    @Test
    void passesRequestedImportHistoryPageAndSizeThroughAndReportsMorePagesAhead() throws Exception {
        // 목 생성이 끝난 뒤에 스텁을 건다. when(...) 인자 안에서 다시 목을 만들면 스텁이 꼬인다.
        BrokerHistoryPage<PortfolioBrokerHoldingImport> page =
                new BrokerHistoryPage<>(List.of(importRecord()), 1, 3, 10L, true);
        when(portfolioBrokerHoldingImportService.getImportHistory(
                eq(10L), eq(20L), any(BrokerHistoryPageRequest.class)))
                .thenReturn(page);

        mockMvc.perform(get("/api/members/10/portfolios/20/broker-holding-imports")
                        .param("page", "1")
                        .param("size", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(3))
                .andExpect(jsonPath("$.totalElements").value(10))
                .andExpect(jsonPath("$.hasNext").value(true));

        ArgumentCaptor<BrokerHistoryPageRequest> pageRequest =
                ArgumentCaptor.forClass(BrokerHistoryPageRequest.class);
        verify(portfolioBrokerHoldingImportService)
                .getImportHistory(eq(10L), eq(20L), pageRequest.capture());
        assertThat(pageRequest.getValue().page()).isEqualTo(1);
        assertThat(pageRequest.getValue().size()).isEqualTo(3);
    }

    /** 상한을 넘는 크기는 조용히 잘라 전체 조회로 되돌리지 않고 잘못된 요청으로 알린다. */
    @Test
    void rejectsAnImportHistoryPageSizeAboveTheServerLimit() throws Exception {
        mockMvc.perform(get("/api/members/10/portfolios/20/broker-holding-imports")
                        .param("size", String.valueOf(BrokerHistoryPageRequest.MAX_SIZE + 1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "페이지 크기는 1 이상 " + BrokerHistoryPageRequest.MAX_SIZE + " 이하여야 합니다."));

        verifyNoInteractions(portfolioBrokerHoldingImportService);
    }

    @Test
    void rejectsANegativeImportHistoryPageNumber() throws Exception {
        mockMvc.perform(get("/api/members/10/portfolios/20/broker-holding-imports")
                        .param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("페이지 번호는 0 이상이어야 합니다."));

        verifyNoInteractions(portfolioBrokerHoldingImportService);
    }

    @Test
    void approvesOpeningBalanceBatchAndReturnsCreatedWithPerItemReasons() throws Exception {
        BrokerOpeningBalanceBatchResult result = new BrokerOpeningBalanceBatchResult(
                77L,
                LocalDateTime.of(2026, 9, 4, 9, 30),
                List.of(importRecord()),
                List.of(
                        new BrokerOpeningBalanceSkip(56L, Market.US, "AAPL", "Apple Inc.",
                                BrokerOpeningBalanceSkipReason.PREVIOUSLY_REVOKED),
                        new BrokerOpeningBalanceSkip(57L, Market.KR, "005930", "삼성전자",
                                BrokerOpeningBalanceSkipReason.UNSUPPORTED_MARKET)
                )
        );
        when(portfolioBrokerHoldingImportService.approveOpeningBalanceBatch(10L, 20L, 77L))
                .thenReturn(result);

        mockMvc.perform(post("/api/members/10/portfolios/20/broker-holding-imports/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"snapshotId": 77}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.snapshotId").value(77))
                .andExpect(jsonPath("$.snapshotSyncedAt").value("2026-09-04T09:30:00"))
                .andExpect(jsonPath("$.approvedCount").value(1))
                .andExpect(jsonPath("$.skippedCount").value(2))
                .andExpect(jsonPath("$.approved[0].ticker").value("SOXL"))
                .andExpect(jsonPath("$.approved[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.skipped[0].ticker").value("AAPL"))
                .andExpect(jsonPath("$.skipped[0].reason").value("PREVIOUSLY_REVOKED"))
                .andExpect(jsonPath("$.skipped[1].reason").value("UNSUPPORTED_MARKET"))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.accountSequence").doesNotExist());

        verify(portfolioBrokerHoldingImportService).approveOpeningBalanceBatch(10L, 20L, 77L);
    }

    /** 반영할 것이 하나도 없는 것은 실패가 아니다. 사유만 담아 200으로 돌려준다. */
    @Test
    void returnsOkWhenEveryOpeningBalanceCandidateWasSkipped() throws Exception {
        when(portfolioBrokerHoldingImportService.approveOpeningBalanceBatch(10L, 20L, 77L))
                .thenReturn(new BrokerOpeningBalanceBatchResult(
                        77L,
                        LocalDateTime.of(2026, 9, 4, 9, 30),
                        List.of(),
                        List.of(new BrokerOpeningBalanceSkip(56L, Market.US, "AAPL", "Apple Inc.",
                                BrokerOpeningBalanceSkipReason.LEDGER_CONFLICT))
                ));

        mockMvc.perform(post("/api/members/10/portfolios/20/broker-holding-imports/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"snapshotId": 77}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approvedCount").value(0))
                .andExpect(jsonPath("$.skipped[0].reason").value("LEDGER_CONFLICT"));
    }

    @Test
    void rejectsBatchOpeningBalanceRequestWithoutASnapshotId() throws Exception {
        mockMvc.perform(post("/api/members/10/portfolios/20/broker-holding-imports/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("스냅샷 ID는 필수입니다."));

        verifyNoInteractions(portfolioBrokerHoldingImportService);
    }

    @Test
    void returnsConflictWhenBatchOpeningBalanceReviewedAStaleSnapshot() throws Exception {
        when(portfolioBrokerHoldingImportService.approveOpeningBalanceBatch(10L, 20L, 77L))
                .thenThrow(new BrokerHoldingImportConflictException(
                        "검토한 스냅샷이 최신이 아닙니다. 스냅샷을 다시 조회한 뒤 반영하세요."));

        mockMvc.perform(post("/api/members/10/portfolios/20/broker-holding-imports/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"snapshotId": 77}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "검토한 스냅샷이 최신이 아닙니다. 스냅샷을 다시 조회한 뒤 반영하세요."));
    }

    @Test
    void returnsNotFoundWhenBatchOpeningBalanceHasNoSavedSnapshot() throws Exception {
        when(portfolioBrokerHoldingImportService.approveOpeningBalanceBatch(10L, 20L, 77L))
                .thenThrow(new BrokerHoldingSnapshotNotFoundException("저장된 증권사 보유 종목 스냅샷이 없습니다."));

        mockMvc.perform(post("/api/members/10/portfolios/20/broker-holding-imports/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"snapshotId": 77}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void deniesBatchOpeningBalanceApprovalForAnotherMembersPortfolio() throws Exception {
        doThrow(new AccessDeniedException("다른 회원의 데이터에 접근할 수 없습니다."))
                .when(memberAccessService).requireMemberAccess(any(), any());

        mockMvc.perform(post("/api/members/99/portfolios/20/broker-holding-imports/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"snapshotId": 77}
                                """))
                .andExpect(status().isForbidden());

        verifyNoInteractions(portfolioBrokerHoldingImportService);
    }

    @Test
    void listsPreservedImportHistoryWhenSnapshotItemIsDetached() throws Exception {
        PortfolioBrokerHoldingImport detached = detachedRevokedImportRecord();
        when(portfolioBrokerHoldingImportService.getImportHistory(
                eq(10L), eq(20L), any(BrokerHistoryPageRequest.class)))
                .thenReturn(new BrokerHistoryPage<>(List.of(detached), 0, 20, 1L, false));

        mockMvc.perform(get("/api/members/10/portfolios/20/broker-holding-imports"))
                .andExpect(status().isOk())
                // 증권사 연결이 삭제되어 원본 스냅샷 항목이 사라진 취소 이력이다.
                // 참조만 널이고 승인 시점 값과 상태는 그대로 응답해야 한다.
                .andExpect(jsonPath("$.items[0].snapshotItemId").value(nullValue()))
                .andExpect(jsonPath("$.items[0].id").value(900))
                .andExpect(jsonPath("$.items[0].market").value("US"))
                .andExpect(jsonPath("$.items[0].ticker").value("SOXL"))
                .andExpect(jsonPath("$.items[0].displayName").value("Direxion Daily Semiconductor Bull 3X"))
                .andExpect(jsonPath("$.items[0].quantity").value(30))
                .andExpect(jsonPath("$.items[0].averagePurchasePrice").value(20.00))
                .andExpect(jsonPath("$.items[0].snapshotSyncedAt").value("2026-09-04T09:30:00"))
                .andExpect(jsonPath("$.items[0].tradeTransactionId").value(900))
                .andExpect(jsonPath("$.items[0].approvedByMemberId").value(10))
                .andExpect(jsonPath("$.items[0].approvedAt").value("2026-09-06T10:00:00"))
                .andExpect(jsonPath("$.items[0].status").value("REVOKED"));
    }

    @Test
    void listsImportHistoryMixingDetachedAndActiveRecords() throws Exception {
        PortfolioBrokerHoldingImport detached = detachedRevokedImportRecord();
        PortfolioBrokerHoldingImport active = importRecord();
        when(portfolioBrokerHoldingImportService.getImportHistory(
                eq(10L), eq(20L), any(BrokerHistoryPageRequest.class)))
                .thenReturn(new BrokerHistoryPage<>(List.of(detached, active), 0, 20, 2L, false));

        mockMvc.perform(get("/api/members/10/portfolios/20/broker-holding-imports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].snapshotItemId").value(nullValue()))
                .andExpect(jsonPath("$.items[0].status").value("REVOKED"))
                .andExpect(jsonPath("$.items[1].snapshotItemId").value(55))
                .andExpect(jsonPath("$.items[1].status").value("ACTIVE"));
    }

    @Test
    void revokesOpeningBalanceImport() throws Exception {
        mockMvc.perform(delete("/api/members/10/portfolios/20/broker-holding-imports/900"))
                .andExpect(status().isNoContent());

        verify(portfolioBrokerHoldingImportService).revokeOpeningBalance(10L, 20L, 900L);
    }

    @Test
    void returnsNotFoundWhenRevokingUnknownOpeningBalanceImport() throws Exception {
        doThrow(new PortfolioBrokerHoldingImportNotFoundException("취소할 수 있는 개시 잔고 승인 이력을 찾을 수 없습니다."))
                .when(portfolioBrokerHoldingImportService).revokeOpeningBalance(10L, 20L, 900L);

        mockMvc.perform(delete("/api/members/10/portfolios/20/broker-holding-imports/900"))
                .andExpect(status().isNotFound());
    }

    @Test
    void approvesHoldingAdjustmentAndReturnsCreated() throws Exception {
        PortfolioBrokerHoldingAdjustment adjustment = adjustmentRecord();
        when(portfolioBrokerHoldingAdjustmentService.approveAdjustment(10L, 20L, 55L))
                .thenReturn(new PortfolioBrokerHoldingAdjustmentService.ApprovalResult(adjustment, true));

        mockMvc.perform(post("/api/members/10/portfolios/20/broker-holding-adjustments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"snapshotItemId": 55}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.snapshotItemId").value(55))
                .andExpect(jsonPath("$.ticker").value("SOXL"))
                .andExpect(jsonPath("$.deltaQuantity").value(5))
                .andExpect(jsonPath("$.unitPrice").value(160.00))
                .andExpect(jsonPath("$.tradeTransactionId").value(900))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        verify(portfolioBrokerHoldingAdjustmentService).approveAdjustment(10L, 20L, 55L);
    }

    @Test
    void returnsOkWhenHoldingAdjustmentApprovalIsRetried() throws Exception {
        PortfolioBrokerHoldingAdjustment adjustment = adjustmentRecord();
        when(portfolioBrokerHoldingAdjustmentService.approveAdjustment(10L, 20L, 55L))
                .thenReturn(new PortfolioBrokerHoldingAdjustmentService.ApprovalResult(adjustment, false));

        mockMvc.perform(post("/api/members/10/portfolios/20/broker-holding-adjustments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"snapshotItemId": 55}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snapshotItemId").value(55));
    }

    @Test
    void returnsConflictWhenHoldingAdjustmentComparisonStatusDisallowsIt() throws Exception {
        when(portfolioBrokerHoldingAdjustmentService.approveAdjustment(10L, 20L, 55L))
                .thenThrow(new BrokerHoldingAdjustmentConflictException(
                        "수량이 불일치하는 종목만 잔고 조정으로 반영할 수 있습니다: ONLY_IN_BROKER"
                ));

        mockMvc.perform(post("/api/members/10/portfolios/20/broker-holding-adjustments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"snapshotItemId": 55}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void returnsUnprocessableEntityWhenHoldingAdjustmentDeltaIsNotPositive() throws Exception {
        when(portfolioBrokerHoldingAdjustmentService.approveAdjustment(10L, 20L, 55L))
                .thenThrow(new BrokerHoldingAdjustmentUnprocessableException(
                        "증권사 수량이 Trade Guide 보유 수량보다 많은 경우만 잔고 조정을 반영할 수 있습니다."
                ));

        mockMvc.perform(post("/api/members/10/portfolios/20/broker-holding-adjustments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"snapshotItemId": 55}
                                """))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void returnsNotFoundWhenHoldingAdjustmentSnapshotItemIsStaleOrForeign() throws Exception {
        when(portfolioBrokerHoldingAdjustmentService.approveAdjustment(10L, 20L, 55L))
                .thenThrow(new BrokerHoldingSnapshotItemNotFoundException(
                        "최신 스냅샷에서 해당 증권사 보유 종목을 찾을 수 없습니다."
                ));

        mockMvc.perform(post("/api/members/10/portfolios/20/broker-holding-adjustments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"snapshotItemId": 55}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void deniesHoldingAdjustmentApprovalForAnotherMembersPortfolio() throws Exception {
        doThrow(new AccessDeniedException("다른 회원의 데이터에 접근할 수 없습니다."))
                .when(memberAccessService).requireMemberAccess(any(), any());

        mockMvc.perform(post("/api/members/99/portfolios/20/broker-holding-adjustments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"snapshotItemId": 55}
                                """))
                .andExpect(status().isForbidden());

        verifyNoInteractions(portfolioBrokerHoldingAdjustmentService);
    }

    @Test
    void listsHoldingAdjustmentHistoryOnePageAtATime() throws Exception {
        PortfolioBrokerHoldingAdjustment adjustment = adjustmentRecord();
        when(portfolioBrokerHoldingAdjustmentService.getAdjustmentHistory(
                eq(10L), eq(20L), any(BrokerHistoryPageRequest.class)))
                .thenReturn(new BrokerHistoryPage<>(List.of(adjustment), 0, 20, 1L, false));

        mockMvc.perform(get("/api/members/10/portfolios/20/broker-holding-adjustments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].ticker").value("SOXL"))
                .andExpect(jsonPath("$.items[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void revokesHoldingAdjustment() throws Exception {
        mockMvc.perform(delete("/api/members/10/portfolios/20/broker-holding-adjustments/900"))
                .andExpect(status().isNoContent());

        verify(portfolioBrokerHoldingAdjustmentService).revokeAdjustment(10L, 20L, 900L);
    }

    @Test
    void returnsNotFoundWhenRevokingUnknownHoldingAdjustment() throws Exception {
        doThrow(new PortfolioBrokerHoldingAdjustmentNotFoundException("취소할 수 있는 잔고 조정 승인 이력을 찾을 수 없습니다."))
                .when(portfolioBrokerHoldingAdjustmentService).revokeAdjustment(10L, 20L, 900L);

        mockMvc.perform(delete("/api/members/10/portfolios/20/broker-holding-adjustments/900"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deniesHoldingAdjustmentRevokeForAnotherMembersPortfolio() throws Exception {
        doThrow(new AccessDeniedException("다른 회원의 데이터에 접근할 수 없습니다."))
                .when(memberAccessService).requireMemberAccess(any(), any());

        mockMvc.perform(delete("/api/members/99/portfolios/20/broker-holding-adjustments/900"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(portfolioBrokerHoldingAdjustmentService);
    }

    private PortfolioBrokerHoldingAdjustment adjustmentRecord() {
        PortfolioBrokerHoldingSnapshotItem snapshotItem = mock(PortfolioBrokerHoldingSnapshotItem.class);
        when(snapshotItem.getId()).thenReturn(55L);

        PortfolioBrokerHoldingAdjustment adjustment = mock(PortfolioBrokerHoldingAdjustment.class);
        when(adjustment.getId()).thenReturn(900L);
        when(adjustment.getSnapshotItem()).thenReturn(snapshotItem);
        when(adjustment.getMarket()).thenReturn(Market.US);
        when(adjustment.getTicker()).thenReturn("SOXL");
        when(adjustment.getDisplayName()).thenReturn("Direxion Daily Semiconductor Bull 3X");
        when(adjustment.getDeltaQuantity()).thenReturn(new BigDecimal("5"));
        when(adjustment.getUnitPrice()).thenReturn(new BigDecimal("160.00"));
        when(adjustment.getBrokerQuantity()).thenReturn(new BigDecimal("15"));
        when(adjustment.getBrokerAveragePurchasePrice()).thenReturn(new BigDecimal("120.00"));
        when(adjustment.getLedgerQuantityBefore()).thenReturn(new BigDecimal("10"));
        when(adjustment.getLedgerAveragePurchasePriceBefore()).thenReturn(new BigDecimal("100.00"));
        when(adjustment.getSnapshotSyncedAt()).thenReturn(LocalDateTime.of(2026, 9, 11, 9, 30));
        when(adjustment.getTradeTransactionId()).thenReturn(900L);
        when(adjustment.getApprovedByMemberId()).thenReturn(10L);
        when(adjustment.getApprovedAt()).thenReturn(LocalDateTime.of(2026, 9, 11, 10, 0));
        when(adjustment.getStatus()).thenReturn(PortfolioBrokerHoldingAdjustmentStatus.ACTIVE);
        return adjustment;
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

    private PortfolioBrokerHoldingImport importRecord() {
        PortfolioBrokerHoldingSnapshotItem snapshotItem = mock(PortfolioBrokerHoldingSnapshotItem.class);
        when(snapshotItem.getId()).thenReturn(55L);

        PortfolioBrokerHoldingImport importRecord = mock(PortfolioBrokerHoldingImport.class);
        when(importRecord.getId()).thenReturn(900L);
        when(importRecord.getSnapshotItem()).thenReturn(snapshotItem);
        when(importRecord.getMarket()).thenReturn(Market.US);
        when(importRecord.getTicker()).thenReturn("SOXL");
        when(importRecord.getDisplayName()).thenReturn("Direxion Daily Semiconductor Bull 3X");
        when(importRecord.getQuantity()).thenReturn(new BigDecimal("30"));
        when(importRecord.getAveragePurchasePrice()).thenReturn(new BigDecimal("20.00"));
        when(importRecord.getSnapshotSyncedAt()).thenReturn(LocalDateTime.of(2026, 9, 4, 9, 30));
        when(importRecord.getTradeTransactionId()).thenReturn(900L);
        when(importRecord.getApprovedByMemberId()).thenReturn(10L);
        when(importRecord.getApprovedAt()).thenReturn(LocalDateTime.of(2026, 9, 6, 10, 0));
        when(importRecord.getStatus()).thenReturn(PortfolioBrokerHoldingImportStatus.ACTIVE);
        return importRecord;
    }

    /**
     * 증권사 연결 삭제로 원본 스냅샷 항목 참조가 끊긴 취소 이력이다.
     * 감사에 필요한 값은 승인 시점 그대로 남아 있고 스냅샷 참조만 널이다.
     */
    private PortfolioBrokerHoldingImport detachedRevokedImportRecord() {
        PortfolioBrokerHoldingImport importRecord = mock(PortfolioBrokerHoldingImport.class);
        when(importRecord.getId()).thenReturn(900L);
        // getSnapshotItem()은 스텁하지 않는다. 목의 기본값 널이 곧 참조가 끊긴 상태다.
        when(importRecord.getMarket()).thenReturn(Market.US);
        when(importRecord.getTicker()).thenReturn("SOXL");
        when(importRecord.getDisplayName()).thenReturn("Direxion Daily Semiconductor Bull 3X");
        when(importRecord.getQuantity()).thenReturn(new BigDecimal("30"));
        when(importRecord.getAveragePurchasePrice()).thenReturn(new BigDecimal("20.00"));
        when(importRecord.getSnapshotSyncedAt()).thenReturn(LocalDateTime.of(2026, 9, 4, 9, 30));
        when(importRecord.getTradeTransactionId()).thenReturn(900L);
        when(importRecord.getApprovedByMemberId()).thenReturn(10L);
        when(importRecord.getApprovedAt()).thenReturn(LocalDateTime.of(2026, 9, 6, 10, 0));
        when(importRecord.getStatus()).thenReturn(PortfolioBrokerHoldingImportStatus.REVOKED);
        return importRecord;
    }

    private PortfolioBrokerHoldingSnapshot snapshot() {
        Member member = new Member("broker@example.com", "broker-user");
        Portfolio portfolio = new Portfolio(member, "성장 포트폴리오");
        BrokerConnection connection = new BrokerConnection(member, BrokerProvider.TOSS_SECURITIES, "개인 토스증권");
        connection.replaceSecretValues(List.of(
                new BrokerConnectionSecretValue("clientId", "encrypted-client-id", "client-id-iv", 1),
                new BrokerConnectionSecretValue("clientSecret", "encrypted-client-secret", "client-secret-iv", 1)
        ));
        BrokerAccount account = new BrokerAccount("encrypted-sequence", "sequence-iv", "*****1234", "위탁", 1);
        connection.reconcileVerifiedAccounts(List.of(account));
        connection.markConnected("*****1234");
        org.springframework.test.util.ReflectionTestUtils.setField(connection, "id", 1L);

        return new PortfolioBrokerHoldingSnapshot(
                portfolio,
                connection,
                connection.getAccounts().getFirst(),
                LocalDateTime.of(2026, 9, 4, 9, 30),
                1,
                List.of(new BrokerHolding(Market.US, "SOXL", new BigDecimal("30"), new BigDecimal("20.00")))
        );
    }
}
