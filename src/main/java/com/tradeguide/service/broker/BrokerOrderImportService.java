package com.tradeguide.service.broker;

import com.tradeguide.domain.broker.BrokerHistoryPage;
import com.tradeguide.domain.broker.BrokerHistoryPageRequest;
import com.tradeguide.domain.broker.BrokerCredentials;
import com.tradeguide.domain.broker.BrokerOrderHistoryPage;
import com.tradeguide.domain.broker.BrokerOrderHistoryQuery;
import com.tradeguide.domain.broker.BrokerOrderImportApprovalAssessment;
import com.tradeguide.domain.broker.BrokerOrderImportItem;
import com.tradeguide.domain.broker.BrokerOrderImportItemFilter;
import com.tradeguide.domain.broker.BrokerOrderImportReconciliationLine;
import com.tradeguide.domain.broker.BrokerOrderImportRun;
import com.tradeguide.domain.broker.BrokerOrderOverrideDecision;
import com.tradeguide.domain.broker.BrokerOrderRecord;
import com.tradeguide.domain.broker.BrokerProvider;
import com.tradeguide.domain.broker.PortfolioBrokerHoldingImport;
import com.tradeguide.domain.broker.PortfolioBrokerHoldingImportStatus;
import com.tradeguide.domain.portfolio.Portfolio;
import com.tradeguide.exception.BrokerConnectionUnavailableException;
import com.tradeguide.exception.BrokerOrderImportNotFoundException;
import com.tradeguide.exception.BrokerOrderImportUnprocessableException;
import com.tradeguide.exception.PortfolioNotFoundException;
import com.tradeguide.repository.broker.BrokerOrderImportItemRepository;
import com.tradeguide.repository.broker.BrokerOrderImportRunRepository;
import com.tradeguide.repository.broker.PortfolioBrokerHoldingImportRepository;
import com.tradeguide.repository.portfolio.PortfolioRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpStatusCodeException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 증권사 주문 이력을 읽어 와 <b>매매 원장을 건드리지 않고</b> 실행 단위로 저장한다.
 *
 * <p>이 서비스가 하지 않는 일을 먼저 적는다. 승인하지 않고, 취소하지 않고,
 * {@code TradeTransaction}을 만들거나 고치거나 지우지 않고, 값을 자동으로 보정하지 않고,
 * 주문을 내지 않는다. 여기서 만들어지는 것은 "증권사가 이렇게 보고했고 우리는 이렇게 읽었다"는
 * 기록 하나뿐이다.
 *
 * <p>그래서 이 단계는 안전하게 여러 번 실행할 수 있고, 같은 구간을 두 번 실행하는 것만으로
 * 제공자의 주문 식별자가 재조회에도 안정적인지 실측할 수 있다. 그 관측이 다음 단계
 * (원장 반영)를 열어도 되는지 판단하는 근거가 된다.
 */
@Service
public class BrokerOrderImportService {

    /**
     * 증권사는 <b>체결일이 아니라 주문일</b> 기준으로 조회한다. 미국 시장은 KST로 보면
     * 주문일과 체결일이 하루 어긋나는 일이 흔하다(KST 밤에 주문 → 새벽에 체결).
     * 그래서 사용자가 요청한 구간의 앞뒤를 이만큼 넓혀 조회한 뒤 체결일로 걸러 보여 준다.
     * 넓혀서 겹치는 주문은 주문 식별자 중복 제거가 흡수한다.
     */
    static final int BOUNDARY_PADDING_DAYS = 2;

    private static final int PAGE_SIZE = BrokerOrderHistoryQuery.MAX_PAGE_SIZE;

    /**
     * 한 번의 {@code createPreview} 요청이 받을 수 있는 최대 조회 폭이다. 양 끝 날짜를 모두
     * 포함한 달력일 수로 센다({@code orderedFrom == orderedTo}이면 1일). 토스증권이 문서화한
     * 실제 제한이 아니라 우리가 정하는 보수적 기본값이다 — 몇 년 치를 한 번에 요청하면
     * {@link #MAX_PAGES}에 걸려 실패할 때까지 증권사를 최대 그만큼 호출한 뒤에야 실패를 알게
     * 되는 낭비를 막는다.
     */
    static final long MAX_REQUESTED_RANGE_DAYS = 366;

