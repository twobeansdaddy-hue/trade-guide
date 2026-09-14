package com.tradeguide.service.broker;

import com.tradeguide.domain.broker.BrokerCredentialField;
import com.tradeguide.domain.broker.BrokerProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 토스 어댑터가 자격 증명 묶음에서 꺼내는 키가 제공자 명세와 같은지 확인한다.
 *
 * <p>둘이 어긋나면 컴파일은 통과하고 연결 검증에서야 실패한다. 그때 보이는 것은
 * "자격 증명 항목이 없습니다"뿐이라 원인이 명세 불일치인지 사용자 입력 오류인지 알 수 없다.
 */
class TossSecuritiesCredentialFieldsTest {

    @Test
    void adapterCredentialKeysMatchTheProviderSchema() {
        assertThat(BrokerProvider.TOSS_SECURITIES.getCredentialFields().stream()
                .map(BrokerCredentialField::key))
                .containsExactly(
                        TossSecuritiesAccessTokenIssuer.CLIENT_ID_FIELD,
                        TossSecuritiesAccessTokenIssuer.CLIENT_SECRET_FIELD
                );
    }
}
