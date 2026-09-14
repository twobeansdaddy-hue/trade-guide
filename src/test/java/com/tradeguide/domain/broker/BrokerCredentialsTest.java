package com.tradeguide.domain.broker;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BrokerCredentialsTest {

    /**
     * 값 유출의 가장 흔한 경로가 로깅이다. 자격 증명 객체를 그대로 로그에 찍는 코드가
     * 한 줄만 있어도 평문이 파일에 남으므로, 문자열 표현에 값이 없어야 한다.
     */
    @Test
    void doesNotExposeCredentialValuesInStringRepresentation() {
        BrokerCredentials credentials = new BrokerCredentials(Map.of(
                "clientId", "leak-marker-id",
                "clientSecret", "leak-marker-secret"
        ));

        assertThat(credentials.toString())
                .doesNotContain("leak-marker-id")
                .doesNotContain("leak-marker-secret")
                .contains("clientId")
                .contains("clientSecret");
    }

    /**
     * 없는 키를 읽는 것은 사용자 입력 오류가 아니라 어댑터의 계약 위반이다.
     * 조용히 {@code null}을 돌려주면 그 값이 그대로 증권사 호출에 실려 원인 없는 401이 된다.
     */
    @Test
    void failsWhenRequestedCredentialFieldIsMissing() {
        BrokerCredentials credentials = new BrokerCredentials(Map.of("clientId", "value"));

        assertThat(credentials.require("clientId")).isEqualTo("value");
        assertThatThrownBy(() -> credentials.require("clientSecret"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("증권사 자격 증명 항목이 없습니다: clientSecret");
    }

    /** 예외 메시지에도 다른 항목의 값이 새지 않아야 한다. */
    @Test
    void doesNotExposeOtherValuesInMissingFieldMessage() {
        BrokerCredentials credentials = new BrokerCredentials(Map.of("clientId", "leak-marker-id"));

        assertThatThrownBy(() -> credentials.require("apiKey"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("leak-marker-id");
    }

    /** 전달한 맵을 나중에 바꿔도 저장된 자격 증명은 흔들리지 않아야 한다. */
    @Test
    void copiesGivenValuesDefensively() {
        Map<String, String> mutable = new LinkedHashMap<>();
        mutable.put("clientId", "first");

        BrokerCredentials credentials = new BrokerCredentials(mutable);
        mutable.put("clientId", "second");

        assertThat(credentials.require("clientId")).isEqualTo("first");
        assertThat(credentials.keys()).containsExactly("clientId");
    }

    @Test
    void rejectsNullValues() {
        assertThatThrownBy(() -> new BrokerCredentials(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("증권사 자격 증명이 필요합니다.");
    }
}
