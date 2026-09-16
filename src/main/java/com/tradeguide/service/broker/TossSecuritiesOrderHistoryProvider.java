package com.tradeguide.service.broker;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.tradeguide.domain.broker.BrokerCredentials;
import com.tradeguide.domain.broker.BrokerOrderExclusionCounts;
import com.tradeguide.domain.broker.BrokerOrderHistoryPage;
import com.tradeguide.domain.broker.BrokerOrderHistoryQuery;
import com.tradeguide.domain.broker.BrokerOrderLifecycle;
import com.tradeguide.domain.broker.BrokerOrderRecord;
import com.tradeguide.domain.broker.BrokerOrderSide;
import com.tradeguide.domain.broker.BrokerOrderStatusGroup;
import com.tradeguide.domain.broker.BrokerProvider;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.exception.BrokerConnectionUnavailableException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 토스증권 계좌의 주문 이력을 읽기 전용으로 조회한다.
 * 주문 제출·정정·취소 호출은 이 어댑터에 존재하지 않으며, 매매 원장도 건드리지 않는다.
 *
 * <p>공식 OpenAPI 명세 기준 계약이다. {@code GET /api/v1/orders}는 계좌를
 * {@code X-Tossinvest-Account} 헤더(accountSeq)로 받고, {@code status}는 <b>필수</b>이며
 * {@code OPEN}(진행 중) 또는 {@code CLOSED}(종료)라는 라이프사이클 <b>그룹</b>을 뜻한다.
 * 이 그룹 값은 개별 주문의 {@code status}와 값 체계가 다르다.
 *
 * <p>{@code CLOSED}만 {@code cursor}·{@code limit}이 적용되는 백필 경로다.
 * {@code OPEN}은 대기 주문을 전량 반환하고 커서·페이지 크기를 무시하므로,
 * 이 어댑터는 {@code OPEN} 요청에 두 파라미터를 보내지 않고 응답의 커서도 신뢰하지 않는다.
 *
 * <p>응답의 수량·금액은 모두 문자열 decimal이고, {@code orderedAt}·{@code filledAt}은
 * KST 오프셋을 포함한 ISO 8601 date-time이다. 명세는 {@code status}·{@code orderType}·
 * {@code timeInForce}·{@code currency}에 대해 클라이언트가 모르는 값을 허용하도록 요구하므로,
 * 이 어댑터는 미지의 값에서 파싱을 실패시키지 않고 건수로 보고한다.
 *
 * <p>주문 응답에는 {@code marketCountry}가 없다. 보유 종목 어댑터의 시장 매핑을 재사용할 수 없어
 * {@code currency}와 종목 코드 표기로 추론하며, 애매한 건은 통과시키지 않고 제외 + 건수 보고로 처리한다.
 * US(USD, 영문 티커)와 KR(KRW, KRX 6자리 숫자 종목코드) 두 조합만 지원하고, 통화와 종목 코드
 * 표기가 서로 다른 시장을 가리키면(예: KRW인데 영문 티커) 애매한 것으로 보고 제외한다.
 */
@Component
public class TossSecuritiesOrderHistoryProvider implements BrokerOrderHistoryProvider {

    private static final String ACCOUNT_HEADER = "X-Tossinvest-Account";
    private static final String ORDERS_PATH = "/api/v1/orders";
    private static final String INVALID_RESPONSE_MESSAGE = "토스증권 주문 이력 응답이 올바르지 않습니다.";
    private static final String FETCH_FAILED_MESSAGE = "토스증권 주문 이력 조회에 실패했습니다.";

    private static final Set<String> SUPPORTED_CURRENCIES = Set.of("USD", "KRW");
    private static final Set<String> KNOWN_CURRENCIES = Set.of("KRW", "USD");
    private static final Set<String> KNOWN_ORDER_TYPES = Set.of("LIMIT", "MARKET");
    private static final Set<String> KNOWN_TIME_IN_FORCES = Set.of("DAY", "CLS", "OPG");

