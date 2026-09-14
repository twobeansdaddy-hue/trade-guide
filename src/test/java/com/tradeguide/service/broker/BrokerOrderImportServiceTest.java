package com.tradeguide.service.broker;

import com.tradeguide.domain.broker.BrokerAccount;
import com.tradeguide.domain.broker.BrokerCredentials;
import com.tradeguide.domain.broker.BrokerCredentialsFixture;
import com.tradeguide.domain.broker.BrokerHistoryPage;
import com.tradeguide.domain.broker.BrokerHistoryPageRequest;
import com.tradeguide.domain.broker.BrokerOrderExclusionCounts;
import com.tradeguide.domain.broker.BrokerOrderHistoryPage;
import com.tradeguide.domain.broker.BrokerOrderHistoryQuery;
import com.tradeguide.domain.broker.BrokerOrderImportApprovalAssessment;
import com.tradeguide.domain.broker.BrokerOrderImportApprovalBlocker;
import com.tradeguide.domain.broker.BrokerOrderImportCounts;
import com.tradeguide.domain.broker.BrokerOrderImportItem;
import com.tradeguide.domain.broker.BrokerOrderImportItemFilter;
import com.tradeguide.domain.broker.BrokerOrderImportRun;
import com.tradeguide.domain.broker.BrokerOrderImportRunStatus;
import com.tradeguide.domain.broker.BrokerOrderLifecycle;
import com.tradeguide.domain.broker.BrokerOrderReconciliationStatus;
import com.tradeguide.domain.broker.BrokerOrderRecord;
import com.tradeguide.domain.broker.BrokerOrderSide;
import com.tradeguide.domain.broker.BrokerOrderStagingStatus;
import com.tradeguide.domain.broker.BrokerOrderStatusGroup;
import com.tradeguide.domain.broker.BrokerProvider;
import com.tradeguide.domain.broker.PortfolioBrokerHoldingImport;
import com.tradeguide.domain.broker.PortfolioBrokerHoldingImportStatus;
import com.tradeguide.domain.member.Member;
import com.tradeguide.domain.portfolio.Portfolio;
import com.tradeguide.exception.BrokerCallCooldownException;
import com.tradeguide.exception.BrokerCallInProgressException;
import com.tradeguide.exception.BrokerConnectionUnavailableException;
import com.tradeguide.exception.BrokerOrderImportNotFoundException;
import com.tradeguide.exception.BrokerOrderImportUnprocessableException;
import com.tradeguide.exception.PortfolioNotFoundException;
import com.tradeguide.repository.broker.BrokerOrderImportItemRepository;
import com.tradeguide.repository.broker.BrokerOrderImportRunRepository;
import com.tradeguide.repository.broker.PortfolioBrokerHoldingImportRepository;
import com.tradeguide.repository.portfolio.PortfolioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BrokerOrderImportServiceTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-09-08T00:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate ORDERED_FROM = LocalDate.of(2026, 9, 1);
    private static final LocalDate ORDERED_TO = LocalDate.of(2026, 9, 5);

    @Mock
    private PortfolioRepository portfolioRepository;

    @Mock
    private BrokerOrderImportRunRepository brokerOrderImportRunRepository;

    @Mock
    private BrokerOrderImportItemRepository brokerOrderImportItemRepository;

    @Mock
    private PortfolioBrokerHoldingImportRepository portfolioBrokerHoldingImportRepository;

    @Mock
    private BrokerOrderImportContextLoader brokerOrderImportContextLoader;

    @Mock
    private BrokerProviderRegistry brokerProviderRegistry;

    @Mock
    private BrokerOrderImportRunWriter brokerOrderImportRunWriter;

    private RecordingOrderHistoryProvider provider;
    private BrokerDuplicateCallGuard brokerDuplicateCallGuard;
    private BrokerOrderImportService brokerOrderImportService;

    @BeforeEach
    void setUp() {
        provider = new RecordingOrderHistoryProvider();
        brokerDuplicateCallGuard = new BrokerDuplicateCallGuard();
        brokerOrderImportService = new BrokerOrderImportService(
                portfolioRepository,
                brokerOrderImportRunRepository,
                brokerOrderImportItemRepository,
                portfolioBrokerHoldingImportRepository,
                brokerOrderImportContextLoader,
                brokerProviderRegistry,
                brokerOrderImportRunWriter,
                brokerDuplicateCallGuard,
                FIXED_CLOCK
        );
    }

    /**
     * 증권사는 주문일 기준으로 조회하는데 미국 시장은 KST로 보면 주문일과 체결일이 하루 어긋난다.
     * 요청 구간 그대로 물어보면 경계일 체결이 통째로 빠지므로 앞뒤를 넓혀 조회한다.
     */
    @Test
    void widensTheQueriedRangeAroundTheRequestedOneBecauseTheBrokerQueriesByOrderDate() {
        givenConnectedAccount();
        provider.enqueue(lastPage());

        brokerOrderImportService.createPreview(10L, 20L, ORDERED_FROM, ORDERED_TO);

        assertThat(provider.closedQueries()).singleElement().satisfies(query -> {
            assertThat(query.orderedFrom()).isEqualTo(LocalDate.of(2026, 8, 30));
            assertThat(query.orderedTo()).isEqualTo(LocalDate.of(2026, 9, 7));
        });
        assertThat(capturedStaging().requestedOrderedFrom()).isEqualTo(ORDERED_FROM);
        assertThat(capturedStaging().queriedOrderedFrom()).isEqualTo(LocalDate.of(2026, 8, 30));
    }

    @Test
    void collectsEveryPageUntilTheProviderReportsNoMore() {
        givenConnectedAccount();
        provider.enqueue(pageWithNext(List.of(filled("order-1")), "cursor-1"));
        provider.enqueue(pageWithNext(List.of(filled("order-2")), "cursor-2"));
        provider.enqueue(BrokerOrderHistoryPage.lastPage(
                List.of(filled("order-3")), BrokerOrderExclusionCounts.none(1)));

        brokerOrderImportService.createPreview(10L, 20L, ORDERED_FROM, ORDERED_TO);

        assertThat(capturedStaging().records())
                .extracting(BrokerOrderRecord::externalOrderId)
                .containsExactly("order-1", "order-2", "order-3");
        assertThat(provider.closedQueries()).extracting(BrokerOrderHistoryQuery::cursor)
                .containsExactly(null, "cursor-1", "cursor-2");
    }

    /** 기간을 넓혀 조회하므로 같은 주문이 두 번 올 수 있다. 합치되 사실은 건수로 남긴다. */
    @Test
    void collapsesOrdersThatComeBackTwiceAndReportsHowOftenItHappened() {
        givenConnectedAccount();
        provider.enqueue(pageWithNext(List.of(filled("order-1")), "cursor-1"));
        provider.enqueue(BrokerOrderHistoryPage.lastPage(
                List.of(filled("order-1"), filled("order-2")), BrokerOrderExclusionCounts.none(2)));

        brokerOrderImportService.createPreview(10L, 20L, ORDERED_FROM, ORDERED_TO);

        assertThat(capturedStaging().records())
                .extracting(BrokerOrderRecord::externalOrderId)
                .containsExactly("order-1", "order-2");
        assertThat(capturedStaging().duplicateFetchCount()).isEqualTo(1);
    }

    @Test
    void carriesAdapterExclusionCountsIntoTheRunSoNothingGoesMissingSilently() {
        givenConnectedAccount();
        provider.enqueue(BrokerOrderHistoryPage.lastPage(
                List.of(filled("order-1")), new BrokerOrderExclusionCounts(4, 1, 2, 3)));

        brokerOrderImportService.createPreview(10L, 20L, ORDERED_FROM, ORDERED_TO);

        assertThat(capturedStaging().unsupportedMarketCount()).isEqualTo(1);
        assertThat(capturedStaging().unsupportedCurrencyCount()).isEqualTo(2);
        assertThat(capturedStaging().unknownEnumCount()).isEqualTo(3);
    }

    /**
     * 진행 중 주문은 전량이 한 번에 오고 커서·페이지 크기가 무시된다. 순회하지 않고 건수만 쓴다.
     * 원장에 넣지 않지만 건수를 감추면 사용자는 데이터가 누락됐다고 읽는다.
     */
    @Test
    void asksForOpenOrdersOnceAndKeepsOnlyTheirCount() {
        givenConnectedAccount();
        provider.enqueue(lastPage());
        provider.enqueueOpen(BrokerOrderHistoryPage.lastPage(
                List.of(filled("open-1"), filled("open-2"), filled("open-3")),
                BrokerOrderExclusionCounts.none(3)));

        brokerOrderImportService.createPreview(10L, 20L, ORDERED_FROM, ORDERED_TO);

        assertThat(provider.openQueries()).singleElement()
                .satisfies(query -> assertThat(query.cursor()).isNull());
        assertThat(capturedStaging().openOrderCount()).isEqualTo(3);
    }

    /**
     * 같은 커서가 다시 오면 계속 돌면 무한 순회고 멈추면 이력이 잘린다. 둘 다 나쁘므로 실패로 알린다.
     * 잘린 이력은 조용히 틀린 원장을 만든다.
     */
    @Test
    void failsInsteadOfLoopingOrTruncatingWhenTheProviderRepeatsACursor() {
        givenConnectedAccount();
        provider.enqueue(pageWithNext(List.of(filled("order-1")), "cursor-1"));
        provider.enqueue(pageWithNext(List.of(filled("order-2")), "cursor-1"));

        assertThatThrownBy(() -> brokerOrderImportService.createPreview(10L, 20L, ORDERED_FROM, ORDERED_TO))
                .isInstanceOf(BrokerOrderImportUnprocessableException.class);

        assertThat(capturedFailure().failureCode()).isEqualTo("CURSOR_REPEATED");
        verify(brokerOrderImportRunWriter, org.mockito.Mockito.never()).stage(any());
    }

    @Test
    void failsWhenTheRangeHasMorePagesThanTheTraversalLimitAllows() {
        givenConnectedAccount();
        for (int page = 0; page < 60; page++) {
            provider.enqueue(pageWithNext(List.of(filled("order-" + page)), "cursor-" + page));
        }

        assertThatThrownBy(() -> brokerOrderImportService.createPreview(10L, 20L, ORDERED_FROM, ORDERED_TO))
                .isInstanceOf(BrokerOrderImportUnprocessableException.class)
                // 요청 구간이 5일이라 창이 하나뿐이다. 가장 좁힌 창조차 예산 안에 못 끝났다는
                // 사실을 메시지로 구체화한다(S6b).
                .hasMessageContaining("30일 창에서도");

        assertThat(capturedFailure().failureCode()).isEqualTo("PAGE_LIMIT_EXCEEDED");
    }

    /**
     * 서버 구간 분할이 30일 단위로 창을 나누고, 창 경계의 padding은 내부 경계에만 적용한다.
     * 전체 구간의 양 끝은 이미 호출자가 넓혀 뒀으므로(BOUNDARY_PADDING_DAYS) 거기에 또
     * padding을 더하면 이중 패딩이 된다.
     */
    @Test
    void splitsALongRangeIntoThirtyDayWindowsWithPaddingOnlyAtInternalBoundaries() {
        givenConnectedAccount();
        provider.enqueue(lastPage());
        provider.enqueue(lastPage());
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 2, 25); // 56일 요청 → 60일 조회 구간 → 30일 창 2개

        brokerOrderImportService.createPreview(10L, 20L, from, to);

        assertThat(provider.closedQueries()).extracting(
                BrokerOrderHistoryQuery::orderedFrom, BrokerOrderHistoryQuery::orderedTo)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                LocalDate.of(2025, 12, 30), LocalDate.of(2026, 1, 30)),
                        org.assertj.core.groups.Tuple.tuple(
                                LocalDate.of(2026, 1, 27), LocalDate.of(2026, 2, 27)));
        // 요청 구간을 끝까지 커버했으므로 coveredOrderedTo는 없다.
        assertThat(capturedStaging().coveredOrderedTo()).isNull();
    }

    /**
     * 창 하나가 공유 예산을 다 쓰기 전에 끝내지 못하면 그 창은 절반만 가져온 채로 버리고,
     * 그 앞까지 완전히 끝난 창의 끝 날짜를 coveredOrderedTo로 남긴다. 실패시키지 않는다 —
     * 이미 완전히 끝난 창의 주문은 실제로 체결된 주문이므로 그대로 스테이징한다.
     */
    @Test
    void discardsAWindowThatExhaustsTheSharedBudgetAndReportsHowFarItGot() {
        givenConnectedAccount();
        provider.enqueue(lastPage()); // 창 0: 1페이지로 끝, 남은 예산 49
        for (int page = 0; page < 49; page++) {
            // 창 1: 49페이지 안에 끝나지 않아 공유 예산을 전부 쓴다.
            provider.enqueue(pageWithNext(List.of(), "cursor-" + page));
        }
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 2, 25); // 창 2개(60일 조회 구간)

        brokerOrderImportService.createPreview(10L, 20L, from, to);

        assertThat(capturedStaging().coveredOrderedTo()).isEqualTo(LocalDate.of(2026, 1, 28));
        assertThat(provider.closedQueries()).hasSize(50);
    }

    /**
     * 실패를 남기지 않으면 재현되지 않는 장애를 영원히 추적할 수 없다.
     * 요청 상관 id는 비밀값이 아니고 증권사에 문의할 때 쓸 수 있는 유일한 근거다.
     */
    @Test
    void recordsAFailedRunWithTheSanitizedProviderRequestIdWhenTheProviderCallFails() {
        givenConnectedAccount();
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Request-Id", "req-abc-123");
        provider.enqueueFailure(new BrokerConnectionUnavailableException(
                "토스증권 주문 이력 조회에 실패했습니다.",
                HttpClientErrorException.create(
                        HttpStatus.INTERNAL_SERVER_ERROR, "error", headers, new byte[0], null)));

        assertThatThrownBy(() -> brokerOrderImportService.createPreview(10L, 20L, ORDERED_FROM, ORDERED_TO))
                .isInstanceOf(BrokerConnectionUnavailableException.class);

        assertThat(capturedFailure().failureCode()).isEqualTo("PROVIDER_UNAVAILABLE");
        assertThat(capturedFailure().providerRequestId()).isEqualTo("req-abc-123");
    }

    /** 응답 헤더는 증권사가 채우는 값이다. 예상한 형태가 아니면 저장하지 않는다. */
    @Test
    void refusesToStoreAProviderRequestIdThatDoesNotLookLikeAnOpaqueCorrelationId() {
        givenConnectedAccount();
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Request-Id", "계좌번호 1234-5678 조회 실패");
        provider.enqueueFailure(new BrokerConnectionUnavailableException(
                "토스증권 주문 이력 조회에 실패했습니다.",
                HttpClientErrorException.create(
                        HttpStatus.INTERNAL_SERVER_ERROR, "error", headers, new byte[0], null)));

        assertThatThrownBy(() -> brokerOrderImportService.createPreview(10L, 20L, ORDERED_FROM, ORDERED_TO))
                .isInstanceOf(BrokerConnectionUnavailableException.class);

        assertThat(capturedFailure().providerRequestId()).isNull();
    }

    @Test
    void rejectsARangeWhoseStartIsAfterItsEnd() {
        assertThatThrownBy(() ->
                brokerOrderImportService.createPreview(10L, 20L, ORDERED_TO, ORDERED_FROM))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("주문 조회 시작일은 종료일보다 늦을 수 없습니다.");

        verifyNoInteractions(brokerOrderImportContextLoader, brokerOrderImportRunWriter);
    }

    /** 같은 날 요청은 양 끝을 포함해 1일이므로 상한과 무관하게 항상 통과한다. */
    @Test
    void acceptsASameDayRange() {
        givenConnectedAccount();
        provider.enqueue(lastPage());
        LocalDate sameDay = LocalDate.of(2026, 9, 1);

        brokerOrderImportService.createPreview(10L, 20L, sameDay, sameDay);

        assertThat(capturedStaging().requestedOrderedFrom()).isEqualTo(sameDay);
    }

    /** 양 끝 날짜를 포함해 정확히 366일(상한)이면 통과한다. */
    @Test
    void acceptsARangeOfExactlyTheThreeHundredSixtySixDayLimit() {
        givenConnectedAccount();
        provider.enqueue(lastPage());
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = from.plusDays(365); // 양 끝 포함 366일

        brokerOrderImportService.createPreview(10L, 20L, from, to);

        assertThat(capturedStaging().requestedOrderedFrom()).isEqualTo(from);
    }

    /** 양 끝 날짜를 포함해 367일이면 증권사·연결 조회 없이 400으로 거부한다. */
    @Test
    void rejectsARangeOfThreeHundredSixtySevenDaysAsExceedingTheLimit() {
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = from.plusDays(366); // 양 끝 포함 367일

        assertThatThrownBy(() -> brokerOrderImportService.createPreview(10L, 20L, from, to))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("조회 기간은 양 끝 날짜를 포함해 최대 366일까지 요청할 수 있습니다. "
                        + "현재 367일을 요청했습니다.");

        verifyNoInteractions(brokerOrderImportContextLoader, brokerOrderImportRunWriter);
    }

    /** 같은 포트폴리오의 주문 이력 가져오기가 이미 진행 중이면 409에 해당하는 예외로 거부한다. */
    @Test
    void rejectsAConcurrentOrderImportCallForTheSamePortfolio() {
        brokerDuplicateCallGuard.acquire(20L, BrokerCallType.ORDER_HISTORY_IMPORT);

        assertThatThrownBy(() -> brokerOrderImportService.createPreview(10L, 20L, ORDERED_FROM, ORDERED_TO))
                .isInstanceOf(BrokerCallInProgressException.class);

        verifyNoInteractions(brokerOrderImportContextLoader, brokerOrderImportRunWriter);
    }

    /** 직전 실행(성공이든 실패든) 시각이 쿨다운 이내면 429에 해당하는 예외로 거부한다. */
    @Test
    void rejectsARepeatedOrderImportCallWithinTheCooldownWindow() {
        BrokerOrderImportRun previousRun = mock(BrokerOrderImportRun.class);
        when(previousRun.getStartedAt()).thenReturn(LocalDateTime.now(FIXED_CLOCK).minusSeconds(2));
        when(brokerOrderImportRunRepository.findFirstByPortfolio_IdOrderByStartedAtDesc(20L))
                .thenReturn(Optional.of(previousRun));

        assertThatThrownBy(() -> brokerOrderImportService.createPreview(10L, 20L, ORDERED_FROM, ORDERED_TO))
                .isInstanceOf(BrokerCallCooldownException.class);

        verifyNoInteractions(brokerOrderImportContextLoader, brokerOrderImportRunWriter);
    }

    /** 쿨다운 시간이 지났으면 직전 실행이 있어도 정상적으로 진행한다. */
    @Test
    void allowsAnOrderImportCallAfterTheCooldownWindowHasPassed() {
        givenConnectedAccount();
        provider.enqueue(lastPage());
        BrokerOrderImportRun previousRun = mock(BrokerOrderImportRun.class);
        when(previousRun.getStartedAt()).thenReturn(LocalDateTime.now(FIXED_CLOCK).minusSeconds(5));
        when(brokerOrderImportRunRepository.findFirstByPortfolio_IdOrderByStartedAtDesc(20L))
                .thenReturn(Optional.of(previousRun));

        brokerOrderImportService.createPreview(10L, 20L, ORDERED_FROM, ORDERED_TO);

        assertThat(capturedStaging().requestedOrderedFrom()).isEqualTo(ORDERED_FROM);
    }

    /** 잠금은 성공이든 실패든 항상 풀려서 다음 호출이 409로 영구히 막히지 않는다. */
    @Test
    void releasesTheLockEvenWhenTheProviderCallFails() {
        givenConnectedAccount();
        provider.enqueueFailure(new BrokerConnectionUnavailableException("토스증권 주문 이력 조회에 실패했습니다."));

        assertThatThrownBy(() -> brokerOrderImportService.createPreview(10L, 20L, ORDERED_FROM, ORDERED_TO))
                .isInstanceOf(BrokerConnectionUnavailableException.class);

        assertThatCode(() ->
                brokerDuplicateCallGuard.acquire(20L, BrokerCallType.ORDER_HISTORY_IMPORT))
                .doesNotThrowAnyException();
    }

    @Test
    void refusesToListRunsOfAPortfolioThatDoesNotBelongToTheMember() {
        when(portfolioRepository.findByMember_IdAndId(99L, 20L)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                brokerOrderImportService.getRuns(99L, 20L, new BrokerHistoryPageRequest(0, 20)))
                .isInstanceOf(PortfolioNotFoundException.class)
                .hasMessage("포트폴리오를 찾을 수 없습니다.");

        verifyNoInteractions(brokerOrderImportRunRepository);
    }

    @Test
    void listsRunsOnePageAtATimeNewestFirst() {
        when(portfolioRepository.findByMember_IdAndId(10L, 20L)).thenReturn(Optional.of(portfolio()));
        BrokerOrderImportRun run = mock(BrokerOrderImportRun.class);
        when(brokerOrderImportRunRepository.findAllByPortfolio_Id(eq(20L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(run), PageRequest.of(1, 2), 7));

        BrokerHistoryPage<BrokerOrderImportRun> page =
                brokerOrderImportService.getRuns(10L, 20L, new BrokerHistoryPageRequest(1, 2));

        assertThat(page.items()).containsExactly(run);
        assertThat(page.page()).isEqualTo(1);
        assertThat(page.size()).isEqualTo(2);
        assertThat(page.totalElements()).isEqualTo(7);
        assertThat(page.hasNext()).isTrue();

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(brokerOrderImportRunRepository).findAllByPortfolio_Id(eq(20L), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(2);
        assertThat(pageable.getValue().getSort()).isEqualTo(
                Sort.by(Sort.Order.desc("startedAt"), Sort.Order.desc("id")));
    }

    @Test
    void rejectsRunPageSizeAboveTheServerLimit() {
        assertThatThrownBy(() -> new BrokerHistoryPageRequest(0, BrokerHistoryPageRequest.MAX_SIZE + 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("페이지 크기");

        verifyNoInteractions(brokerOrderImportRunRepository);
    }

    @Test
    void reportsAMissingRunAsNotFound() {
        when(portfolioRepository.findByMember_IdAndId(10L, 20L)).thenReturn(Optional.of(portfolio()));
        when(brokerOrderImportRunRepository.findByPortfolio_IdAndId(20L, 7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> brokerOrderImportService.getRun(10L, 20L, 7L))
                .isInstanceOf(BrokerOrderImportNotFoundException.class);
    }

    /**
     * 승인 판정을 서버가 계산해 담는다. 화면이 항목 배열을 세지 않고도 승인 버튼을 열지 말지
     * 정할 수 있어야, 항목이 페이지로 나뉘어도 판정이 흔들리지 않는다.
     */
    @Test
    void reportsTheApprovalAssessmentInsteadOfTheItemList() {
        BrokerOrderImportRun run = givenStagedRun(BrokerOrderReconciliationStatus.MATCHED, counts(5, 3));
        givenNoActiveOpeningBalance();
        when(brokerOrderImportItemRepository.countEligibleItems(7L, null)).thenReturn(5L);
        when(brokerOrderImportItemRepository.countEligibleItemsAlreadyLinked(7L, 100L, null)).thenReturn(2L);

        BrokerOrderImportApprovalAssessment approval =
                brokerOrderImportService.getRun(10L, 20L, 7L).approval();

        assertThat(approval.stagedCount()).isEqualTo(5);
        assertThat(approval.eligibleCount()).isEqualTo(5);
        assertThat(approval.baselineExcludedCount()).isZero();
        assertThat(approval.alreadyLinkedCount()).isEqualTo(2);
        assertThat(approval.writableCount()).isEqualTo(3);
        assertThat(approval.baselineAt()).isNull();
        assertThat(approval.approvable()).isTrue();
        assertThat(approval.blocker()).isNull();

        // 판정을 항목을 세어 만들면 응답에서 항목을 뺀 뜻이 사라진다. 크기 문제가 힙으로 옮겨 갈 뿐이다.
        verify(run, never()).getItems();
    }

    /**
     * 기준 시각은 실행에 저장할 수 없다. 실행이 끝난 뒤에도 개시 잔고가 새로 승인되면 바뀌므로
     * 조회할 때마다 지금 값으로 다시 계산해야 승인 시점의 판정과 어긋나지 않는다.
     */
    @Test
    void countsEligibleItemsAgainstTheCurrentOpeningBalanceBaseline() {
        givenStagedRun(BrokerOrderReconciliationStatus.MATCHED, counts(4, 0));
        Instant baseline = Instant.parse("2026-09-03T00:00:00Z");
        givenActiveOpeningBalanceApprovedAt(LocalDateTime.ofInstant(baseline, ZoneOffset.UTC));
        when(brokerOrderImportItemRepository.countEligibleItems(7L, baseline)).thenReturn(1L);
        when(brokerOrderImportItemRepository.countEligibleItemsAlreadyLinked(7L, 100L, baseline))
                .thenReturn(0L);

        BrokerOrderImportApprovalAssessment approval =
                brokerOrderImportService.getRun(10L, 20L, 7L).approval();

        assertThat(approval.eligibleCount()).isEqualTo(1);
        assertThat(approval.baselineExcludedCount()).isEqualTo(3);
        assertThat(approval.baselineAt()).isEqualTo(LocalDateTime.ofInstant(baseline, ZoneOffset.UTC));
        assertThat(approval.approvable()).isTrue();

        verify(brokerOrderImportItemRepository).countEligibleItems(7L, baseline);
    }

    /**
     * 후보가 애초에 없었던 것과, 후보는 있었으나 기준 시각이 전부 걸러낸 것은 사용자가 할 일이
     * 다르다. 하나로 뭉치면 화면이 다음 행동을 제시할 수 없다.
     */
    @Test
    void distinguishesAnEmptyRunFromOneFullyExcludedByTheBaseline() {
        givenStagedRun(BrokerOrderReconciliationStatus.MATCHED, counts(4, 0));
        Instant baseline = Instant.parse("2026-09-03T00:00:00Z");
        givenActiveOpeningBalanceApprovedAt(LocalDateTime.ofInstant(baseline, ZoneOffset.UTC));
        when(brokerOrderImportItemRepository.countEligibleItems(7L, baseline)).thenReturn(0L);
        when(brokerOrderImportItemRepository.countEligibleItemsAlreadyLinked(7L, 100L, baseline))
                .thenReturn(0L);

        BrokerOrderImportApprovalAssessment approval =
                brokerOrderImportService.getRun(10L, 20L, 7L).approval();

        assertThat(approval.approvable()).isFalse();
        assertThat(approval.blocker())
                .isEqualTo(BrokerOrderImportApprovalBlocker.ALL_BEFORE_BASELINE);
    }

    /**
     * 대조가 불일치면 서버는 승인을 거부한다. 판정이 그 사실을 말하지 않으면 화면은 승인 버튼을
     * 열어 두고 사용자는 눌러 본 뒤에야 거부를 알게 된다.
     */
    @Test
    void refusesApprovalWhileTheHoldingReconciliationDisagrees() {
        givenStagedRun(BrokerOrderReconciliationStatus.MISMATCHED, counts(5, 0));
        givenNoActiveOpeningBalance();
        when(brokerOrderImportItemRepository.countEligibleItems(7L, null)).thenReturn(5L);
        when(brokerOrderImportItemRepository.countEligibleItemsAlreadyLinked(7L, 100L, null)).thenReturn(0L);

        BrokerOrderImportApprovalAssessment approval =
                brokerOrderImportService.getRun(10L, 20L, 7L).approval();

        assertThat(approval.approvable()).isFalse();
        assertThat(approval.blocker())
                .isEqualTo(BrokerOrderImportApprovalBlocker.RECONCILIATION_MISMATCHED);
        // 승인은 막히지만 건수는 그대로 보고한다. 무엇이 걸려 있는지 사용자가 알아야 한다.
        assertThat(approval.eligibleCount()).isEqualTo(5);
    }

    @Test
    void readsRunItemsOnePageAtATimeNewestOrderedFirst() {
        BrokerOrderImportItem item = mock(BrokerOrderImportItem.class);
        givenRun();
        when(brokerOrderImportItemRepository.findRunItems(eq(7L), eq(null), eq(null), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(item), PageRequest.of(1, 2), 7));

        BrokerHistoryPage<BrokerOrderImportItem> page = brokerOrderImportService.getRunItems(
                10L, 20L, 7L, BrokerOrderImportItemFilter.NONE, new BrokerHistoryPageRequest(1, 2));

        assertThat(page.items()).containsExactly(item);
        assertThat(page.page()).isEqualTo(1);
        assertThat(page.size()).isEqualTo(2);
        assertThat(page.totalElements()).isEqualTo(7);
        assertThat(page.hasNext()).isTrue();

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(brokerOrderImportItemRepository)
                .findRunItems(eq(7L), eq(null), eq(null), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(2);
        // 주문 시각만으로는 순서가 정해지지 않는다. 동점 기준이 없으면 페이지 경계가 흔들린다.
        assertThat(pageable.getValue().getSort()).isEqualTo(
                Sort.by(Sort.Order.desc("orderedAt"), Sort.Order.desc("id")));
    }

    /** 거르기는 저장소까지 내려가야 한다. 전부 읽어 화면에서 거르면 페이지를 나눈 뜻이 없다. */
    @Test
    void appliesTheStatusAndSymbolFiltersInTheQueryItself() {
        givenRun();
        when(brokerOrderImportItemRepository.findRunItems(
                eq(7L), eq(BrokerOrderStagingStatus.STAGED), eq("AAPL"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        brokerOrderImportService.getRunItems(
                10L, 20L, 7L,
                BrokerOrderImportItemFilter.of("staged", " aapl "),
                new BrokerHistoryPageRequest(0, 20));

        verify(brokerOrderImportItemRepository).findRunItems(
                eq(7L), eq(BrokerOrderStagingStatus.STAGED), eq("AAPL"), any(Pageable.class));
    }

    /**
     * 항목 조회는 실행 id만 본다. 실행이 이 포트폴리오 것인지 먼저 확인하지 않으면
     * 실행 id만 바꿔 다른 회원의 항목을 읽을 수 있다.
     */
    @Test
    void refusesToReadItemsOfARunOutsideThePortfolio() {
        when(portfolioRepository.findByMember_IdAndId(10L, 20L)).thenReturn(Optional.of(portfolio()));
        when(brokerOrderImportRunRepository.findByPortfolio_IdAndId(20L, 7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> brokerOrderImportService.getRunItems(
                10L, 20L, 7L, BrokerOrderImportItemFilter.NONE, new BrokerHistoryPageRequest(0, 20)))
                .isInstanceOf(BrokerOrderImportNotFoundException.class);

        verifyNoInteractions(brokerOrderImportItemRepository);
    }

    @Test
    void refusesToReadItemsOfAPortfolioThatDoesNotBelongToTheMember() {
        when(portfolioRepository.findByMember_IdAndId(99L, 20L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> brokerOrderImportService.getRunItems(
                99L, 20L, 7L, BrokerOrderImportItemFilter.NONE, new BrokerHistoryPageRequest(0, 20)))
                .isInstanceOf(PortfolioNotFoundException.class)
                .hasMessage("포트폴리오를 찾을 수 없습니다.");

        verifyNoInteractions(brokerOrderImportRunRepository, brokerOrderImportItemRepository);
    }

    /** 알 수 없는 상태를 조용히 무시하면 호출자는 조건이 걸린 결과를 받았다고 오해한다. */
    @Test
    void rejectsAnUnknownItemStatusFilter() {
        assertThatThrownBy(() -> BrokerOrderImportItemFilter.of("NOT_A_STATUS", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("알 수 없는 주문 항목 상태입니다.");
    }

    @Test
    void treatsBlankStatusAndSymbolAsNoFilter() {
        BrokerOrderImportItemFilter filter = BrokerOrderImportItemFilter.of("  ", "  ");

        assertThat(filter.stagingStatus()).isNull();
        assertThat(filter.ticker()).isNull();
    }

    /**
     * 승인 판정 계산에 필요한 것만 갖춘 실행이다. 항목 배열은 일부러 스터빙하지 않는다.
     * 서비스가 그것을 읽으면 스터빙이 없어 빈 목록이 나오고 테스트가 그 사실을 드러낸다.
     */
    private BrokerOrderImportRun givenStagedRun(
            BrokerOrderReconciliationStatus reconciliationStatus,
            BrokerOrderImportCounts counts
    ) {
        when(portfolioRepository.findByMember_IdAndId(10L, 20L)).thenReturn(Optional.of(portfolio()));

        BrokerAccount account = mock(BrokerAccount.class);
        when(account.getId()).thenReturn(100L);

        BrokerOrderImportRun run = mock(BrokerOrderImportRun.class);
        when(run.getId()).thenReturn(7L);
        when(run.getBrokerAccount()).thenReturn(account);
        when(run.getStatus()).thenReturn(BrokerOrderImportRunStatus.STAGED);
        when(run.getReconciliationStatus()).thenReturn(reconciliationStatus);
        when(run.getCounts()).thenReturn(counts);
        when(run.getReconciliationLines()).thenReturn(List.of());
        when(brokerOrderImportRunRepository.findByPortfolio_IdAndId(20L, 7L)).thenReturn(Optional.of(run));
        return run;
    }

    private void givenNoActiveOpeningBalance() {
        when(portfolioBrokerHoldingImportRepository
                .findFirstByPortfolio_IdAndStatusOrderByApprovedAtDesc(
                        20L, PortfolioBrokerHoldingImportStatus.ACTIVE))
                .thenReturn(Optional.empty());
    }

    private void givenActiveOpeningBalanceApprovedAt(LocalDateTime approvedAt) {
        PortfolioBrokerHoldingImport openingBalance = mock(PortfolioBrokerHoldingImport.class);
        when(openingBalance.getApprovedAt()).thenReturn(approvedAt);
        when(portfolioBrokerHoldingImportRepository
                .findFirstByPortfolio_IdAndStatusOrderByApprovedAtDesc(
                        20L, PortfolioBrokerHoldingImportStatus.ACTIVE))
                .thenReturn(Optional.of(openingBalance));
    }

    /** 반영 후보와 정상 제외만 있는 최소 건수다. 불변식(조회 = 배타 분류 합)을 만족해야 한다. */
    private BrokerOrderImportCounts counts(int stagedCount, int notFilledCount) {
        return new BrokerOrderImportCounts(
                stagedCount + notFilledCount, stagedCount, notFilledCount,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    private void givenRun() {
        when(portfolioRepository.findByMember_IdAndId(10L, 20L)).thenReturn(Optional.of(portfolio()));
        BrokerOrderImportRun run = mock(BrokerOrderImportRun.class);
        when(run.getId()).thenReturn(7L);
        when(brokerOrderImportRunRepository.findByPortfolio_IdAndId(20L, 7L)).thenReturn(Optional.of(run));
    }

    private void givenConnectedAccount() {
        when(brokerOrderImportContextLoader.load(10L, 20L)).thenReturn(new BrokerOrderImportService.ImportContext(
                1L,
                100L,
                BrokerProvider.TOSS_SECURITIES,
                BrokerCredentialsFixture.tossCredentials(),
                "account-sequence"));
        when(brokerProviderRegistry.requireOrderHistoryProvider(BrokerProvider.TOSS_SECURITIES))
                .thenReturn(provider);
    }

    private BrokerOrderImportRunWriter.StagingRequest capturedStaging() {
        ArgumentCaptor<BrokerOrderImportRunWriter.StagingRequest> captor =
                ArgumentCaptor.forClass(BrokerOrderImportRunWriter.StagingRequest.class);
        verify(brokerOrderImportRunWriter).stage(captor.capture());
        return captor.getValue();
    }

    private BrokerOrderImportRunWriter.FailureRequest capturedFailure() {
        ArgumentCaptor<BrokerOrderImportRunWriter.FailureRequest> captor =
                ArgumentCaptor.forClass(BrokerOrderImportRunWriter.FailureRequest.class);
        verify(brokerOrderImportRunWriter).recordFailure(captor.capture());
        return captor.getValue();
    }

    private Portfolio portfolio() {
        return new Portfolio(new Member("owner@example.com", "owner"), "성장 포트폴리오");
    }

    private BrokerOrderHistoryPage lastPage() {
        return BrokerOrderHistoryPage.lastPage(List.of(), BrokerOrderExclusionCounts.none(0));
    }

    private BrokerOrderHistoryPage pageWithNext(List<BrokerOrderRecord> records, String nextCursor) {
        return new BrokerOrderHistoryPage(
                records, nextCursor, true, BrokerOrderExclusionCounts.none(records.size()));
    }

    private BrokerOrderRecord filled(String orderId) {
        return new BrokerOrderRecord(
                orderId, com.tradeguide.domain.trade.Market.US, "AAPL", BrokerOrderSide.BUY,
                BrokerOrderLifecycle.TERMINAL_WITH_FILL, "FILLED", "LIMIT", "DAY", "USD",
                new BigDecimal("10"), new BigDecimal("10"), new BigDecimal("100.25"), new BigDecimal("1002.50"),
                BigDecimal.ZERO, BigDecimal.ZERO,
                Instant.parse("2026-09-01T00:30:00Z"), Instant.parse("2026-09-01T13:30:00Z"), null);
    }

    /** 조회 조건을 기록하고 미리 넣어 둔 응답을 순서대로 돌려주는 어댑터 대역이다. */
    private static final class RecordingOrderHistoryProvider implements BrokerOrderHistoryProvider {

        private final Deque<Object> closedResponses = new ArrayDeque<>();
        private final Deque<BrokerOrderHistoryPage> openResponses = new ArrayDeque<>();
        private final List<BrokerOrderHistoryQuery> closedQueries = new ArrayList<>();
        private final List<BrokerOrderHistoryQuery> openQueries = new ArrayList<>();

        void enqueue(BrokerOrderHistoryPage page) {
            closedResponses.add(page);
        }

        void enqueueFailure(RuntimeException exception) {
            closedResponses.add(exception);
        }

        void enqueueOpen(BrokerOrderHistoryPage page) {
            openResponses.add(page);
        }

        List<BrokerOrderHistoryQuery> closedQueries() {
            return closedQueries;
        }

        List<BrokerOrderHistoryQuery> openQueries() {
            return openQueries;
        }

        @Override
        public BrokerProvider getProvider() {
            return BrokerProvider.TOSS_SECURITIES;
        }

        @Override
        public BrokerOrderHistoryPage fetchOrders(
                BrokerCredentials credentials,
                String accountSequence,
                BrokerOrderHistoryQuery query
        ) {
            if (query.statusGroup() == BrokerOrderStatusGroup.OPEN) {
                openQueries.add(query);
                return openResponses.isEmpty()
                        ? BrokerOrderHistoryPage.lastPage(List.of(), BrokerOrderExclusionCounts.none(0))
                        : openResponses.poll();
            }

            closedQueries.add(query);
            Object response = closedResponses.poll();
            if (response instanceof RuntimeException exception) {
                throw exception;
            }
            return response == null
                    ? BrokerOrderHistoryPage.lastPage(List.of(), BrokerOrderExclusionCounts.none(0))
                    : (BrokerOrderHistoryPage) response;
        }
    }
}
