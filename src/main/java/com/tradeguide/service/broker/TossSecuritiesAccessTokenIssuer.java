package com.tradeguide.service.broker;

import com.tradeguide.exception.BrokerConnectionUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 토스증권 client-credentials 액세스 토큰을 발급한다.
 * 토큰은 호출 구간에서만 사용하는 단기 런타임 값이며 저장하지 않는다.
 */
@Component
public class TossSecuritiesAccessTokenIssuer {

    private final RestClient restClient;

    public TossSecuritiesAccessTokenIssuer(
            RestClient.Builder builder,
            @Value("${toss-securities.base-url:https://openapi.tossinvest.com}") String baseUrl
    ) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    public String issueAccessToken(String clientId, String clientSecret) {
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

            return token.access_token();
        } catch (RestClientException exception) {
            throw new BrokerConnectionUnavailableException("토스증권 인증에 실패했습니다.", exception);
        }
    }

    private record TokenResponse(String access_token) {}
}