    /** 명세가 정의한 주문 상태 10종. 여기 없는 값은 미지의 enum으로 처리한다. */
    private static final Set<String> OPEN_STATUSES =
            Set.of("PENDING", "PENDING_CANCEL", "PENDING_REPLACE");
    private static final Set<String> TERMINAL_STATUSES =
            Set.of("FILLED", "CANCELED", "REJECTED", "REPLACED");
    private static final Set<String> CONTROL_STATUSES =
            Set.of("CANCEL_REJECTED", "REPLACE_REJECTED");
    private static final String PARTIAL_FILLED_STATUS = "PARTIAL_FILLED";

    /** US 티커 표기(영문 시작, 영문·점·하이픈). KRX 6자리 숫자와 구분하기 위한 최소 규칙이다. */
    private static final Pattern US_TICKER = Pattern.compile("[A-Za-z][A-Za-z.\\-]*");
    /** KRX 종목 코드 표기(숫자 6자리 고정). */
    private static final Pattern KRX_TICKER = Pattern.compile("\\d{6}");

    private final RestClient restClient;
    private final TossSecuritiesAccessTokenIssuer accessTokenIssuer;

    public TossSecuritiesOrderHistoryProvider(
            @Qualifier("brokerRestClientBuilder") RestClient.Builder builder,
            @Value("${toss-securities.base-url:https://openapi.tossinvest.com}") String baseUrl,
            TossSecuritiesAccessTokenIssuer accessTokenIssuer
    ) {
        this.restClient = builder.baseUrl(baseUrl).build();
        this.accessTokenIssuer = accessTokenIssuer;
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
        if (accountSequence == null || accountSequence.isBlank()) {
            throw new IllegalArgumentException("증권사 계좌 일련번호가 필요합니다.");
        }
        if (query == null) {
            throw new IllegalArgumentException("주문 이력 조회 조건이 필요합니다.");
        }

        String accessToken = accessTokenIssuer.issueAccessToken(credentials);

        OrdersResponse response;
        try {
            response = restClient.get()
                    .uri(uriBuilder -> {
                        uriBuilder.path(ORDERS_PATH).queryParam("status", query.statusGroup().name());
                        if (query.orderedFrom() != null) {
                            uriBuilder.queryParam("from", query.orderedFrom().toString());
                        }
                        if (query.orderedTo() != null) {
                            uriBuilder.queryParam("to", query.orderedTo().toString());
                        }
                        // OPEN 그룹은 커서와 페이지 크기를 무시하고 전량 반환하므로 보내지 않는다.
                        if (query.statusGroup() == BrokerOrderStatusGroup.CLOSED) {
                            if (query.cursor() != null) {
                                uriBuilder.queryParam("cursor", query.cursor());
                            }
                            uriBuilder.queryParam("limit", query.pageSize());
                        }
                        return uriBuilder.build();
                    })
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .header(ACCOUNT_HEADER, accountSequence)
                    .retrieve()
                    .body(OrdersResponse.class);
        } catch (HttpClientErrorException.Unauthorized exception) {
            // 토큰이 이미 무효화된 상태이므로 캐시를 버려 다음 호출이 새로 발급하게 한다.
            accessTokenIssuer.invalidate(credentials);
            throw new BrokerConnectionUnavailableException(FETCH_FAILED_MESSAGE, exception);
        } catch (RestClientException exception) {
            throw new BrokerConnectionUnavailableException(FETCH_FAILED_MESSAGE, exception);
        }

        if (response == null || response.result() == null || response.result().orders() == null) {
            throw new BrokerConnectionUnavailableException(INVALID_RESPONSE_MESSAGE);
        }

        return toPage(response.result(), query.statusGroup());
    }