    /**
     * 커서 순회 상한이다. 상한이 없으면 제공자가 커서를 잘못 돌려줄 때 순회가 끝나지 않는다.
     * 여기 걸리면 조용히 자르지 않고 실패로 알린다. 잘린 이력은 조용히 틀린 원장을 만든다.
     *
     * <p>서버 구간 분할 이후에도 이 예산은 전체 조회 하나가 공유한다. 창을 나눈다고 창
     * 개수만큼 곱하지 않는다 — 곱하면 하나의 동기 HTTP 요청 안에서 제공자를 수백 번 호출하게
     * 되어 요청 자체가 타임아웃될 위험이 커진다.
     */
    private static final int MAX_PAGES = 50;

    /**
     * 서버 구간 분할의 창 크기다. 30일 단위로 나눠 조회하면, 특정 구간만 초고밀도라도 나머지
     * 구간은 정상 커버되고 예산 소진 시 정확히 어디부터 다시 요청할지 안내할 수 있다.
     */
    private static final int CHUNK_WINDOW_DAYS = 30;

    /** 상관 id로 저장할 수 있는 형태. 예상 밖의 값이 오면 저장하지 않는다. */
    private static final Pattern SAFE_REQUEST_ID = Pattern.compile("[A-Za-z0-9._:-]{1,255}");
    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    /**
     * 실행 목록 정렬이다. 시작 시각만으로는 순서가 정해지지 않을 수 있으므로 id를 동점 기준으로
     * 함께 둔다. 정렬이 흔들리면 페이지 경계에서 실행이 중복되거나 빠진다.
     */
    private static final Sort RUN_SORT = Sort.by(
            Sort.Order.desc("startedAt"),
            Sort.Order.desc("id")
    );

    /**
     * 실행 항목 목록 정렬이다. 최신 주문부터 보여 준다. 주문 시각은 같은 값이 흔하므로
     * id를 동점 기준으로 함께 둔다. 정렬이 완전히 결정되지 않으면 페이지 경계에서 같은 항목이
     * 두 페이지에 나오거나 아예 빠진다.
     */
    private static final Sort ITEM_SORT = Sort.by(
            Sort.Order.desc("orderedAt"),
            Sort.Order.desc("id")
    );

    private static final String FAILURE_PROVIDER_UNAVAILABLE = "PROVIDER_UNAVAILABLE";
    private static final String FAILURE_CURSOR_REPEATED = "CURSOR_REPEATED";
    private static final String FAILURE_PAGE_LIMIT_EXCEEDED = "PAGE_LIMIT_EXCEEDED";

    private final PortfolioRepository portfolioRepository;
    private final BrokerOrderImportRunRepository brokerOrderImportRunRepository;
    private final BrokerOrderImportItemRepository brokerOrderImportItemRepository;
    private final PortfolioBrokerHoldingImportRepository portfolioBrokerHoldingImportRepository;
    private final BrokerOrderImportContextLoader brokerOrderImportContextLoader;
    private final BrokerProviderRegistry brokerProviderRegistry;
    private final BrokerOrderImportRunWriter brokerOrderImportRunWriter;
    private final BrokerDuplicateCallGuard brokerDuplicateCallGuard;
    private final Clock clock;

    public BrokerOrderImportService(
            PortfolioRepository portfolioRepository,
            BrokerOrderImportRunRepository brokerOrderImportRunRepository,
            BrokerOrderImportItemRepository brokerOrderImportItemRepository,
            PortfolioBrokerHoldingImportRepository portfolioBrokerHoldingImportRepository,
            BrokerOrderImportContextLoader brokerOrderImportContextLoader,
            BrokerProviderRegistry brokerProviderRegistry,
            BrokerOrderImportRunWriter brokerOrderImportRunWriter,
            BrokerDuplicateCallGuard brokerDuplicateCallGuard,
            Clock clock
    ) {
        this.portfolioRepository = portfolioRepository;
        this.brokerOrderImportRunRepository = brokerOrderImportRunRepository;
        this.brokerOrderImportItemRepository = brokerOrderImportItemRepository;
        this.portfolioBrokerHoldingImportRepository = portfolioBrokerHoldingImportRepository;
        this.brokerOrderImportContextLoader = brokerOrderImportContextLoader;
        this.brokerProviderRegistry = brokerProviderRegistry;
        this.brokerOrderImportRunWriter = brokerOrderImportRunWriter;
        this.brokerDuplicateCallGuard = brokerDuplicateCallGuard;
        this.clock = clock;
    }

