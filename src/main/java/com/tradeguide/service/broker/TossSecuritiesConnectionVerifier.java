package com.tradeguide.service.broker;

import com.tradeguide.exception.BrokerConnectionUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

@Component
public class TossSecuritiesConnectionVerifier {

    private final RestClient restClient;
    private final TossSecuritiesAccessTokenIssuer accessTokenIssuer;

    public TossSecuritiesConnectionVerifier(
            RestClient.Builder builder,
            @Value("${toss-securities.base-url:https://openapi.tossinvest.com}") String baseUrl,
            TossSecuritiesAccessTokenIssuer accessTokenIssuer
    ) {
        this.restClient = builder.baseUrl(baseUrl).build();
        this.accessTokenIssuer = accessTokenIssuer;
    }

    public List<TossAccount> verify(String clientId, String clientSecret) {
        String accessToken = accessTokenIssuer.issueAccessToken(clientId, clientSecret);

        try {
            AccountsResponse accounts = restClient.get().uri("/api/v1/accounts")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve().body(AccountsResponse.class);
            if (accounts == null || accounts.result() == null) {
                throw new BrokerConnectionUnavailableException("토스증권 계좌 응답이 올바르지 않습니다.");
            }
            return accounts.result().stream()
                    .filter(account -> account.accountSeq() != null && account.accountNo() != null)
                    .map(account -> new TossAccount(account.accountSeq(), mask(account.accountNo()), account.accountType()))
                    .toList();
        } catch (RestClientException exception) {
            throw new BrokerConnectionUnavailableException("토스증권 연결 확인에 실패했습니다.", exception);
        }
    }

    private String mask(String number) {
        int visible = Math.min(4, number.length());
        return "*".repeat(Math.max(0, number.length() - visible)) + number.substring(number.length() - visible);
    }

    private record AccountsResponse(List<AccountResponse> result) {}
    private record AccountResponse(Long accountSeq, String accountNo, String accountType) {}
    public record TossAccount(Long accountSequence, String maskedAccountNumber, String accountType) {}
}