    private BrokerOrderHistoryPage toPage(PaginatedOrders result, BrokerOrderStatusGroup statusGroup) {
        List<BrokerOrderRecord> records = new ArrayList<>();
        int fetchedCount = 0;
        int unsupportedMarketCount = 0;
        int unsupportedCurrencyCount = 0;
        int unknownEnumCount = 0;

        for (OrderItem item : result.orders()) {
            if (item == null) {
                throw new BrokerConnectionUnavailableException(INVALID_RESPONSE_MESSAGE);
            }
            fetchedCount++;

            String currency = normalize(item.currency());
            String status = normalize(item.status());
            String orderType = normalize(item.orderType());
            String timeInForce = normalize(item.timeInForce());
            if (isUnknownEnumValue(status, orderType, timeInForce, currency)) {
                unknownEnumCount++;
            }

            if (!SUPPORTED_CURRENCIES.contains(currency)) {
                unsupportedCurrencyCount++;
                continue;
            }

            Market market = toMarket(requireText(item.symbol()));
            if (market == null || !market.getCurrency().name().equals(currency)) {
                // 종목 코드 표기로 추론한 시장이 없거나, 그 시장의 통화가 응답의 통화와
                // 다르면(예: KRW인데 영문 티커) 애매한 조합이므로 통과시키지 않는다.
                unsupportedMarketCount++;
                continue;
            }

            records.add(toRecord(item, status, orderType, timeInForce, currency, market));
        }

        BrokerOrderExclusionCounts exclusions = new BrokerOrderExclusionCounts(
                fetchedCount, unsupportedMarketCount, unsupportedCurrencyCount, unknownEnumCount);

        // OPEN 응답은 커서를 채우지 않는 것이 명세지만, 값이 오더라도 순회 근거로 쓰지 않는다.
        if (statusGroup == BrokerOrderStatusGroup.OPEN) {
            return BrokerOrderHistoryPage.lastPage(records, exclusions);
        }

        return new BrokerOrderHistoryPage(
                records, result.nextCursor(), Boolean.TRUE.equals(result.hasNext()), exclusions);
    }

    private BrokerOrderRecord toRecord(
            OrderItem item,
            String status,
            String orderType,
            String timeInForce,
            String currency,
            Market market
    ) {
        OrderExecution execution = item.execution();
        if (execution == null) {
            throw new BrokerConnectionUnavailableException(INVALID_RESPONSE_MESSAGE);
        }

        BigDecimal filledQuantity = requiredDecimal(execution.filledQuantity());

        return new BrokerOrderRecord(
                requireText(item.orderId()),
                market,
                requireText(item.symbol()),
                toSide(item.side()),
                toLifecycle(status, filledQuantity),
                requireText(item.status()),
                item.orderType(),
                item.timeInForce(),
                currency,
                requiredDecimal(item.quantity()),
                filledQuantity,
                optionalDecimal(execution.averageFilledPrice()),
                optionalDecimal(execution.filledAmount()),
                optionalDecimal(execution.commission()),
                optionalDecimal(execution.tax()),
                requiredInstant(item.orderedAt()),
                optionalInstant(execution.filledAt()),
                optionalDate(execution.settlementDate())
        );
    }

    /**
     * 제공자 상태 코드를 중립 라이프사이클로 판정한다.
     *
     * <p>{@code PARTIAL_FILLED}는 {@code OPEN}·{@code CLOSED} 양쪽 그룹에 나타나고,
     * 더 이상 체결되지 않는다는 신호가 명세에 없다. 그래서 어느 그룹에서 왔든 보류로 판정한다.
     *
     * <p>{@code CANCELED}·{@code REJECTED}·{@code REPLACED}는 체결분이 있으면 그만큼 실재하는
     * 거래이므로 상태만 보고 버리지 않는다. 반대로 {@code CANCEL_REJECTED}·{@code REPLACE_REJECTED}는
     * 별도 레코드로 생기는 제어 기록이라 체결분 유무와 무관하게 분리한다.
     */
    private BrokerOrderLifecycle toLifecycle(String status, BigDecimal filledQuantity) {
        boolean hasFill = filledQuantity.signum() > 0;

        if (status == null) {
            return BrokerOrderLifecycle.UNKNOWN;
        }
        if (CONTROL_STATUSES.contains(status)) {
            return BrokerOrderLifecycle.CONTROL_RECORD;
        }
        if (PARTIAL_FILLED_STATUS.equals(status)) {
            return BrokerOrderLifecycle.PARTIALLY_FILLED_OPEN;
        }
        if (OPEN_STATUSES.contains(status)) {
            // 진행 중인데 체결분이 있으면 부분 체결로 읽는다. 상태 이름만 믿고 체결분을 지우지 않는다.
            return hasFill ? BrokerOrderLifecycle.PARTIALLY_FILLED_OPEN : BrokerOrderLifecycle.NOT_FILLED;
        }
        if (TERMINAL_STATUSES.contains(status)) {
            return hasFill ? BrokerOrderLifecycle.TERMINAL_WITH_FILL : BrokerOrderLifecycle.TERMINAL_WITHOUT_FILL;
        }
        return BrokerOrderLifecycle.UNKNOWN;
    }