    /**
     * 주문 이력을 조회해 실행 한 건으로 스테이징한다.
     *
     * <p>증권사 호출은 트랜잭션 밖에서 한다. 응답을 기다리는 동안 DB 트랜잭션을 붙잡고 있으면
     * 증권사가 느려질 때 커넥션 풀이 먼저 고갈된다.
     */
    public BrokerOrderImportRun createPreview(
            Long memberId,
            Long portfolioId,
            LocalDate orderedFrom,
            LocalDate orderedTo
    ) {
        requireRange(orderedFrom, orderedTo);

        brokerDuplicateCallGuard.acquire(portfolioId, BrokerCallType.ORDER_HISTORY_IMPORT);
        try {
            LocalDateTime lastStartedAt = brokerOrderImportRunRepository
                    .findFirstByPortfolio_IdOrderByStartedAtDesc(portfolioId)
                    .map(BrokerOrderImportRun::getStartedAt)
                    .orElse(null);
            brokerDuplicateCallGuard.checkCooldown(BrokerCallType.ORDER_HISTORY_IMPORT, lastStartedAt, clock);

            return stagePreview(memberId, portfolioId, orderedFrom, orderedTo);
        } finally {
            brokerDuplicateCallGuard.release(portfolioId, BrokerCallType.ORDER_HISTORY_IMPORT);
        }
    }

    private BrokerOrderImportRun stagePreview(
            Long memberId,
            Long portfolioId,
            LocalDate orderedFrom,
            LocalDate orderedTo
    ) {
        ImportContext context = brokerOrderImportContextLoader.load(memberId, portfolioId);
        LocalDate queriedFrom = orderedFrom.minusDays(BOUNDARY_PADDING_DAYS);
        LocalDate queriedTo = orderedTo.plusDays(BOUNDARY_PADDING_DAYS);
        LocalDateTime startedAt = LocalDateTime.now(clock);

        FetchResult fetched;
        try {
            fetched = fetchOrders(context, queriedFrom, queriedTo);
        } catch (BrokerConnectionUnavailableException exception) {
            recordFailure(context, memberId, portfolioId, orderedFrom, orderedTo, queriedFrom, queriedTo,
                    startedAt, FAILURE_PROVIDER_UNAVAILABLE, extractProviderRequestId(exception));
            throw exception;
        } catch (BrokerOrderImportUnprocessableException exception) {
            recordFailure(context, memberId, portfolioId, orderedFrom, orderedTo, queriedFrom, queriedTo,
                    startedAt, exception.failureCode(), null);
            throw exception;
        }

        return brokerOrderImportRunWriter.stage(new BrokerOrderImportRunWriter.StagingRequest(
                memberId,
                portfolioId,
                context.brokerConnectionId(),
                context.brokerAccountId(),
                orderedFrom,
                orderedTo,
                queriedFrom,
                queriedTo,
                startedAt,
                LocalDateTime.now(clock),
                fetched.records(),
                fetched.unsupportedMarketCount(),
                fetched.unsupportedCurrencyCount(),
                fetched.unknownEnumCount(),
                fetched.duplicateFetchCount(),
                fetched.openOrderCount(),
                fetched.coveredOrderedTo()
        ));
    }

