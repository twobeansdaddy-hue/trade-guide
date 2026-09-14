package com.tradeguide.service.market;

import com.tradeguide.config.BrokerCredentialKeyringProperties;
import com.tradeguide.domain.market.MarketDataProvider;
import com.tradeguide.exception.MarketDataProviderNotConfiguredException;
import com.tradeguide.exception.MarketDataUnavailableException;
import com.tradeguide.service.broker.AesGcmBrokerCredentialCipher;
import com.tradeguide.service.broker.BrokerCredentialCipher;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class MarketDataProviderConfigurationStatusTest {

    private static final String VALID_KEY =
            Base64.getEncoder().encodeToString(new byte[32]);

    private static BrokerCredentialCipher unconfiguredCipher() {
        return new AesGcmBrokerCredentialCipher(
                new BrokerCredentialKeyringProperties(null, null, null)
        );
    }

    private static BrokerCredentialCipher legacySingleKeyCipher() {
        return new AesGcmBrokerCredentialCipher(
                new BrokerCredentialKeyringProperties(VALID_KEY, null, null)
        );
    }

    private static BrokerCredentialCipher v1KeyringCipher() {
        return new AesGcmBrokerCredentialCipher(
                new BrokerCredentialKeyringProperties(
                        null,
                        List.of(new BrokerCredentialKeyringProperties.KeyEntry(1, VALID_KEY)),
                        1
                )
        );
    }

    private static BrokerCredentialCipher v2OnlyKeyringCipher() {
        return new AesGcmBrokerCredentialCipher(
                new BrokerCredentialKeyringProperties(
                        null,
                        List.of(new BrokerCredentialKeyringProperties.KeyEntry(2, VALID_KEY)),
                        2
                )
        );
    }

    @Test
    void reportsTwelveDataConfiguredWhenApiKeyIsPresent() {
        MarketDataProviderConfigurationStatus status =
                new MarketDataProviderConfigurationStatus("test-api-key", unconfiguredCipher());

        assertThat(status.isConfigured(MarketDataProvider.TWELVE_DATA)).isTrue();
        assertThatCode(() ->
                status.requireConfigured(MarketDataProvider.TWELVE_DATA)
        ).doesNotThrowAnyException();
    }

    @Test
    void reportsTwelveDataNotConfiguredWhenApiKeyIsBlank() {
        MarketDataProviderConfigurationStatus status =
                new MarketDataProviderConfigurationStatus("   ", unconfiguredCipher());

        assertThat(status.isConfigured(MarketDataProvider.TWELVE_DATA)).isFalse();
    }

    @Test
    void reportsTwelveDataNotConfiguredWhenApiKeyIsNull() {
        MarketDataProviderConfigurationStatus status =
                new MarketDataProviderConfigurationStatus(null, unconfiguredCipher());

        assertThat(status.isConfigured(MarketDataProvider.TWELVE_DATA)).isFalse();
    }

    @Test
    void requireConfiguredExplainsMissingTwelveDataPrerequisiteWithoutSecretValue() {
        MarketDataProviderConfigurationStatus status =
                new MarketDataProviderConfigurationStatus("", unconfiguredCipher());

        assertThatExceptionOfType(MarketDataProviderNotConfiguredException.class)
                .isThrownBy(() ->
                        status.requireConfigured(MarketDataProvider.TWELVE_DATA)
                )
                .satisfies(exception -> {
                    assertThat(exception.getProvider())
                            .isEqualTo(MarketDataProvider.TWELVE_DATA);
                    assertThat(exception.getMessage())
                            .contains("Twelve Data")
                            .contains("TWELVE_DATA_API_KEY");
                })
                .isInstanceOf(MarketDataUnavailableException.class);
    }

    @Test
    void reportsTossSecuritiesNotConfiguredWhenNoBrokerEncryptionKeyExists() {
        MarketDataProviderConfigurationStatus status =
                new MarketDataProviderConfigurationStatus("", unconfiguredCipher());

        assertThat(status.isConfigured(MarketDataProvider.TOSS_SECURITIES)).isFalse();
    }

    @Test
    void reportsTossSecuritiesConfiguredWithLegacySingleEncryptionKey() {
        MarketDataProviderConfigurationStatus status =
                new MarketDataProviderConfigurationStatus("", legacySingleKeyCipher());

        assertThat(status.isConfigured(MarketDataProvider.TOSS_SECURITIES)).isTrue();
    }

    @Test
    void reportsTossSecuritiesConfiguredWithV1KeyringEntry() {
        MarketDataProviderConfigurationStatus status =
                new MarketDataProviderConfigurationStatus("", v1KeyringCipher());

        assertThat(status.isConfigured(MarketDataProvider.TOSS_SECURITIES)).isTrue();
    }

    @Test
    void reportsTossSecuritiesConfiguredWithV2OnlyKeyringWhenItIsTheCurrentVersion() {
        MarketDataProviderConfigurationStatus status =
                new MarketDataProviderConfigurationStatus("", v2OnlyKeyringCipher());

        assertThat(status.isConfigured(MarketDataProvider.TOSS_SECURITIES)).isTrue();
    }

    @Test
    void requireConfiguredExplainsMissingTossSecuritiesPrerequisiteWithoutSecretValue() {
        MarketDataProviderConfigurationStatus status =
                new MarketDataProviderConfigurationStatus("", unconfiguredCipher());

        assertThatExceptionOfType(MarketDataProviderNotConfiguredException.class)
                .isThrownBy(() ->
                        status.requireConfigured(MarketDataProvider.TOSS_SECURITIES)
                )
                .satisfies(exception -> {
                    assertThat(exception.getProvider())
                            .isEqualTo(MarketDataProvider.TOSS_SECURITIES);
                    assertThat(exception.getMessage())
                            .contains("BROKER_CREDENTIAL_ENCRYPTION_KEY")
                            .doesNotContain(VALID_KEY);
                });
    }

    @Test
    void treatsYahooFinanceAsHavingNoServerSideCredentialPrerequisite() {
        MarketDataProviderConfigurationStatus status =
                new MarketDataProviderConfigurationStatus("", unconfiguredCipher());

        assertThat(status.isConfigured(MarketDataProvider.YAHOO_FINANCE)).isTrue();
    }
}
