package com.tradeguide.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 브로커 HTTP 클라이언트의 connect/read timeout이 실제로 반영되는지 검증한다.
 *
 * <p>{@link BrokerHttpClientConfig#createRequestFactory}는 Spring 컨테이너 없이도 호출할 수
 * 있는 순수 함수라서 타임아웃 값 자체를 직접 검증하고, {@link ApplicationContextRunner}로는
 * {@code @Value} 기본값·오버라이드 바인딩이 실제로 동작하는지 검증한다. 어느 쪽도 실제
 * 네트워크 호출을 하지 않는다.
 */
class BrokerHttpClientConfigTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(BrokerHttpClientConfig.class);

    @Test
    void appliesTheGivenConnectAndReadTimeoutsToTheUnderlyingHttpClient() {
        ClientHttpRequestFactory factory = BrokerHttpClientConfig.createRequestFactory(
                Duration.ofMillis(1_234), Duration.ofMillis(5_678));

        assertThat(factory).isInstanceOf(JdkClientHttpRequestFactory.class);
        HttpClient httpClient = (HttpClient) ReflectionTestUtils.getField(factory, "httpClient");
        assertThat(httpClient.connectTimeout()).contains(Duration.ofMillis(1_234));
        assertThat(ReflectionTestUtils.getField(factory, "readTimeout")).isEqualTo(Duration.ofMillis(5_678));
    }

    @Test
    void rejectsAZeroOrNegativeConnectTimeout() {
        assertThatThrownBy(() ->
                BrokerHttpClientConfig.createRequestFactory(Duration.ZERO, Duration.ofSeconds(10)))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() ->
                BrokerHttpClientConfig.createRequestFactory(Duration.ofSeconds(-1), Duration.ofSeconds(10)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAZeroOrNegativeReadTimeout() {
        assertThatThrownBy(() ->
                BrokerHttpClientConfig.createRequestFactory(Duration.ofSeconds(3), Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() ->
                BrokerHttpClientConfig.createRequestFactory(Duration.ofSeconds(3), Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** 아무 설정이 없을 때도 보수적인 기본값(연결 3초/읽기 10초)으로 빈이 만들어진다. */
    @Test
    void buildsWithConservativeDefaultsWhenNoPropertyIsSet() {
        contextRunner.run(context -> {
            RestClient.Builder builder = context.getBean("brokerRestClientBuilder", RestClient.Builder.class);

            HttpClient httpClient = extractHttpClient(builder);
            assertThat(httpClient.connectTimeout()).contains(Duration.ofMillis(3_000));
            assertThat(extractReadTimeout(builder)).isEqualTo(Duration.ofMillis(10_000));
        });
    }

    /** 프로퍼티로 넘긴 타임아웃 값이 실제로 빈 생성에 쓰인다. */
    @Test
    void bindsExplicitTimeoutPropertiesFromConfiguration() {
        contextRunner
                .withPropertyValues(
                        "tradeguide.broker.http.connect-timeout-ms=2000",
                        "tradeguide.broker.http.read-timeout-ms=9000")
                .run(context -> {
                    RestClient.Builder builder =
                            context.getBean("brokerRestClientBuilder", RestClient.Builder.class);

                    HttpClient httpClient = extractHttpClient(builder);
                    assertThat(httpClient.connectTimeout()).contains(Duration.ofMillis(2_000));
                    assertThat(extractReadTimeout(builder)).isEqualTo(Duration.ofMillis(9_000));
                });
    }

    private HttpClient extractHttpClient(RestClient.Builder builder) {
        return (HttpClient) ReflectionTestUtils.getField(extractRequestFactory(builder), "httpClient");
    }

    private Duration extractReadTimeout(RestClient.Builder builder) {
        return (Duration) ReflectionTestUtils.getField(extractRequestFactory(builder), "readTimeout");
    }

    private Object extractRequestFactory(RestClient.Builder builder) {
        return ReflectionTestUtils.getField(builder, "requestFactory");
    }
}
