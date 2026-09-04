package com.tradeguide.service.broker;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.tradeguide.domain.broker.BrokerHolding;
import com.tradeguide.domain.broker.BrokerHoldingSnapshot;
import com.tradeguide.domain.broker.BrokerProvider;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.exception.BrokerConnectionUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 토스증권 계좌의 보유 종목을 읽기 전용으로 조회한다.
 * 주문, 예약 주문, 계좌 변경 요청은 이 어댑터에 존재하지 않는다.
 *
 * <p>공식 OpenAPI 명세 기준 계약이다.
 * {@code GET /api/v1/holdings}는 계좌를 {@code X-Tossinvest-Account} 헤더(accountSeq)로 받고,
 * 응답 {@code result}는 요약 필드와 {@code items} 배열을 함께 담은 보유 종목 개요 객체다.
 * {@code items}의 {@code quantity}와 {@code averagePurchasePrice}는 JSON 문자열 decimal이다.
 *
 * <p>명세는 {@code marketCountry}에 대해 클라이언트가 알 수 없는 enum 값을 허용하도록 요구한다.
 * 이 어댑터는 지원하지 않는 시장을 스냅샷에서 제외하고 건수만 보고한다.
 */
@Component
public class TossSecuritiesHoldingsProvider implements BrokerHoldingsProvider {

    private static final String ACCOUNT_HEADER = "X-Tossinvest-Account";

    private final RestClient restClient;
    private final TossSecuritiesAccessTokenIssuer accessTokenIssuer;

    public TossSecuritiesHoldingsProvider(
            RestClient.Builder builder,
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
    public BrokerHoldingSnapshot fetchHoldings(String clientId, String clientSecret, String accountSequence) {
        if (accountSequence == null || accountSequence.isBlank()) {
            throw new IllegalArgumentException("증권사 계좌 일련번호가 필요합니다.");
        }

        String accessToken = accessTokenIssuer.issueAccessToken(clientId, clientSecret);

        HoldingsResponse response;
        try {
            response = restClient.get().uri("/api/v1/holdings")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .header(ACCOUNT_HEADER, accountSequence)
                    .retrieve()
                    .body(HoldingsResponse.class);
        } catch (RestClientException exception) {
            throw new BrokerConnectionUnavailableException("토스증권 보유 종목 조회에 실패했습니다.", exception);
        }

        if (response == null || response.result() == null || response.result().items() == null) {
            throw new BrokerConnectionUnavailableException("토스증권 보유 종목 응답이 올바르지 않습니다.");
        }

        List<BrokerHolding> holdings = new ArrayList<>();
        int unsupportedMarketCount = 0;

        for (HoldingsItem item : response.result().items()) {
            if (item == null || item.symbol() == null || item.symbol().isBlank()) {
                throw new BrokerConnectionUnavailableException("토스증권 보유 종목 응답이 올바르지 않습니다.");
            }

            Market market = toMarket(item.marketCountry());
            if (market == null) {
                unsupportedMarketCount++;
                continue;
            }

            holdings.add(new BrokerHolding(
                    market,
                    item.symbol().trim().toUpperCase(Locale.ROOT),
                    toDecimal(item.quantity()),
                    toDecimal(item.averagePurchasePrice())
            ));
        }

        return new BrokerHoldingSnapshot(holdings, unsupportedMarketCount);
    }

    private Market toMarket(String marketCountry) {
        if (marketCountry == null) {
            return null;
        }

        return switch (marketCountry.trim().toUpperCase(Locale.ROOT)) {
            case "US" -> Market.US;
            case "KR" -> Market.KR;
            default -> null;
        };
    }

    private BigDecimal toDecimal(String value) {
        if (value == null || value.isBlank()) {
            throw new BrokerConnectionUnavailableException("토스증권 보유 종목 응답이 올바르지 않습니다.");
        }

        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException exception) {
            throw new BrokerConnectionUnavailableException("토스증권 보유 종목 응답이 올바르지 않습니다.", exception);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record HoldingsResponse(HoldingsOverview result) {}

    /** 명세의 {@code HoldingsOverview}에서 이 어댑터가 사용하는 필드만 매핑한다. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record HoldingsOverview(List<HoldingsItem> items) {}

    /** 명세의 {@code HoldingsItem}에서 이 어댑터가 사용하는 필드만 매핑한다. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record HoldingsItem(
            String symbol,
            String marketCountry,
            String quantity,
            String averagePurchasePrice
    ) {}
}