    /**
     * 실행 이력을 최신순 한 페이지만 읽는다. 실행은 사용자가 조회할 때마다 쌓이므로 과거 전체를
     * 한 번에 돌려주지 않는다. 항목·대조 결과는 목록에 담지 않으며 상세 조회에서만 읽는다.
     */
    @Transactional(readOnly = true)
    public BrokerHistoryPage<BrokerOrderImportRun> getRuns(
            Long memberId,
            Long portfolioId,
            BrokerHistoryPageRequest pageRequest
    ) {
        requirePortfolio(memberId, portfolioId);

        Page<BrokerOrderImportRun> page = brokerOrderImportRunRepository.findAllByPortfolio_Id(
                portfolioId,
                PageRequest.of(pageRequest.page(), pageRequest.size(), RUN_SORT)
        );

        return new BrokerHistoryPage<>(
                page.getContent(),
                pageRequest.page(),
                pageRequest.size(),
                page.getTotalElements(),
                page.hasNext()
        );
    }

    /**
     * 실행 한 건을 승인 판정 집계·대조 결과와 함께 읽는다.
     *
     * <p>항목 배열은 담지 않는다. 실행 하나에 수천 건이 담길 수 있고, 항목은
     * {@code GET .../{runId}/items} 가 정본으로 나눠 준다. 대신 항목을 세어야만 알 수 있던
     * 승인 판정을 여기서 집계로 계산해 담는다. 그러지 않으면 화면이 한 페이지만 세어 승인
     * 여부를 정하게 되고, 그 판정은 조용히 틀린다.
     *
     * <p>대조 결과는 계속 담는다. 보유 종목 수에 비례하므로 무제한이 아니고, 대조를 별도
     * 호출로 나누면 화면이 그것을 건너뛸 수 있다.
     */
    @Transactional(readOnly = true)
    public RunDetail getRun(Long memberId, Long portfolioId, Long runId) {
        requirePortfolio(memberId, portfolioId);

        BrokerOrderImportRun run = brokerOrderImportRunRepository
                .findByPortfolio_IdAndId(portfolioId, runId)
                .orElseThrow(() -> new BrokerOrderImportNotFoundException(
                        "주문 이력 가져오기 실행을 찾을 수 없습니다."));

        return new RunDetail(run, assessApproval(portfolioId, run), run.getReconciliationLines());
    }

    /**
     * 승인 판정에 필요한 집계를 계산한다.
     *
     * <p>기준 시각과 원장 연계는 실행이 끝난 뒤에도 바뀌므로 실행에 저장할 수 없다. 조회할
     * 때마다 지금 값으로 다시 계산해야 화면이 보는 판정과 승인 시점의 판정이 어긋나지 않는다.
     * 배타 분류 건수는 반대로 실행에 이미 저장돼 있으므로 다시 세지 않는다.
     *
     * <p>세는 일은 두 번의 count 질의로 끝낸다. 항목을 메모리로 끌어와 세면 응답에서 항목을
     * 뺀 의미가 사라진다.
     */
    private BrokerOrderImportApprovalAssessment assessApproval(Long portfolioId, BrokerOrderImportRun run) {
        Instant baseline = resolveBaseline(portfolioId);
        Long runIdValue = run.getId();

        long eligibleCount = brokerOrderImportItemRepository.countEligibleItems(runIdValue, baseline);
        long alreadyLinkedCount = brokerOrderImportItemRepository.countEligibleItemsAlreadyLinked(
                runIdValue, run.getBrokerAccount().getId(), baseline);
        long overrideAllowedCount = brokerOrderImportItemRepository.countOverridesByDecision(
                runIdValue, BrokerOrderOverrideDecision.ALLOW_LEDGER_WRITE);
        long overrideKeptExcludedCount = brokerOrderImportItemRepository.countOverridesByDecision(
                runIdValue, BrokerOrderOverrideDecision.KEEP_EXCLUDED);

        return BrokerOrderImportApprovalAssessment.of(
                run.getStatus(),
                run.getReconciliationStatus(),
                run.getCounts(),
                Math.toIntExact(eligibleCount),
                Math.toIntExact(alreadyLinkedCount),
                Math.toIntExact(overrideAllowedCount),
                Math.toIntExact(overrideKeptExcludedCount),
                baseline == null ? null : LocalDateTime.ofInstant(baseline, clock.getZone()),
                run.isFullyCovered(),
                run.getCoverageAcknowledgedAt() != null
        );
    }

