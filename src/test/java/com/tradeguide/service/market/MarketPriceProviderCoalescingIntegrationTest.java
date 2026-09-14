package com.tradeguide.service.market;

import com.tradeguide.config.BrokerCredentialKeyringProperties;
import com.tradeguide.domain.market.MarketPrice;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.service.broker.AesGcmBrokerCredentialCipher;
import com.tradeguide.service.broker.BrokerCredentialCipher;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * {@link TwelveDataMarketPriceProvider}와 {@link MarketPriceCache}를 실제로 연결해,
 * 동일 종목에 대한 동시 요청이 외부 API를 한 번만 호출하는지 검증한다.
 *
 * <p>단일 비행(single-flight) 보장이 없으면 포트폴리오 여러 개가 동시에 같은
 * 종목의 현재가를 조회할 때마다 각각 Twelve Data를 호출해 429를 유발할 수 있다.
 * 이 테스트는 {@link MockRestServiceServer}가 요청을 정확히 한 번만 기대하도록
 * 설정해, 코얼레싱이 깨지면 즉시 실패하도록 한다.
 */
class MarketPriceProviderCoalescingIntegrationTest {

    @Test
    void concurrentRequestsForTheSameTickerTriggerOnlyOneExternalCall()
            throws InterruptedException {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server =
                MockRestServiceServer.bindTo(restClientBuilder).build();

        TwelveDataMarketPriceProvider provider = new TwelveDataMarketPriceProvider(
                restClientBuilder,
                "test-api-key",
                new MarketPriceCache(),
                new MarketDataProviderConfigurationStatus(
                        "test-api-key",
                        unconfiguredBrokerCipher()
                ),
                new TwelveDataRateLimitTracker(java.time.Clock.systemUTC(), 30)
        );

        server.expect(requestTo("https://api.twelvedata.com/price?symbol=AAPL"))
                .andRespond(withSuccess(
                        """
                        { "price": "210.50" }
                        """,
                        MediaType.APPLICATION_JSON
                ));

        int callerCount = 20;
        CountDownLatch callersReady = new CountDownLatch(callerCount);
        CountDownLatch startSignal = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(callerCount);

        try {
            List<Future<MarketPrice>> futures = IntStream.range(0, callerCount)
                    .mapToObj(i -> executor.submit(() -> {
                        callersReady.countDown();
                        awaitStartSignal(startSignal);
                        return provider.getCurrentPrice(Market.US, "AAPL");
                    }))
                    .toList();

            callersReady.await(5, TimeUnit.SECONDS);
            startSignal.countDown();

            Set<BigDecimalKey> distinctPrices = futures.stream()
                    .map(this::joinUninterruptibly)
                    .map(price -> new BigDecimalKey(price.getCurrentPrice().toPlainString()))
                    .collect(Collectors.toSet());

            assertThat(distinctPrices).hasSize(1);
        } finally {
            executor.shutdownNow();
        }

        // 정확히 한 번만 기대한 요청이 실제로도 한 번만 발생했는지 확인한다.
        // 코얼레싱이 깨져 두 번째 요청이 나가면 여기서 AssertionError가 발생한다.
        server.verify();
    }

    private record BigDecimalKey(String value) {
    }

    private static BrokerCredentialCipher unconfiguredBrokerCipher() {
        return new AesGcmBrokerCredentialCipher(
                new BrokerCredentialKeyringProperties(null, null, null)
        );
    }

    private void awaitStartSignal(CountDownLatch startSignal) {
        try {
            startSignal.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private MarketPrice joinUninterruptibly(Future<MarketPrice> future) {
        try {
            return future.get(5, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        } catch (java.util.concurrent.ExecutionException exception) {
            throw new IllegalStateException(exception.getCause());
        } catch (java.util.concurrent.TimeoutException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
