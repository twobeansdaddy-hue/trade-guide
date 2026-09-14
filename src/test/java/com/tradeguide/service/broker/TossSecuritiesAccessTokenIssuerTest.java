package com.tradeguide.service.broker;

import static com.tradeguide.domain.broker.BrokerCredentialsFixture.tossCredentials;
import com.tradeguide.exception.BrokerConnectionUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 토스증권 공식 계약(client 당 유효한 access token 1개, 재발급 시 이전 토큰 즉시 무효화)을 전제로
 * 토큰 재사용·만료 재발급·자격 증명 격리·동시 호출 단일 발급을 검증한다.
 * 실제 토스증권 API는 호출하지 않고 가짜 토큰 엔드포인트를 사용한다.
 */
class TossSecuritiesAccessTokenIssuerTest {

    /** 운영 코드가 사용하는 조기 만료 버퍼와 같은 값이다. */
    private static final Duration EARLY_EXPIRY_BUFFER = Duration.ofSeconds(60);
    private static final long EXPIRES_IN_SECONDS = 3600;

    private final TokenEndpointStub tokenEndpoint = new TokenEndpointStub();
    private final MutableClock clock = new MutableClock(Instant.parse("2026-09-08T00:00:00Z"));
    private final TossSecuritiesAccessTokenIssuer issuer = new TossSecuritiesAccessTokenIssuer(
            RestClient.builder().requestFactory(tokenEndpoint),
            "https://openapi.example.test",
            clock
    );

    @Test
    void reusesIssuedTokenWhileItIsStillValid() {
        String first = issuer.issueAccessToken(tossCredentials("client-id", "client-secret"));
        String second = issuer.issueAccessToken(tossCredentials("client-id", "client-secret"));

        assertThat(first).isEqualTo("access-token-1");
        assertThat(second).isEqualTo(first);
        assertThat(tokenEndpoint.issuedCount()).isEqualTo(1);
    }

    @Test
    void keepsReusingTokenUntilEarlyExpiryBufferIsReached() {
        issuer.issueAccessToken(tossCredentials("client-id", "client-secret"));

        clock.advance(Duration.ofSeconds(EXPIRES_IN_SECONDS).minus(EARLY_EXPIRY_BUFFER).minusSeconds(1));

        assertThat(issuer.issueAccessToken(tossCredentials("client-id", "client-secret"))).isEqualTo("access-token-1");
        assertThat(tokenEndpoint.issuedCount()).isEqualTo(1);
    }

    @Test
    void reissuesTokenOnceEarlyExpiryBufferIsReached() {
        issuer.issueAccessToken(tossCredentials("client-id", "client-secret"));

        clock.advance(Duration.ofSeconds(EXPIRES_IN_SECONDS).minus(EARLY_EXPIRY_BUFFER));

        assertThat(issuer.issueAccessToken(tossCredentials("client-id", "client-secret"))).isEqualTo("access-token-2");
        assertThat(tokenEndpoint.issuedCount()).isEqualTo(2);
    }

    @Test
    void keepsTokensSeparatePerCredential() {
        String first = issuer.issueAccessToken(tossCredentials("client-id-a", "client-secret-a"));
        String second = issuer.issueAccessToken(tossCredentials("client-id-b", "client-secret-b"));

        assertThat(first).isEqualTo("access-token-1");
        assertThat(second).isEqualTo("access-token-2");
        assertThat(issuer.issueAccessToken(tossCredentials("client-id-a", "client-secret-a"))).isEqualTo(first);
        assertThat(issuer.issueAccessToken(tossCredentials("client-id-b", "client-secret-b"))).isEqualTo(second);
        assertThat(tokenEndpoint.issuedCount()).isEqualTo(2);
    }

    @Test
    void treatsSameClientIdWithDifferentSecretAsDifferentCredential() {
        String first = issuer.issueAccessToken(tossCredentials("client-id", "client-secret-old"));
        String second = issuer.issueAccessToken(tossCredentials("client-id", "client-secret-new"));

        assertThat(second).isNotEqualTo(first);
        assertThat(tokenEndpoint.issuedCount()).isEqualTo(2);
    }

    @Test
    void issuesOnlyOnceWhenConcurrentCallsShareCredentials() throws Exception {
        int callers = 4;
        CyclicBarrier startTogether = new CyclicBarrier(callers);
        CountDownLatch finished = new CountDownLatch(callers);
        Set<String> tokens = ConcurrentHashMap.newKeySet();
        // 첫 발급이 진행 중인 동안 나머지 호출이 겹치도록 응답을 잠시 붙잡는다.
        tokenEndpoint.holdDuringResponse(Duration.ofMillis(200));

        ExecutorService executor = Executors.newFixedThreadPool(callers);
        try {
            for (int i = 0; i < callers; i++) {
                executor.execute(() -> {
                    try {
                        startTogether.await(5, TimeUnit.SECONDS);
                        tokens.add(issuer.issueAccessToken(tossCredentials("client-id", "client-secret")));
                    } catch (Exception exception) {
                        throw new IllegalStateException(exception);
                    } finally {
                        finished.countDown();
                    }
                });
            }

            assertThat(finished.await(15, TimeUnit.SECONDS)).isTrue();
        } finally {
            executor.shutdownNow();
        }

        assertThat(tokens).containsExactly("access-token-1");
        assertThat(tokenEndpoint.issuedCount()).isEqualTo(1);
    }