    /**
     * 활성 개시 잔고가 있으면 그중 가장 최근 승인 시각을 기준점으로 돌려준다. 없으면
     * {@code null}이며 아무 주문도 기준점 때문에 걸러지지 않는다.
     *
     * <p>{@link BrokerOrderImportApprovalWriter}의 같은 이름 메서드와 규칙을 맞춘다. 두 곳이
     * 갈라지면 화면이 안내한 반영 대상 건수와 실제로 반영되는 건수가 달라진다.
     */
    private Instant resolveBaseline(Long portfolioId) {
        return portfolioBrokerHoldingImportRepository
                .findFirstByPortfolio_IdAndStatusOrderByApprovedAtDesc(
                        portfolioId, PortfolioBrokerHoldingImportStatus.ACTIVE)
                .map(PortfolioBrokerHoldingImport::getApprovedAt)
                .map(approvedAt -> approvedAt.atZone(clock.getZone()).toInstant())
                .orElse(null);
    }

    /**
     * 실행 한 건의 주문 항목을 조건에 맞게 한 페이지만 읽는다.
     *
     * <p>실행 하나에 수천 건이 담길 수 있어 상세 응답 하나로 전부 내려보내면 응답이 수 MB에
     * 이른다. 그래서 항목은 이 조회로 나눠 읽는다. 거르기도 여기서 한다. 전부 내려보내고
     * 화면에서 거르면 페이지를 나눈 이유가 사라진다.
     *
     * <p>실행을 먼저 포트폴리오 범위로 찾는다. 항목 조회는 실행 id만 보므로, 여기서 소유권을
     * 확인하지 않으면 다른 회원의 실행 항목을 실행 id만 바꿔 읽을 수 있다.
     */
    @Transactional(readOnly = true)
    public BrokerHistoryPage<BrokerOrderImportItem> getRunItems(
            Long memberId,
            Long portfolioId,
            Long runId,
            BrokerOrderImportItemFilter filter,
            BrokerHistoryPageRequest pageRequest
    ) {
        requirePortfolio(memberId, portfolioId);

        BrokerOrderImportRun run = brokerOrderImportRunRepository
                .findByPortfolio_IdAndId(portfolioId, runId)
                .orElseThrow(() -> new BrokerOrderImportNotFoundException(
                        "주문 이력 가져오기 실행을 찾을 수 없습니다."));

        Page<BrokerOrderImportItem> page = brokerOrderImportItemRepository.findRunItems(
                run.getId(),
                filter.stagingStatus(),
                filter.ticker(),
                PageRequest.of(pageRequest.page(), pageRequest.size(), ITEM_SORT)
        );

        return new BrokerHistoryPage<>(
                page.getContent(),
                pageRequest.page(),
                pageRequest.size(),
                page.getTotalElements(),
                page.hasNext()
        );
    }

