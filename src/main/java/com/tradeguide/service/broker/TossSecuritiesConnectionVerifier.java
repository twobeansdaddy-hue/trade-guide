package com.tradeguide.service.broker;

import com.tradeguide.exception.BrokerConnectionUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

@Component
public class TossSecuritiesConnectionVerifier {
    private final RestClient restClient;

    public TossSecuritiesConnectionVerifier(RestClient.Builder builder,
            @Value("${toss-securities.base-url:https://openapi.tossinvest.com}") String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    public List<String> verify(String clientId, String clientSecret) {
        try {
            LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("grant_type", "client_credentials");
            form.add("client_id", clientId);
            form.add("client_secret", clientSecret);
            TokenResponse token = restClient.post().uri("/oauth2/token")
                    .header(HttpHeaders.CONTENT_TYPE, "application/x-www-form-urlencoded")
                    .body(form).retrieve().body(TokenResponse.class);
            if (token == null || token.access_token() == null || token.access_token().isBlank()) {
                throw new BrokerConnectionUnavailableException("토스증권 인증 응답이 올바르지 않습니다.");
            }
            AccountsResponse accounts = restClient.get().uri("/api/v1/accounts")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token.access_token())
                    .retrieve().body(AccountsResponse.class);
            if (accounts == null || accounts.result() == null) {
                throw new BrokerConnectionUnavailableException("토스증권 계좌 응답이 올바르지 않습니다.");
            }
            return accounts.result().stream().map(AccountResponse::accountNo)
                    .filter(value -> value != null && !value.isBlank()).map(this::mask).toList();
        } catch (RestClientException exception) {
            throw new BrokerConnectionUnavailableException("토스증권 연결 확인에 실패했습니다.", exception);
        }
    }

    private String mask(String number) {
        int visible = Math.min(4, number.length());
        return "*".repeat(Math.max(0, number.length() - visible)) + number.substring(number.length() - visible);
    }

    private record TokenResponse(String access_token) {}
    private record AccountsResponse(List<AccountResponse> result) {}
    private record AccountResponse(String accountNo) {}
}
