package com.tradeguide.dto.broker;

import com.tradeguide.domain.broker.BrokerProvider;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 두 본문 형태를 하나로 정규화하는 규칙을 고정한다. 프론트엔드가 아직 레거시 형태를
 * 보내므로, 두 경로가 같은 결과를 만들지 못하면 화면이 조용히 깨진다.
 */
class BrokerConnectionCreateRequestTest {

    @Test
    void normalizesLegacyTopLevelFieldsIntoTheSameMapAsTheCredentialForm() {
        BrokerConnectionCreateRequest legacy = request();
        legacy.setClientId("test-client-id");
        legacy.setClientSecret("test-client-secret");

        BrokerConnectionCreateRequest modern = request();
        modern.setCredentials(new LinkedHashMap<>(Map.of(
                "clientId", "test-client-id",
                "clientSecret", "test-client-secret"
        )));

        assertThat(legacy.resolveCredentialValues())
                .isEqualTo(modern.resolveCredentialValues())
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        "clientId", "test-client-id",
                        "clientSecret", "test-client-secret"
                ));
    }

    /**
     * 어느 쪽이 진짜 입력인지 서버가 정할 수 없다. 한쪽을 조용히 이기게 하면 사용자가
     * 방금 고친 값이 무시된 채 저장될 수 있다.
     */
    @Test
    void rejectsRequestThatSendsBothCredentialForms() {
        BrokerConnectionCreateRequest request = request();
        request.setClientId("test-client-id");
        request.setCredentials(new LinkedHashMap<>(Map.of("clientId", "other-value")));

        assertThatThrownBy(request::resolveCredentialValues)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("자격 증명은 credentials 항목과 개별 항목 중 한 형태로만 보낼 수 있습니다.");
    }

    @Test
    void rejectsRequestWithoutAnyCredentials() {
        assertThatThrownBy(request()::resolveCredentialValues)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("자격 증명은 필수입니다.");
    }

    /**
     * 빈 맵은 "보내지 않음"으로 본다. 레거시 항목까지 없으면 자격 증명이 없는 요청이다.
     * 이 판정은 DTO에서 끝나고, 값의 유효성은 제공자 명세를 아는 서비스가 판단한다.
     */
    @Test
    void treatsEmptyCredentialMapAsMissingCredentials() {
        BrokerConnectionCreateRequest request = request();
        request.setCredentials(new LinkedHashMap<>());

        assertThatThrownBy(request::resolveCredentialValues)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("자격 증명은 필수입니다.");
    }

    /** 오류 메시지에는 입력값이 담기지 않아야 한다. */
    @Test
    void doesNotExposeSubmittedValuesInRejectionMessages() {
        BrokerConnectionCreateRequest request = request();
        request.setClientId("leak-marker-value");
        request.setCredentials(new LinkedHashMap<>(Map.of("clientId", "leak-marker-value")));

        assertThatThrownBy(request::resolveCredentialValues)
                .hasMessageNotContaining("leak-marker-value");
    }

    private BrokerConnectionCreateRequest request() {
        BrokerConnectionCreateRequest request = new BrokerConnectionCreateRequest();
        request.setProvider(BrokerProvider.TOSS_SECURITIES);
        request.setDisplayName("개인 토스증권");
        return request;
    }
}