    /**
     * 조회 구간을 {@link #CHUNK_WINDOW_DAYS}일 단위 창으로 나눠 오래된 창부터 차례로 조회한다.
     * provider 호출 예산({@link #MAX_PAGES})은 창 전체가 공유하며, 창을 나눈다고 곱하지 않는다.
     *
     * <p>창 하나를 예산 안에 다 끝내지 못하면 그 창은 <b>절반만 가져온 채로 버린다.</b>
     * 절반만 가져온 창을 "이 날짜까지 커버함"으로 기록하면, 예산 소진 시점 이후의 주문이
     * 조용히 누락된 채 "커버했다"는 거짓 판정이 생긴다. 완전히 끝난 창만 결과에 반영한다.
     *
     * <p>가장 좁힌 창(30일)조차 예산 안에 끝내지 못하면(완전히 끝난 창이 0개) 자동 분할이
     * 더 해 줄 수 있는 일이 없으므로 오늘과 동일하게 실패로 알린다.
     *
     * <p>진행 중 주문은 원장 반영 대상이 아니지만 건수는 보고한다. "왜 어제 산 게 안 보이지"의
     * 답이 "아직 체결이 끝나지 않았습니다"인 경우가 있고, 그걸 알려 주지 않으면 사용자는
     * 데이터가 누락됐다고 생각한다. 진행 중 주문 집계는 창 분할과 무관하게 한 번만 조회한다.
     */
    private FetchResult fetchOrders(ImportContext context, LocalDate queriedFrom, LocalDate queriedTo) {
        BrokerOrderHistoryProvider provider =
                brokerProviderRegistry.requireOrderHistoryProvider(context.provider());

        List<DateRange> windows = buildChunkWindows(queriedFrom, queriedTo);
        Map<String, BrokerOrderRecord> recordsByOrderId = new LinkedHashMap<>();
        int unsupportedMarketCount = 0;
        int unsupportedCurrencyCount = 0;
        int unknownEnumCount = 0;
        int duplicateFetchCount = 0;
        int remainingPageBudget = MAX_PAGES;
        int completedWindows = 0;

        for (int i = 0; i < windows.size(); i++) {
            DateRange window = windows.get(i);
            // 창 경계의 padding은 내부 경계에만 적용한다. 전체 구간의 양 끝은 이미 호출자가
            // 주문일·체결일 어긋남을 흡수하려고 넓혀 뒀으므로(BOUNDARY_PADDING_DAYS), 여기서
            // 또 넓히면 이중 패딩이 된다.
            LocalDate windowFrom = i == 0 ? window.from() : window.from().minusDays(BOUNDARY_PADDING_DAYS);
            LocalDate windowTo = i == windows.size() - 1
                    ? window.to() : window.to().plusDays(BOUNDARY_PADDING_DAYS);

            WindowFetchResult windowResult =
                    fetchWindow(provider, context, windowFrom, windowTo, remainingPageBudget);
            remainingPageBudget -= windowResult.pagesUsed();

            if (!windowResult.completed()) {
                // 이 창은 절반만 가져온 상태로 버린다. 병합하지 않고 여기서 순회를 멈춘다.
                break;
            }

            for (BrokerOrderRecord record : windowResult.records()) {
                // 창 경계가 겹치므로 같은 주문이 두 번 올 수 있다. 먼저 본 값을 남긴다.
                if (recordsByOrderId.putIfAbsent(record.externalOrderId(), record) != null) {
                    duplicateFetchCount++;
                }
            }
            unsupportedMarketCount += windowResult.unsupportedMarketCount();
            unsupportedCurrencyCount += windowResult.unsupportedCurrencyCount();
            unknownEnumCount += windowResult.unknownEnumCount();
            completedWindows++;
        }

        if (completedWindows == 0) {
            throw new BrokerOrderImportUnprocessableException(
                    "가장 좁힌 " + CHUNK_WINDOW_DAYS + "일 창에서도 조회 구간의 주문이 너무 많습니다. "
                            + "기간을 나눠 다시 시도하세요.",
                    FAILURE_PAGE_LIMIT_EXCEEDED);
        }

        LocalDate coveredOrderedTo = completedWindows == windows.size()
                ? null
                : windows.get(completedWindows - 1).to();

        return new FetchResult(
                List.copyOf(recordsByOrderId.values()),
                unsupportedMarketCount,
                unsupportedCurrencyCount,
                unknownEnumCount,
                duplicateFetchCount,
                countOpenOrders(provider, context, queriedFrom, queriedTo),
                coveredOrderedTo
        );
    }

    /** {@code from}부터 {@code to}까지를 {@link #CHUNK_WINDOW_DAYS}일 단위로 자른다. 마지막 창은 {@code to}에서 자른다. */
    private List<DateRange> buildChunkWindows(LocalDate from, LocalDate to) {
        List<DateRange> windows = new ArrayList<>();
        LocalDate windowStart = from;
        while (!windowStart.isAfter(to)) {
            LocalDate windowEnd = windowStart.plusDays(CHUNK_WINDOW_DAYS - 1);
            if (windowEnd.isAfter(to)) {
                windowEnd = to;
            }
            windows.add(new DateRange(windowStart, windowEnd));
            windowStart = windowEnd.plusDays(1);
        }
        return windows;
    }

