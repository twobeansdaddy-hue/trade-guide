package com.tradeguide.service.broker;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.tradeguide.domain.broker.BrokerCredentials;
import com.tradeguide.exception.BrokerConnectionUnavailableException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 토스증권 client-credentials 액세스 토큰을 발급하고, 유효 구간 동안 메모리에서만 재사용한다.
 *
 * <p>공식 명세 기준으로 <b>client 당 유효한 access token 은 1개이며, 재발급하면 이전 토큰은 즉시 무효화된다.</b>
 * 따라서 호출마다 새로 발급하면 동시에 진행 중이던 다른 호출이 401 {@code invalid-token}으로 깨진다.
 * 이 클래스는 같은 자격 증명에 대해 발급을 한 번만 수행하고 결과를 공유해 그 충돌을 막는다.
 *
 * <p>수명은 응답의 {@code expires_in}만 사용하고, 만료 직전 재사용을 피하기 위해
 * {@link #EARLY_EXPIRY_BUFFER}만큼 보수적으로 앞당겨 만료시킨다.
 * {@code expires_in}이 없거나 버퍼보다 짧으면 캐시하지 않고 매번 새로 발급한다.
 *
 * <p>보안 경계: 토큰과 client secret은 메모리에만 두고 로그·저장소·예외 메시지에 남기지 않는다.
 * 캐시 키도 원문 자격 증명이 아니라 프로세스마다 새로 만든 임의 키로 계산한 HMAC 값이다.
 */
@Component
public class TossSecuritiesAccessTokenIssuer {

    /**
     * 토스증권 제공자 명세의 자격 증명 키다.
     * {@code BrokerProvider.TOSS_SECURITIES}가 선언한 {@code BrokerCredentialField.key}와 같아야 하며,
     * {@code TossSecuritiesCredentialFieldsTest}가 둘의 일치를 검사한다.
     *
     * <p>자격 증명 묶음에서 개별 값을 꺼내는 지점을 토스 어댑터 전체에서 여기 하나로 모은다.
     * 세 어댑터가 각자 키 문자열을 들고 있으면 명세가 바뀔 때 한 곳을 빠뜨리기 쉽다.
     */
    static final String CLIENT_ID_FIELD = "clientId";
    static final String CLIENT_SECRET_FIELD = "clientSecret";

    /** 만료 경계에서의 401을 피하기 위한 조기 만료 버퍼. */
    private static final Duration EARLY_EXPIRY_BUFFER = Duration.ofSeconds(60);

    private static final String KEY_ALGORITHM = "HmacSHA256";

    private final RestClient restClient;
    private final Clock clock;
    private final SecretKeySpec credentialKeySalt = newCredentialKeySalt();
    private final ConcurrentMap<String, CachedToken> cachedTokens = new ConcurrentHashMap<>();

    public TossSecuritiesAccessTokenIssuer(
            @Qualifier("brokerRestClientBuilder") RestClient.Builder builder,
            @Value("${toss-securities.base-url:https://openapi.tossinvest.com}") String baseUrl,
            Clock clock
    ) {
        this.restClient = builder.baseUrl(baseUrl).build();
        this.clock = clock;
    }

    /**
     * 유효한 토큰이 남아 있으면 그대로 돌려주고, 없을 때만 새로 발급한다.
     * 같은 자격 증명으로 동시에 호출해도 발급은 한 번만 일어난다.
     */
    public String issueAccessToken(BrokerCredentials credentials) {
        String clientId = credentials.require(CLIENT_ID_FIELD);
        String clientSecret = credentials.require(CLIENT_SECRET_FIELD);

        CachedToken cachedToken = cachedTokens.computeIfAbsent(
                credentialKey(clientId, clientSecret), key -> new CachedToken());

        String reusable = cachedToken.reusableToken(clock.instant());
        if (reusable != null) {
            return reusable;
        }

        cachedToken.issuanceLock.lock();
        try {
            // 대기 중 다른 호출이 이미 발급했을 수 있으므로 잠금 안에서 다시 확인한다.
            String reusableAfterLock = cachedToken.reusableToken(clock.instant());
            if (reusableAfterLock != null) {
                return reusableAfterLock;
            }

            IssuedToken issuedToken = requestAccessToken(clientId, clientSecret);
            cachedToken.store(issuedToken, clock.instant(), EARLY_EXPIRY_BUFFER);
            return issuedToken.accessToken();
        } finally {
            cachedToken.issuanceLock.unlock();
        }
    }

    /**
     * 캐시된 토큰을 버린다. 증권사가 401을 돌려줘 토큰이 이미 무효화됐다고 판단될 때 호출한다.
     * 다음 호출은 새 토큰을 발급한다.
     */
    public void invalidate(BrokerCredentials credentials) {
        CachedToken cachedToken = cachedTokens.get(credentialKey(
                credentials.require(CLIENT_ID_FIELD), credentials.require(CLIENT_SECRET_FIELD)));
        if (cachedToken != null) {
            cachedToken.clear();
        }
    }

    private IssuedToken requestAccessToken(String clientId, String clientSecret) {
        try {
            LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("grant_type", "client_credentials");
            form.add("client_id", clientId);
            form.add("client_secret", clientSecret);

            TokenResponse token = restClient.post().uri("/oauth2/token")
                    .header(HttpHeaders.CONTENT_TYPE, "application/x-www-form-urlencoded")
                    .body(form)
                    .retrieve()
                    .body(TokenResponse.class);

            if (token == null || token.access_token() == null || token.access_token().isBlank()) {
                throw new BrokerConnectionUnavailableException("토스증권 인증 응답이 올바르지 않습니다.");
            }

            return new IssuedToken(token.access_token(), token.expires_in());
        } catch (RestClientException exception) {
            throw new BrokerConnectionUnavailableException("토스증권 인증에 실패했습니다.", exception);
        }
    }

    /** 자격 증명 원문을 보관하지 않으면서 자격 증명별로 캐시를 분리하기 위한 키다. */
    private String credentialKey(String clientId, String clientSecret) {
        try {
            Mac mac = Mac.getInstance(KEY_ALGORITHM);
            mac.init(credentialKeySalt);
            mac.update(nullToEmpty(clientId).getBytes(StandardCharsets.UTF_8));
            mac.update((byte) 0);
            mac.update(nullToEmpty(clientSecret).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(mac.doFinal());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("자격 증명 캐시 키를 만들 수 없습니다.", exception);
        }
    }

    private static SecretKeySpec newCredentialKeySalt() {
        byte[] salt = new byte[32];
        new SecureRandom().nextBytes(salt);
        return new SecretKeySpec(salt, KEY_ALGORITHM);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /** 자격 증명 하나에 대한 캐시 항목이자 발급 직렬화 지점이다. */
    private static final class CachedToken {

        private final ReentrantLock issuanceLock = new ReentrantLock();

        private volatile String accessToken;
        private volatile Instant reusableUntil;

        private String reusableToken(Instant now) {
            String token = accessToken;
            Instant until = reusableUntil;
            if (token == null || until == null || !now.isBefore(until)) {
                return null;
            }
            return token;
        }

        private void store(IssuedToken issuedToken, Instant now, Duration earlyExpiryBuffer) {
            Duration reusableFor = issuedToken.reusableFor(earlyExpiryBuffer);
            if (reusableFor == null) {
                // 수명을 신뢰할 수 없으면 캐시하지 않고 다음 호출에서 다시 발급한다.
                clear();
                return;
            }
            accessToken = issuedToken.accessToken();
            reusableUntil = now.plus(reusableFor);
        }

        private void clear() {
            accessToken = null;
            reusableUntil = null;
        }
    }

    /** 발급 응답에서 이 클래스가 사용하는 값만 담는다. */
    private record IssuedToken(String accessToken, Long expiresInSeconds) {

        /** 재사용 가능한 기간. 수명이 없거나 버퍼 이하이면 {@code null}이다. */
        private Duration reusableFor(Duration earlyExpiryBuffer) {
            if (expiresInSeconds == null || expiresInSeconds <= 0) {
                return null;
            }
            Duration reusableFor = Duration.ofSeconds(expiresInSeconds).minus(earlyExpiryBuffer);
            return reusableFor.isPositive() ? reusableFor : null;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TokenResponse(String access_token, Long expires_in) {}
}
