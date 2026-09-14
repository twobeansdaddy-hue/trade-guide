package com.tradeguide.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.Assert;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * 증권사 어댑터가 쓰는 {@code RestClient}에 명시적 connect/read timeout을 건다.
 *
 * <p>이 타임아웃이 없으면 {@link com.tradeguide.service.broker.BrokerDuplicateCallGuard}의
 * 진행 중 잠금이 실제로 아무 문제가 없다. 잠금은 증권사 호출이 실제로 끝나야(성공이든 실패든)
 * {@code finally}에서 풀리는데, 네트워크가 멈춘 채 응답이 영원히 오지 않으면 그 잠금이
 * 사실상 무기한 풀리지 않기 때문이다. 값 자체는 토스증권이 문서화한 제한이 아니라 우리 쪽
 * HTTP 클라이언트 설정이므로, 비밀값이나 제공자 정책을 대신 주장하지 않는다.
 *
 * <p>기본값은 {@code brokerRestClientBuilder(...)} 메서드 인자로 들어오고, 실제 타임아웃
 * 반영은 {@link #createRequestFactory}가 전담한다. 이 메서드는 Spring 컨테이너 없이도 바로
 * 호출해 검증할 수 있는 순수 함수라서, 설정값이 실제로 어떻게 반영되는지 단위 테스트로
 * 고정할 수 있다.
 */
@Configuration
public class BrokerHttpClientConfig {

    static final long DEFAULT_CONNECT_TIMEOUT_MS = 3_000;
    static final long DEFAULT_READ_TIMEOUT_MS = 10_000;

    // Spring Boot의 기본 RestClient.Builder 빈과 마찬가지로 프로토타입 스코프다. 싱글턴이면
    // 네 토스 어댑터가 baseUrl()로 서로의 빌더 상태를 덮어써 마지막에 초기화한 어댑터의
    // baseUrl만 남는다.
    @Bean
    @Qualifier("brokerRestClientBuilder")
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public RestClient.Builder brokerRestClientBuilder(
            @Value("${tradeguide.broker.http.connect-timeout-ms:" + DEFAULT_CONNECT_TIMEOUT_MS + "}")
            long connectTimeoutMs,
            @Value("${tradeguide.broker.http.read-timeout-ms:" + DEFAULT_READ_TIMEOUT_MS + "}")
            long readTimeoutMs
    ) {
        return RestClient.builder().requestFactory(
                createRequestFactory(Duration.ofMillis(connectTimeoutMs), Duration.ofMillis(readTimeoutMs)));
    }

    /**
     * 순수 함수로 분리해 Spring 컨테이너 없이 타임아웃 값 자체를 테스트할 수 있게 한다.
     */
    static ClientHttpRequestFactory createRequestFactory(Duration connectTimeout, Duration readTimeout) {
        Assert.isTrue(connectTimeout != null && !connectTimeout.isNegative() && !connectTimeout.isZero(),
                "연결 타임아웃은 0보다 커야 합니다.");
        Assert.isTrue(readTimeout != null && !readTimeout.isNegative() && !readTimeout.isZero(),
                "읽기 타임아웃은 0보다 커야 합니다.");

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(readTimeout);
        return factory;
    }
}