    /**
     * 창 하나를 커서로 순회한다. {@code pageBudget}을 다 쓰기 전에 끝나지 못하면 지금까지
     * 모은 값을 버리고 {@code completed=false}로 돌려준다 — 절반만 가져온 창은 호출자가
     * 병합하지 않는다.
     */
    private WindowFetchResult fetchWindow(
            BrokerOrderHistoryProvider provider,
            ImportContext context,
            LocalDate windowFrom,
            LocalDate windowTo,
            int pageBudget
    ) {
        List<BrokerOrderRecord> records = new ArrayList<>();
        int unsupportedMarketCount = 0;
        int unsupportedCurrencyCount = 0;
        int unknownEnumCount = 0;
        int pagesUsed = 0;

        BrokerOrderHistoryQuery query =
                BrokerOrderHistoryQuery.closedFirstPage(windowFrom, windowTo, PAGE_SIZE);
        List<String> seenCursors = new ArrayList<>();

        for (int page = 0; page < pageBudget; page++) {
            BrokerOrderHistoryPage fetchedPage = provider.fetchOrders(
                    context.credentials(), context.accountSequence(), query);
            pagesUsed++;

            records.addAll(fetchedPage.records());
            unsupportedMarketCount += fetchedPage.exclusions().unsupportedMarketCount();
            unsupportedCurrencyCount += fetchedPage.exclusions().unsupportedCurrencyCount();
            unknownEnumCount += fetchedPage.exclusions().unknownEnumCount();

            if (!fetchedPage.hasNext()) {
                return new WindowFetchResult(
                        true, pagesUsed, records,
                        unsupportedMarketCount, unsupportedCurrencyCount, unknownEnumCount);
            }

            if (seenCursors.contains(fetchedPage.nextCursor())) {
                // 같은 커서가 다시 왔다. 계속 돌면 무한 순회가 되고, 멈추면 이력이 잘린다.
                // 둘 다 나쁘므로 자르지 않고 실패로 알린다.
                throw new BrokerOrderImportUnprocessableException(
                        "증권사가 같은 조회 위치를 반복해 주문 이력을 끝까지 읽지 못했습니다. 기간을 좁혀 다시 시도하세요.",
                        FAILURE_CURSOR_REPEATED);
            }
            seenCursors.add(fetchedPage.nextCursor());
            query = query.withCursor(fetchedPage.nextCursor());
        }

        return new WindowFetchResult(false, pagesUsed, List.of(), 0, 0, 0);
    }

    /**
     * 진행 중 주문은 한 번에 전량 돌아오므로 호출 한 번으로 끝난다. 원장에 넣지 않고 건수만 쓴다.
     */
    private int countOpenOrders(
            BrokerOrderHistoryProvider provider,
            ImportContext context,
            LocalDate queriedFrom,
            LocalDate queriedTo
    ) {
        BrokerOrderHistoryPage openPage = provider.fetchOrders(
                context.credentials(),
                context.accountSequence(),
                BrokerOrderHistoryQuery.open(queriedFrom, queriedTo)
        );

        return openPage.exclusions().fetchedCount();
    }

    private void recordFailure(
            ImportContext context,
            Long memberId,
            Long portfolioId,
            LocalDate orderedFrom,
            LocalDate orderedTo,
            LocalDate queriedFrom,
            LocalDate queriedTo,
            LocalDateTime startedAt,
            String failureCode,
            String providerRequestId
    ) {
        brokerOrderImportRunWriter.recordFailure(new BrokerOrderImportRunWriter.FailureRequest(
                memberId,
                portfolioId,
                context.brokerConnectionId(),
                context.brokerAccountId(),
                orderedFrom,
                orderedTo,
                queriedFrom,
                queriedTo,
                startedAt,
                LocalDateTime.now(clock),
                failureCode,
                providerRequestId
        ));
    }

