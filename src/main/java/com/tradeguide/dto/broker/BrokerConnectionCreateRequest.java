package com.tradeguide.dto.broker;

import com.tradeguide.domain.broker.BrokerProvider;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 증권사 연결 생성 요청이다. 자격 증명은 두 형태를 모두 받는다.
 *
 * <p>권장 형태는 제공자 명세의 키를 그대로 쓰는 {@code credentials} 맵이다.
 *
 * <pre>{@code
 * { "provider": "TOSS_SECURITIES", "displayName": "내 토스 계좌",
 *   "credentials": { "clientId": "...", "clientSecret": "..." } }
 * }</pre>
 *
 * <p>레거시 형태는 {@code clientId}/{@code clientSecret}을 최상위에 두는 현재 프론트엔드의
 * 본문이다. 이 슬라이스는 프론트엔드를 바꾸지 않으므로 최소 한 릴리스 동안 함께 받는다.
 * 레거시 본문은 같은 이름의 맵으로 정규화한 뒤 이후 경로를 똑같이 탄다.
 *
 * <pre>{@code
 * { "provider": "TOSS_SECURITIES", "displayName": "내 토스 계좌",
 *   "clientId": "...", "clientSecret": "..." }
 * }</pre>
 *
 * <p>두 형태를 동시에 보내면 거부한다. 어느 쪽이 진짜 입력인지 서버가 정할 수 없고,
 * 한쪽을 조용히 이기게 하면 사용자가 고친 값이 무시된 채 저장될 수 있다.
 *
 * <p>이 클래스는 자격 증명 값을 검증하지 않는다. 무엇이 필수이고 어떤 키가 허용되는지는
 * 제공자 명세가 정하므로, 판정은 제공자를 아는 서비스 계층에서 한다.
 */
public class BrokerConnectionCreateRequest {

    @NotNull(message = "증권사 제공자는 필수입니다.")
    private BrokerProvider provider;

    @NotBlank(message = "연결 이름은 필수입니다.")
    @Size(max = 100, message = "연결 이름은 100자 이하여야 합니다.")
    private String displayName;

    private Map<String, String> credentials;

    private String clientId;

    private String clientSecret;

    public BrokerProvider getProvider() {
        return provider;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * 두 본문 형태 중 실제로 보낸 쪽을 "키 → 값" 맵으로 정규화한다.
     *
     * <p>오류 메시지에는 입력값을 담지 않는다. 자격 증명이 오류 응답과 서버 로그로 새는
     * 가장 흔한 경로가 검증 메시지다.
     */
    public Map<String, String> resolveCredentialValues() {
        boolean hasCredentialMap = credentials != null && !credentials.isEmpty();
        boolean hasLegacyFields = clientId != null || clientSecret != null;

        if (hasCredentialMap && hasLegacyFields) {
            throw new IllegalArgumentException(
                    "자격 증명은 credentials 항목과 개별 항목 중 한 형태로만 보낼 수 있습니다.");
        }
        if (hasCredentialMap) {
            return new LinkedHashMap<>(credentials);
        }
        if (hasLegacyFields) {
            Map<String, String> normalized = new LinkedHashMap<>();
            normalized.put("clientId", clientId);
            normalized.put("clientSecret", clientSecret);
            return normalized;
        }

        throw new IllegalArgumentException("자격 증명은 필수입니다.");
    }

    public void setProvider(BrokerProvider provider) {
        this.provider = provider;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public void setCredentials(Map<String, String> credentials) {
        this.credentials = credentials;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }
}
