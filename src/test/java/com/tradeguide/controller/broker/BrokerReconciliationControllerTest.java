package com.tradeguide.controller.broker;

import com.tradeguide.domain.broker.BrokerAccount;
import com.tradeguide.domain.broker.BrokerConnection;
import com.tradeguide.domain.broker.BrokerHistoryPage;
import com.tradeguide.domain.broker.BrokerHistoryPageRequest;
import com.tradeguide.domain.broker.BrokerHoldingComparison;
import com.tradeguide.domain.broker.BrokerProvider;
import com.tradeguide.domain.broker.BrokerReconciliationLine;
import com.tradeguide.domain.broker.BrokerReconciliationOverallStatus;
import com.tradeguide.domain.broker.BrokerReconciliationReasonCode;
import com.tradeguide.domain.broker.BrokerReconciliationRun;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.exception.BrokerHoldingSnapshotNotFoundException;
import com.tradeguide.exception.BrokerReconciliationRunNotFoundException;
import com.tradeguide.exception.PortfolioNotFoundException;
import com.tradeguide.service.auth.MemberAccessService;
import com.tradeguide.service.broker.BrokerReconciliationService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

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

/**
 * 사용자 주도 원장 정합성 점검 API 계약을 검증한다.
 *
 * <p>이 컨트롤러는 읽기 전용이다. 여기서 확인하는 것은 응답 모양과 오류 매핑뿐이며,
 * 실제 비교·저장 로직은 {@code BrokerReconciliationServiceTest}와
 * {@code PostgresBrokerReconciliationIntegrationTest}가 검증한다.
 */
@WebMvcTest(BrokerReconciliationController.class)
@AutoConfigureMockMvc(addFilters = false)
class BrokerReconciliationControllerTest {

    private static final String BASE_PATH = "/api/members/10/portfolios/20/broker-reconciliations";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BrokerReconciliationService brokerReconciliationService;

    @MockitoBean
    private MemberAccessService memberAccessService;

    @Test
    void createsAReconciliationAndReturnsCreatedWithItsLines() throws Exception {
        // 목을 만드는 것 자체가 스터빙이므로, 바깥 when(...) 안에서 만들면 스터빙이 중첩돼 깨진다.
        BrokerReconciliationRun run = run();
        when(brokerReconciliationService.createReconciliation(10L, 20L)).thenReturn(run);

        mockMvc.perform(post(BASE_PATH))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.run.id").value(7))
                .andExpect(jsonPath("$.run.provider").value("TOSS_SECURITIES"))
                .andExpect(jsonPath("$.run.maskedAccountNumber").value("*****1234"))
                .andExpect(jsonPath("$.run.overallStatus").value("DIFFERENCES_FOUND"))
                .andExpect(jsonPath("$.run.matchedCount").value(1))
                .andExpect(jsonPath("$.run.onlyInBrokerCount").value(1))
                .andExpect(jsonPath("$.lines[0].ticker").value("AAPL"))
                .andExpect(jsonPath("$.lines[0].comparison").value("MATCHED"))
                .andExpect(jsonPath("$.lines[0].reasonCandidates").isEmpty())
                .andExpect(jsonPath("$.lines[1].ticker").value("005930"))
                .andExpect(jsonPath("$.lines[1].comparison").value("ONLY_IN_BROKER"))
                .andExpect(jsonPath("$.lines[1].reasonCandidates[0]").value("LEDGER_MARKET_UNSUPPORTED"));
    }