    private boolean isUnknownEnumValue(String status, String orderType, String timeInForce, String currency) {
        return !isKnown(status, OPEN_STATUSES, TERMINAL_STATUSES, CONTROL_STATUSES, Set.of(PARTIAL_FILLED_STATUS))
                || !isKnown(orderType, KNOWN_ORDER_TYPES)
                || !isKnown(timeInForce, KNOWN_TIME_IN_FORCES)
                || !isKnown(currency, KNOWN_CURRENCIES);
    }

    /** 값이 없는 것은 미지의 값이 아니다. 선택 필드가 비어 있을 뿐이므로 신호로 세지 않는다. */
    @SafeVarargs
    private boolean isKnown(String value, Set<String>... knownValueSets) {
        if (value == null) {
            return true;
        }
        for (Set<String> knownValues : knownValueSets) {
            if (knownValues.contains(value)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 주문 응답에는 시장 필드가 없어 통화와 종목 코드 표기로 추론한다.
     * 명세가 보장하는 매핑이 아니므로 애매한 값은 US로 통과시키지 않는다.
     */
    private Market toMarket(String symbol) {
        if (US_TICKER.matcher(symbol).matches()) {
            return Market.US;
        }
        if (KRX_TICKER.matcher(symbol).matches()) {
            return Market.KR;
        }
        return null;
    }

    private BrokerOrderSide toSide(String side) {
        // side는 명세가 미지의 값을 허용하지 않는 필수 필드다. 방향을 모르면 거래로 읽을 수 없다.
        String normalized = normalize(side);
        if ("BUY".equals(normalized)) {
            return BrokerOrderSide.BUY;
        }
        if ("SELL".equals(normalized)) {
            return BrokerOrderSide.SELL;
        }
        throw new BrokerConnectionUnavailableException(INVALID_RESPONSE_MESSAGE);
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String requireText(String value) {
        if (value == null || value.isBlank()) {
            throw new BrokerConnectionUnavailableException(INVALID_RESPONSE_MESSAGE);
        }
        return value.trim();
    }

    private BigDecimal requiredDecimal(String value) {
        BigDecimal decimal = optionalDecimal(value);
        if (decimal == null) {
            throw new BrokerConnectionUnavailableException(INVALID_RESPONSE_MESSAGE);
        }
        return decimal;
    }

    private BigDecimal optionalDecimal(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException exception) {
            throw new BrokerConnectionUnavailableException(INVALID_RESPONSE_MESSAGE, exception);
        }
    }

    private Instant requiredInstant(String value) {
        Instant instant = optionalInstant(value);
        if (instant == null) {
            throw new BrokerConnectionUnavailableException(INVALID_RESPONSE_MESSAGE);
        }
        return instant;
    }

    private Instant optionalInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return OffsetDateTime.parse(value.trim()).toInstant();
        } catch (DateTimeParseException exception) {
            throw new BrokerConnectionUnavailableException(INVALID_RESPONSE_MESSAGE, exception);
        }
    }

    private LocalDate optionalDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException exception) {
            throw new BrokerConnectionUnavailableException(INVALID_RESPONSE_MESSAGE, exception);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OrdersResponse(PaginatedOrders result) {}

    /** 명세의 {@code PaginatedOrderResponse}에서 이 어댑터가 사용하는 필드만 매핑한다. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record PaginatedOrders(List<OrderItem> orders, String nextCursor, Boolean hasNext) {}

    /** 명세의 {@code Order}에서 이 어댑터가 사용하는 필드만 매핑한다. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OrderItem(
            String orderId,
            String symbol,
            String side,
            String orderType,
            String timeInForce,
            String status,
            String quantity,
            String currency,
            String orderedAt,
            OrderExecution execution
    ) {}

    /** 명세의 {@code OrderExecution}은 필드가 모두 존재하되 값은 비어 있을 수 있다. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OrderExecution(
            String filledQuantity,
            String averageFilledPrice,
            String filledAmount,
            String commission,
            String tax,
            String filledAt,
            String settlementDate
    ) {}
}
