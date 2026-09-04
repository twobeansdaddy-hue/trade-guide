package com.tradeguide.dto.broker;

import com.tradeguide.domain.broker.BrokerProvider;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class BrokerConnectionCreateRequest {

    @NotNull(message = "증권사 제공자는 필수입니다.")
    private BrokerProvider provider;

    @NotBlank(message = "연결 이름은 필수입니다.")
    @Size(max = 100, message = "연결 이름은 100자 이하여야 합니다.")
    private String displayName;

    @NotBlank(message = "Client ID는 필수입니다.")
    private String clientId;

    @NotBlank(message = "Client Secret은 필수입니다.")
    private String clientSecret;

    public BrokerProvider getProvider() {
        return provider;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getClientId() {
        return clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }
}
