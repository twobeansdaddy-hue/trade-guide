package com.tradeguide.service.market;

import com.tradeguide.config.BrokerCredentialKeyringProperties;
import com.tradeguide.domain.market.MarketDataProvider;
import com.tradeguide.service.broker.AesGcmBrokerCredentialCipher;
import com.tradeguide.service.broker.BrokerCredentialCipher;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * application.yml과 application-local.yml이 twelve-data.api-key를 같은 프로퍼티
 * 경로로 바인딩하는지 확인한다. 실제 application-local.yml은 Git에서 제외돼 있어
 * 이 테스트에서 읽거나 참조하지 않으며, local 프로필이 활성화된 상태에서 더미
 * 프로퍼티가 같은 경로로 주입될 때의 바인딩만 검증한다.
 */
class MarketDataProviderConfigurationStatusLocalProfileTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(MarketDataProviderConfigurationStatus.class)
            .withBean(BrokerCredentialCipher.class, () -> new AesGcmBrokerCredentialCipher(
                    new BrokerCredentialKeyringProperties(null, null, null)))
            .withInitializer(context -> context.getEnvironment().setActiveProfiles("local"));

    @Test
    void bindsTwelveDataApiKeyFromSamePropertyPathWhenLocalProfileIsActive() {
        contextRunner
                .withPropertyValues("twelve-data.api-key=dummy-local-test-key")
                .run(context -> {
                    MarketDataProviderConfigurationStatus status =
                            context.getBean(MarketDataProviderConfigurationStatus.class);

                    assertThat(status.isConfigured(MarketDataProvider.TWELVE_DATA)).isTrue();
                });
    }

    @Test
    void treatsTwelveDataAsUnconfiguredWhenLocalProfileHasNoApiKeyProperty() {
        contextRunner.run(context -> {
            MarketDataProviderConfigurationStatus status =
                    context.getBean(MarketDataProviderConfigurationStatus.class);

            assertThat(status.isConfigured(MarketDataProvider.TWELVE_DATA)).isFalse();
        });
    }
}
