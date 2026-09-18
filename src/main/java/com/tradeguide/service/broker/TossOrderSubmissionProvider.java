package com.tradeguide.service.broker;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.tradeguide.domain.broker.BrokerCredentials;
import com.tradeguide.domain.broker.BrokerProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.UUID;

/**
 * 토스증권에 실제로 시장가 주문을 제출한다.
 *
 * <p>공식 OpenAPI 명세 기준 계약이다({@code POST /api/v1/orders}, 2026-09-18
 * `https://openapi.tossinvest.com/openapi-docs/latest/openapi.json` 직접 조회로
 * 확정). 요청은 수량 기반(`quantity`)만 쓰고 금액 기반(`orderAmount`)은 쓰지
 * 않는다 - {@link BrokerOrderSubmissionRequest}가 이미 수량으로 설계됐다.
 * {@code orderType}은 항상 {@code MARKET}이고 {@code price}는 절대 채우지 않는다
 * (지정가는 이 어댑터의 범위 밖).
 *
 * <p>{@code confirmHighValueOrder}는 항상 기본값(false)이다 - 1억원 이상 주문을
 * 자동으로 확인 처리하지 않는다. 거부되면 그 사유 코드(`confirm-high-value
 * -required`)를 그대로 실패로 남겨 사람이 검토하게 한다.
 *
 * <p>{@link TossSecuritiesAccessTokenIssuer}(기존 인증 경로)를 그대로 재사용한다.
 * 401이면 {@link TossSecuritiesOrderHistoryProvider}와 동일하게 토큰 캐시를
 * 무효화한다.
 *
 * <p>모든 실패(4xx/5xx/네트워크 오류/응답 파싱 실패)는 예외를 던지지 않고
 * {@link BrokerOrderSubmissionResult#failed(String)}로 수렴한다 - 호출부
 * ({@link BrokerOrderExecutionService})가 이 결과로 감사 기록을 {@code FAILED}
 * 상태로 완결짓기 때문에, 여기서 예외가 새면 그 기록이 {@code PENDING}으로
 * 고아처럼 남는다. 실패 사유는 토스 {@code error.code}(짧은 flat 식별자)만
 * 담는다 - {@code message}(사람이 읽는 문장, 민감정보 가능성)나 {@code data}는
 * 저장하지 않는다.
 */
@Component
public class TossOrderSubmissionProvider implements BrokerOrderSubmissionProvider {

    private static final Logger log = LoggerFactory.getLogger(TossOrderSubmissionProvider.class);

    private static final String ORDERS_PATH = "/api/v1/orders";
    private static final String ACCOUNT_HEADER = "X-Tossinvest-Account";
    private static final String MARKET_ORDER_TYPE = "MARKET";

    private static final String UNAUTHORIZED_REASON_CODE = "UNAUTHORIZED";
    private static final String INVALID_RESPONSE_REASON_CODE = "INVALID_RESPONSE";
    private static final String SUBMISSION_FAILED_REASON_CODE = "SUBMISSION_FAILED";

    private final RestClient restClient;
    private final TossSecuritiesAccessTokenIssuer accessTokenIssuer;

    public TossOrderSubmissionProvider(
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
    public BrokerOrderSubmissionResult submit(BrokerCredentials credentials, BrokerOrderSubmissionRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("주문 요청이 필요합니다.");
        }

        String accessToken = accessTokenIssuer.issueAccessToken(credentials);

        // 재시도 안전성을 완전히 보장하지 않는다 - 이 슬라이스에는 재시도 로직이 없어
        // 호출마다 새 키를 쓴다(멱등성 키는 10분간만 유효). 향후 재시도를 추가한다면
        // 호출자가 안정적인 clientOrderId를 넘기도록 다시 설계해야 한다.
        OrderCreateRequestBody body = new OrderCreateRequestBody(
                UUID.randomUUID().toString(),
                request.ticker(),
                request.side().name(),
                MARKET_ORDER_TYPE,
                request.quantity().stripTrailingZeros().toPlainString()
        );

        try {
            OrderCreateResponse response = restClient.post()
                    .uri(ORDERS_PATH)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .header(ACCOUNT_HEADER, request.accountSequence())
                    .body(body)
                    .retrieve()
                    .body(OrderCreateResponse.class);

            if (response == null || response.result() == null || response.result().orderId() == null) {
                return BrokerOrderSubmissionResult.failed(INVALID_RESPONSE_REASON_CODE);
            }
            return BrokerOrderSubmissionResult.submitted(response.result().orderId());
        } catch (HttpClientErrorException.Unauthorized exception) {
            // 토큰이 이미 무효화된 상태이므로 캐시를 버려 다음 호출이 새로 발급하게 한다.
            accessTokenIssuer.invalidate(credentials);
            return BrokerOrderSubmissionResult.failed(UNAUTHORIZED_REASON_CODE);
        } catch (HttpClientErrorException exception) {
            return BrokerOrderSubmissionResult.failed(extractErrorCode(exception));
        } catch (RestClientException exception) {
            log.warn("토스증권 주문 제출 호출이 실패했습니다.", exception);
            return BrokerOrderSubmissionResult.failed(SUBMISSION_FAILED_REASON_CODE);
        }
    }

    /**
     * 실패 응답 본문에서 {@code error.code}만 꺼낸다. 파싱에 실패해도 예외를 던지지
     * 않고 일반 실패 코드로 대체한다 - 오류 처리 경로 자체가 또 실패하게 두지 않는다.
     */
    private String extractErrorCode(HttpClientErrorException exception) {
        try {
            ErrorResponse errorResponse = exception.getResponseBodyAs(ErrorResponse.class);
            if (errorResponse != null && errorResponse.error() != null && errorResponse.error().code() != null) {
                return errorResponse.error().code();
            }
        } catch (RuntimeException parseException) {
            log.warn("토스증권 오류 응답을 해석하지 못했습니다.", parseException);
        }
        return SUBMISSION_FAILED_REASON_CODE;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OrderCreateRequestBody(
            String clientOrderId,
            String symbol,
            String side,
            String orderType,
            String quantity
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OrderCreateResponse(OrderResult result) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OrderResult(String orderId, String clientOrderId) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ErrorResponse(ApiError error) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ApiError(String requestId, String code, String message) {}
}