    @Test
    void reportsMissingSnapshotAsNotFound() throws Exception {
        when(brokerReconciliationService.createReconciliation(10L, 20L))
                .thenThrow(new BrokerHoldingSnapshotNotFoundException("저장된 증권사 보유 종목 스냅샷이 없습니다."));

        mockMvc.perform(post(BASE_PATH))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("저장된 증권사 보유 종목 스냅샷이 없습니다."));
    }

    @Test
    void reportsAMissingPortfolioOnCreateAsNotFoundWithCode() throws Exception {
        when(brokerReconciliationService.createReconciliation(10L, 20L))
                .thenThrow(new PortfolioNotFoundException("포트폴리오를 찾을 수 없습니다."));

        mockMvc.perform(post(BASE_PATH))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("포트폴리오를 찾을 수 없습니다."))
                .andExpect(jsonPath("$.code").value("PORTFOLIO_NOT_FOUND"));
    }

    @Test
    void blocksCreateAccessToAnotherMembersPortfolio() throws Exception {
        doThrow(new AccessDeniedException("다른 회원의 데이터에 접근할 수 없습니다."))
                .when(memberAccessService).requireMemberAccess(any(), any());

        mockMvc.perform(post(BASE_PATH))
                .andExpect(status().isForbidden());

        verifyNoInteractions(brokerReconciliationService);
    }

    @Test
    void listsReconciliationsWithDefaultPaging() throws Exception {
        BrokerHistoryPage<BrokerReconciliationRun> page =
                new BrokerHistoryPage<>(List.of(run()), 0, 20, 1L, false);
        when(brokerReconciliationService.getRuns(eq(10L), eq(20L), any(BrokerHistoryPageRequest.class)))
                .thenReturn(page);

        mockMvc.perform(get(BASE_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(7))
                .andExpect(jsonPath("$.items[0].overallStatus").value("DIFFERENCES_FOUND"))
                .andExpect(jsonPath("$.items[0].lines").doesNotExist())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.hasNext").value(false));

        ArgumentCaptor<BrokerHistoryPageRequest> pageRequest =
                ArgumentCaptor.forClass(BrokerHistoryPageRequest.class);
        verify(brokerReconciliationService).getRuns(eq(10L), eq(20L), pageRequest.capture());
        assertThat(pageRequest.getValue().page()).isZero();
        assertThat(pageRequest.getValue().size()).isEqualTo(BrokerHistoryPageRequest.DEFAULT_SIZE);
    }

    @Test
    void rejectsAPageSizeAboveTheServerLimit() throws Exception {
        mockMvc.perform(get(BASE_PATH).param("size", String.valueOf(BrokerHistoryPageRequest.MAX_SIZE + 1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "페이지 크기는 1 이상 " + BrokerHistoryPageRequest.MAX_SIZE + " 이하여야 합니다."));

        verifyNoInteractions(brokerReconciliationService);
    }

    @Test
    void getsAReconciliationDetail() throws Exception {
        BrokerReconciliationRun run = run();
        when(brokerReconciliationService.getRun(10L, 20L, 7L)).thenReturn(run);

        mockMvc.perform(get(BASE_PATH + "/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.run.id").value(7))
                .andExpect(jsonPath("$.lines").isArray());
    }

    @Test
    void reportsAMissingRunAsNotFound() throws Exception {
        when(brokerReconciliationService.getRun(10L, 20L, 7L))
                .thenThrow(new BrokerReconciliationRunNotFoundException("요청한 정합성 점검 실행을 찾을 수 없습니다."));

        mockMvc.perform(get(BASE_PATH + "/7"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("요청한 정합성 점검 실행을 찾을 수 없습니다."));
    }

    @Test
    void neverExposesCredentialsOrAccountSequence() throws Exception {
        BrokerReconciliationRun run = run();
        when(brokerReconciliationService.getRun(10L, 20L, 7L)).thenReturn(run);

        mockMvc.perform(get(BASE_PATH + "/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.run.maskedAccountNumber").value("*****1234"))
                .andExpect(jsonPath("$.run.accountSequence").doesNotExist())
                .andExpect(jsonPath("$.run.clientId").doesNotExist())
                .andExpect(jsonPath("$.run.clientSecret").doesNotExist());
    }

    @Test
    void blocksListAccessToAnotherMembersPortfolio() throws Exception {
        doThrow(new AccessDeniedException("다른 회원의 데이터에 접근할 수 없습니다."))
                .when(memberAccessService).requireMemberAccess(any(), any());

        mockMvc.perform(get(BASE_PATH))
                .andExpect(status().isForbidden());

        verifyNoInteractions(brokerReconciliationService);
    }

    private BrokerReconciliationRun run() {
        BrokerConnection connection = mock(BrokerConnection.class);
        when(connection.getId()).thenReturn(1L);
        when(connection.getProvider()).thenReturn(BrokerProvider.TOSS_SECURITIES);
        BrokerAccount account = mock(BrokerAccount.class);
        when(account.getMaskedAccountNumber()).thenReturn("*****1234");

        BrokerReconciliationRun run = mock(BrokerReconciliationRun.class);
        when(run.getId()).thenReturn(7L);
        when(run.getBrokerConnection()).thenReturn(connection);
        when(run.getBrokerAccount()).thenReturn(account);
        when(run.getSnapshotId()).thenReturn(500L);
        when(run.getSnapshotSyncedAt()).thenReturn(LocalDateTime.of(2026, 9, 8, 8, 0));
        when(run.getExecutedByMemberId()).thenReturn(10L);
        when(run.getExecutedAt()).thenReturn(LocalDateTime.of(2026, 9, 8, 9, 0));
        when(run.getOverallStatus()).thenReturn(BrokerReconciliationOverallStatus.DIFFERENCES_FOUND);
        when(run.getMatchedCount()).thenReturn(1);
        when(run.getQuantityMismatchCount()).thenReturn(0);
        when(run.getOnlyInBrokerCount()).thenReturn(1);
        when(run.getOnlyInTradeGuideCount()).thenReturn(0);
        BrokerReconciliationLine matchedLine = matchedLine();
        BrokerReconciliationLine onlyInBrokerLine = onlyInBrokerLine();
        when(run.getLines()).thenReturn(List.of(matchedLine, onlyInBrokerLine));
        return run;
    }

    private BrokerReconciliationLine matchedLine() {
        BrokerReconciliationLine line = mock(BrokerReconciliationLine.class);
        when(line.getMarket()).thenReturn(Market.US);
        when(line.getTicker()).thenReturn("AAPL");
        when(line.getDisplayName()).thenReturn("Apple Inc.");
        when(line.getBrokerQuantity()).thenReturn(new BigDecimal("10"));
        when(line.getTradeGuideQuantity()).thenReturn(new BigDecimal("10"));
        when(line.getComparison()).thenReturn(BrokerHoldingComparison.MATCHED);
        when(line.getReasonCandidates()).thenReturn(Set.of());
        return line;
    }

    private BrokerReconciliationLine onlyInBrokerLine() {
        BrokerReconciliationLine line = mock(BrokerReconciliationLine.class);
        when(line.getMarket()).thenReturn(Market.KR);
        when(line.getTicker()).thenReturn("005930");
        when(line.getDisplayName()).thenReturn("삼성전자");
        when(line.getBrokerQuantity()).thenReturn(new BigDecimal("10"));
        when(line.getTradeGuideQuantity()).thenReturn(null);
        when(line.getComparison()).thenReturn(BrokerHoldingComparison.ONLY_IN_BROKER);
        when(line.getReasonCandidates()).thenReturn(Set.of(BrokerReconciliationReasonCode.LEDGER_MARKET_UNSUPPORTED));
        return line;
    }
}
