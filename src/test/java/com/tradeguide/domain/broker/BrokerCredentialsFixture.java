package com.tradeguide.domain.broker;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 테스트용 자격 증명 묶음이다. 값은 전부 고정된 더미 문자열이며, 실제 발급 자격 증명을
 * 픽스처에 넣지 않는다.
 */
public final class BrokerCredentialsFixture {

    public static final String CLIENT_ID = "test-client-id";
    public static final String CLIENT_SECRET = "test-client-secret";

    private BrokerCredentialsFixture() {
    }

    /** 토스증권 명세 형태의 더미 자격 증명이다. */
    public static BrokerCredentials tossCredentials() {
        return tossCredentials(CLIENT_ID, CLIENT_SECRET);
    }

    public static BrokerCredentials tossCredentials(String clientId, String clientSecret) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("clientId", clientId);
        values.put("clientSecret", clientSecret);
        return new BrokerCredentials(values);
    }
}
