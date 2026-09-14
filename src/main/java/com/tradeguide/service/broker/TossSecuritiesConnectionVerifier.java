package com.tradeguide.service.broker;

import com.tradeguide.domain.broker.BrokerConnectionCandidateAccount;
import com.tradeguide.domain.broker.BrokerCredentials;
import com.tradeguide.domain.broker.BrokerProvider;
import com.tradeguide.exception.BrokerConnectionUnavailableException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

@Component
public class TossSecuritiesConnectionVerifier implements BrokerConnectionVerifier {

    private final RestClient restClient;
    private final TossSecuritiesAccessTokenIssuer accessTokenIssuer;

    public TossSecuritiesConnectionVerifier(
            @Qualifier("brokerRestClientBuilder") RestClient.Builder builder,
            @Value("${toss-securities.base-url:https://openapi.tossinvest.com}") String baseUrl,
            TossSecuritiesAccessTokenIssuer accessTokenIssuer
    ) {
        this.restClient = builder.baseUrl(baseUrl).build();
        this.accessTokenIssuer = accessTokenIssuer;
    }

    @Override
    public BrokerProvider getProvider() {
        return BrokerProvider.TOSS_SECURITIES;
    }

    @Override
    public List<BrokerConnectionCandidateAccount> verify(BrokerCredentials credentials) {
        String accessToken = accessTokenIssuer.issueAccessToken(credentials);

        try {
            AccountsResponse accounts = restClient.get().uri("/api/v1/accounts")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve().body(AccountsResponse.class);
            if (accounts == null || accounts.result() == null) {
                throw new BrokerConnectionUnavailableException("토스증권 계좌 응답이 올바르지 않습니다.");
            }
            return accounts.result().stream()
                    .filter(account -> account.accountSeq() != null && account.accountNo() != null)
                    .map(account -> new BrokerConnectionCandidateAccount(
                            String.valueOf(account.accountSeq()), mask(account.accountNo()), account.accountType()))
                    .toList();
        } catch (HttpClientErrorException.Unauthorized exception) {
            // 토큰이 이미 무효화된 상태이므로 캐시를 버려 다음 호출이 새로 발급하게 한다.
            accessTokenIssuer.invalidate(credentials);
            throw new BrokerConnectionUnavailableException("토스증권 연결 확인에 실패했습니다.", exception);
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
}