    /**
     * 실패 응답의 요청 상관 id를 꺼낸다. 비밀값도 개인정보도 아닌 불투명한 값이며,
     * 증권사에 장애를 문의할 때 쓸 수 있는 유일한 근거다.
     *
     * <p>그래도 응답 헤더는 증권사가 채우는 값이므로 그대로 믿지 않는다. 예상한 형태가 아니면
     * 저장하지 않는다. 응답 본문이나 오류 메시지는 어떤 경우에도 꺼내지 않는다.
     */
    private String extractProviderRequestId(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (!(cause instanceof HttpStatusCodeException httpException)
                    || httpException.getResponseHeaders() == null) {
                continue;
            }

            String requestId = httpException.getResponseHeaders().getFirst(REQUEST_ID_HEADER);
            if (requestId != null && SAFE_REQUEST_ID.matcher(requestId).matches()) {
                return requestId;
            }
        }
        return null;
    }

    private void requireRange(LocalDate orderedFrom, LocalDate orderedTo) {
        if (orderedFrom == null || orderedTo == null) {
            throw new IllegalArgumentException("주문 조회 시작일과 종료일은 필수입니다.");
        }
        if (orderedFrom.isAfter(orderedTo)) {
            throw new IllegalArgumentException("주문 조회 시작일은 종료일보다 늦을 수 없습니다.");
        }
        // 양 끝 날짜를 모두 포함해 센다. 같은 날이면 1일이다.
        long requestedDays = ChronoUnit.DAYS.between(orderedFrom, orderedTo) + 1;
        if (requestedDays > MAX_REQUESTED_RANGE_DAYS) {
            throw new IllegalArgumentException(
                    "조회 기간은 양 끝 날짜를 포함해 최대 " + MAX_REQUESTED_RANGE_DAYS + "일까지 요청할 수 있습니다. "
                            + "현재 " + requestedDays + "일을 요청했습니다.");
        }
    }

    private Portfolio requirePortfolio(Long memberId, Long portfolioId) {
        return portfolioRepository.findByMember_IdAndId(memberId, portfolioId)
                .orElseThrow(() -> new PortfolioNotFoundException("포트폴리오를 찾을 수 없습니다."));
    }

    /**
     * 조회 한 번에 필요한 값이다. 복호화된 자격 증명은 이 객체 밖으로 나가지 않고,
     * 저장·로그·응답 어디에도 담기지 않는다.
     */
    /**
     * 주문 이력 조회 한 번에 필요한 연결 정보다. 자격 증명은 제공자마다 항목 수가 다르므로
     * 고정 필드가 아니라 {@link BrokerCredentials}로 담는다. 계좌 일련번호는 자격 증명이 아니라
     * 계좌 식별자이므로 분리된 필드로 남긴다.
     *
     * <p>여기 담긴 평문은 어댑터 호출 인자로만 나간다. 저장·로그·응답 어디에도 담기지 않는다.
     */
    record ImportContext(
            Long brokerConnectionId,
            Long brokerAccountId,
            BrokerProvider provider,
            BrokerCredentials credentials,
            String accountSequence
    ) {
    }

    private record FetchResult(
            List<BrokerOrderRecord> records,
            int unsupportedMarketCount,
            int unsupportedCurrencyCount,
            int unknownEnumCount,
            int duplicateFetchCount,
            int openOrderCount,
            LocalDate coveredOrderedTo
    ) {
    }

    /** 서버 구간 분할의 창 하나. {@code from}·{@code to}는 padding을 더하기 전의 기본 경계다. */
    private record DateRange(LocalDate from, LocalDate to) {
    }

    /** 창 하나를 커서로 순회한 결과다. {@code completed=false}면 {@code records}는 항상 비어 있다. */
    private record WindowFetchResult(
            boolean completed,
            int pagesUsed,
            List<BrokerOrderRecord> records,
            int unsupportedMarketCount,
            int unsupportedCurrencyCount,
            int unknownEnumCount
    ) {
    }

    /**
     * 실행 한 건과 그 승인 판정 집계·대조 결과. 지연 로딩이 끝난 값만 담는다.
     *
     * <p>항목 배열은 여기 담지 않는다. 항목은 {@link #getRunItems} 가 정본으로 나눠 준다.
     */
    public record RunDetail(
            BrokerOrderImportRun run,
            BrokerOrderImportApprovalAssessment approval,
            List<BrokerOrderImportReconciliationLine> reconciliationLines
    ) {
    }
}
