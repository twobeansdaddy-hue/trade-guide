package com.tradeguide.domain.broker;

import com.tradeguide.domain.trade.Market;
import com.tradeguide.dto.broker.BrokerConnectionCreateRequest;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 제공자 카탈로그의 자격 증명 입력 명세가 연결 생성 계약과 어긋나지 않는지 검증한다.
 * 명세가 실제 요청 본문과 달라지면 연결 화면이 조용히 잘못된 요청을 만들게 되므로
 * 도메인 단계에서 막는다.
 */
class BrokerProviderCredentialSchemaTest {

    @Test
    void everyProviderDeclaresCredentialFieldsWithUniqueKeys() {
        for (BrokerProvider provider : BrokerProvider.values()) {
            List<BrokerCredentialField> fields = provider.getCredentialFields();

            assertThat(fields).as("%s 자격 증명 입력 명세", provider).isNotEmpty();
            assertThat(fields.stream().map(BrokerCredentialField::key))
                    .as("%s 입력 키는 중복되지 않아야 한다", provider)
                    .doesNotHaveDuplicates();
            assertThat(fields).allSatisfy(field -> {
                assertThat(field.label()).isNotBlank();
                assertThat(field.type()).isNotNull();
            });
        }
    }

    /**
     * 자격 증명 키는 {@code broker_connection_secret_values.field_key}에 그대로 저장된다.
     * 열 길이를 넘는 키를 선언하면 저장 시점에야 실패하므로 명세 단계에서 막는다.
     */
    @Test
    void credentialFieldKeysFitTheSecretValueStorageColumn() {
        for (BrokerProvider provider : BrokerProvider.values()) {
            assertThat(provider.getCredentialFields()).allSatisfy(field -> assertThat(field.key())
                    .as("%s.%s 키는 64자 이하여야 한다", provider, field.key())
                    .hasSizeLessThanOrEqualTo(64));
        }
    }

    /**
     * 레거시 본문 호환 창이다. 현재 프론트엔드는 자격 증명을 최상위 속성으로 보내므로,
     * 그 형태로 오는 키는 요청 DTO가 여전히 받을 수 있어야 한다. 프론트엔드가
     * {@code credentials} 맵으로 옮겨 간 뒤 이 검사는 사라진다.
     */
    @Test
    void tossSecuritiesCredentialKeysStillMapToLegacyRequestProperties() {
        Set<String> requestProperties = Arrays.stream(BrokerConnectionCreateRequest.class.getDeclaredFields())
                .map(Field::getName)
                .collect(Collectors.toSet());

        assertThat(BrokerProvider.TOSS_SECURITIES.getCredentialFields().stream()
                .map(BrokerCredentialField::key))
                .allMatch(requestProperties::contains);
    }

    /**
     * 비밀 입력은 저장 후 다시 조회할 수 없다. 어디서 값을 얻고 무엇이 저장되는지 알려 주지
     * 않으면 사용자는 계좌번호나 로그인 아이디를 넣게 된다.
     */
    @Test
    void secretCredentialFieldsExplainWhereTheValueComesFrom() {
        for (BrokerProvider provider : BrokerProvider.values()) {
            provider.getCredentialFields().stream()
                    .filter(field -> field.type() == BrokerCredentialFieldType.SECRET)
                    .forEach(field -> {
                        assertThat(field.placeholder())
                                .as("%s.%s placeholder", provider, field.key())
                                .isNotBlank();
                        assertThat(field.hint())
                                .as("%s.%s hint", provider, field.key())
                                .isNotBlank();
                    });
        }
    }

    @Test
    void clientSecretIsDeclaredAsSecretInputAndNoPlaceholderCarriesACredential() {
        for (BrokerProvider provider : BrokerProvider.values()) {
            for (BrokerCredentialField field : provider.getCredentialFields()) {
                if (field.key().toLowerCase(Locale.ROOT).contains("secret")) {
                    assertThat(field.type())
                            .as("%s.%s 는 비밀 입력이어야 한다", provider, field.key())
                            .isEqualTo(BrokerCredentialFieldType.SECRET);
                }

                assertThat(field.placeholder())
                        .as("%s.%s placeholder 는 안내 문구여야 한다", provider, field.key())
                        .doesNotContainPattern("[A-Za-z0-9_-]{16,}");
                assertThat(field.hint())
                        .as("%s.%s hint 는 안내 문구여야 한다", provider, field.key())
                        .doesNotContainPattern("[A-Za-z0-9_-]{16,}");
            }
        }
    }

    @Test
    void tossSecuritiesDeclaresOnlyTheMarketsTheHoldingAdapterCanProcess() {
        assertThat(BrokerProvider.TOSS_SECURITIES.getSupportedMarkets())
                .containsExactlyInAnyOrder(Market.US, Market.KR);
    }

    /**
     * 원장에 쓸 수 있는 시장은 조회할 수 있는 시장의 부분집합이어야 한다. 제공자가 늘어도
     * 자동으로 적용되도록 카탈로그 전체를 순회한다. 이 불변식이 깨지면 어댑터가 변환할 수도
     * 없는 시장을 원장에 쓸 수 있다고 선언하게 된다.
     */
    @Test
    void everyProviderDeclaresLedgerWritableMarketsAsSubsetOfSupportedMarkets() {
        for (BrokerProvider provider : BrokerProvider.values()) {
            assertThat(provider.getLedgerWritableMarkets())
                    .as("%s 의 원장 반영 시장은 조회 가능 시장의 부분집합이어야 한다", provider)
                    .isSubsetOf(provider.getSupportedMarkets());
            assertThat(provider.getLedgerWritableMarkets())
                    .as("%s 는 원장에 반영할 수 있는 시장을 하나 이상 선언해야 한다", provider)
                    .isNotEmpty();
        }
    }

    /**
     * 매매 원장에는 통화 필드가 없다. 다중 통화 원장이 도입되기 전에는 원장 반영 시장이
     * US 하나여야 하고, KR은 조회는 되지만 원장에는 들어가지 않는다.
     */
    @Test
    void tossSecuritiesDeclaresOnlyUsAsLedgerWritableWhileStillFetchingKoreanHoldings() {
        assertThat(BrokerProvider.TOSS_SECURITIES.getLedgerWritableMarkets())
                .containsExactly(Market.US);
        assertThat(BrokerProvider.TOSS_SECURITIES.getSupportedMarkets())
                .contains(Market.KR);
    }

    @Test
    void rejectsCredentialFieldWithoutKeyOrLabel() {
        assertThatThrownBy(() -> new BrokerCredentialField(
                " ", "Client ID", BrokerCredentialFieldType.TEXT, true, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("자격 증명 입력 칸 키는 필수입니다.");

        assertThatThrownBy(() -> new BrokerCredentialField(
                "clientId", " ", BrokerCredentialFieldType.TEXT, true, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("자격 증명 입력 칸 라벨은 필수입니다.");

        assertThatThrownBy(() -> new BrokerCredentialField(
                "clientId", "Client ID", null, true, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("자격 증명 입력 칸 유형은 필수입니다.");
    }
}