    @Test
    void issuesEveryTimeWhenLifetimeIsShorterThanEarlyExpiryBuffer() {
        tokenEndpoint.respondWithExpiresIn(EARLY_EXPIRY_BUFFER.toSeconds());

        issuer.issueAccessToken(tossCredentials("client-id", "client-secret"));
        issuer.issueAccessToken(tossCredentials("client-id", "client-secret"));

        assertThat(tokenEndpoint.issuedCount()).isEqualTo(2);
    }

    @Test
    void issuesEveryTimeWhenLifetimeIsMissing() {
        tokenEndpoint.respondWithoutExpiresIn();

        issuer.issueAccessToken(tossCredentials("client-id", "client-secret"));
        issuer.issueAccessToken(tossCredentials("client-id", "client-secret"));

        assertThat(tokenEndpoint.issuedCount()).isEqualTo(2);
    }

    @Test
    void issuesNewTokenAfterInvalidation() {
        assertThat(issuer.issueAccessToken(tossCredentials("client-id", "client-secret"))).isEqualTo("access-token-1");

        issuer.invalidate(tossCredentials("client-id", "client-secret"));

        assertThat(issuer.issueAccessToken(tossCredentials("client-id", "client-secret"))).isEqualTo("access-token-2");
        assertThat(tokenEndpoint.issuedCount()).isEqualTo(2);
    }

    @Test
    void doesNotCacheFailedIssuance() {
        tokenEndpoint.respondWithStatus(HttpStatus.SERVICE_UNAVAILABLE);

        assertThatThrownBy(() -> issuer.issueAccessToken(tossCredentials("client-id", "client-secret")))
                .isInstanceOf(BrokerConnectionUnavailableException.class)
                .hasMessage("토스증권 인증에 실패했습니다.");

        tokenEndpoint.respondWithStatus(HttpStatus.OK);

        assertThat(issuer.issueAccessToken(tossCredentials("client-id", "client-secret"))).isEqualTo("access-token-2");
        assertThat(issuer.issueAccessToken(tossCredentials("client-id", "client-secret"))).isEqualTo("access-token-2");
        assertThat(tokenEndpoint.issuedCount()).isEqualTo(2);
    }

    @Test
    void failsWhenTokenResponseHasNoAccessToken() {
        tokenEndpoint.respondWithBody("{\"token_type\": \"Bearer\", \"expires_in\": 3600}");

        assertThatThrownBy(() -> issuer.issueAccessToken(tossCredentials("client-id", "client-secret")))
                .isInstanceOf(BrokerConnectionUnavailableException.class)
                .hasMessage("토스증권 인증 응답이 올바르지 않습니다.");
    }

    @Test
    void requestsClientCredentialsGrant() {
        issuer.issueAccessToken(tossCredentials("client-id", "client-secret"));

        assertThat(tokenEndpoint.requestedUris()).containsExactly(URI.create("https://openapi.example.test/oauth2/token"));
        assertThat(tokenEndpoint.lastRequestBody()).contains("grant_type=client_credentials");
    }

    /** 발급 요청 수와 응답을 통제하는 가짜 토큰 엔드포인트다. */
    private static final class TokenEndpointStub implements ClientHttpRequestFactory {

        private final AtomicInteger issuedCount = new AtomicInteger();
        private final List<URI> requestedUris = java.util.Collections.synchronizedList(new java.util.ArrayList<>());

        private volatile HttpStatusCode status = HttpStatus.OK;
        private volatile Long expiresInSeconds = EXPIRES_IN_SECONDS;
        private volatile String fixedBody;
        private volatile Duration holdDuringResponse = Duration.ZERO;
        private volatile String lastRequestBody = "";

        @Override
        public ClientHttpRequest createRequest(URI uri, HttpMethod httpMethod) {
            requestedUris.add(uri);
            return new MockClientHttpRequest(httpMethod, uri) {
                @Override
                protected ClientHttpResponse executeInternal() {
                    lastRequestBody = getBodyAsString();
                    int sequence = issuedCount.incrementAndGet();
                    sleep(holdDuringResponse);
                    MockClientHttpResponse response =
                            new MockClientHttpResponse(body(sequence).getBytes(StandardCharsets.UTF_8), status);
                    response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                    return response;
                }
            };
        }

        private String body(int sequence) {
            if (fixedBody != null) {
                return fixedBody;
            }
            String expiresIn = expiresInSeconds == null ? "" : ", \"expires_in\": " + expiresInSeconds;
            return "{\"access_token\": \"access-token-%d\", \"token_type\": \"Bearer\"%s}".formatted(sequence, expiresIn);
        }

        private int issuedCount() {
            return issuedCount.get();
        }

        private List<URI> requestedUris() {
            return List.copyOf(requestedUris);
        }

        private String lastRequestBody() {
            return lastRequestBody;
        }

        private void respondWithStatus(HttpStatusCode status) {
            this.status = status;
        }

        private void respondWithExpiresIn(long seconds) {
            this.expiresInSeconds = seconds;
        }

        private void respondWithoutExpiresIn() {
            this.expiresInSeconds = null;
        }

        private void respondWithBody(String body) {
            this.fixedBody = body;
        }

        private void holdDuringResponse(Duration duration) {
            this.holdDuringResponse = duration;
        }

        private static void sleep(Duration duration) {
            if (duration.isZero() || duration.isNegative()) {
                return;
            }
            try {
                Thread.sleep(duration.toMillis());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /** 만료 검증을 위해 앞으로만 움직이는 테스트용 시계다. */
    private static final class MutableClock extends Clock {

        private volatile Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
