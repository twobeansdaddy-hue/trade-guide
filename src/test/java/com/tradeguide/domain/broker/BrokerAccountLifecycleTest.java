package com.tradeguide.domain.broker;

import com.tradeguide.domain.member.Member;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 계좌 행의 생애주기 규칙을 검증한다. 재검증은 계좌 행을 지우지 않고 상태만 바꾼다.
 * 계좌 식별자는 과거 보유 종목 스냅샷과 개시 잔고 승인 이력이 참조하기 때문이다.
 */
class BrokerAccountLifecycleTest {

    @Test
    void marksNewAccountActive() {
        BrokerAccount account = account("*****1234");

        assertThat(account.getStatus()).isEqualTo(BrokerAccountStatus.ACTIVE);
        assertThat(account.isActive()).isTrue();
        assertThat(account.getDetachedAt()).isNull();
    }

    @Test
    void keepsAccountRowAndDetachesItWhenProviderStopsReturningIt() {
        BrokerConnection connection = connection();
        BrokerAccount kept = account("*****1111");
        BrokerAccount missing = account("*****2222");
        connection.reconcileVerifiedAccounts(List.of(kept, missing));

        connection.reconcileVerifiedAccounts(List.of(kept));

        assertThat(connection.getAccounts()).containsExactly(kept, missing);
        assertThat(connection.getActiveAccounts()).containsExactly(kept);
        assertThat(missing.getStatus()).isEqualTo(BrokerAccountStatus.DETACHED);
        assertThat(missing.getDetachedAt()).isNotNull();
    }

    @Test
    void keepsFirstDetachedAtWhenAccountStaysMissing() {
        BrokerConnection connection = connection();
        BrokerAccount kept = account("*****1111");
        BrokerAccount missing = account("*****2222");
        connection.reconcileVerifiedAccounts(List.of(kept, missing));
        connection.reconcileVerifiedAccounts(List.of(kept));
        var firstDetachedAt = missing.getDetachedAt();

        connection.reconcileVerifiedAccounts(List.of(kept));

        assertThat(missing.getDetachedAt()).isEqualTo(firstDetachedAt);
    }

    @Test
    void reactivatesSameRowWhenProviderReturnsAccountAgain() {
        BrokerConnection connection = connection();
        BrokerAccount account = account("*****1234");
        connection.reconcileVerifiedAccounts(List.of(account));
        connection.reconcileVerifiedAccounts(List.of());

        account.refreshFromProvider("re-encrypted", "new-iv", "*****9999", "종합", 2);
        connection.reconcileVerifiedAccounts(List.of(account));

        assertThat(connection.getAccounts()).containsExactly(account);
        assertThat(account.getStatus()).isEqualTo(BrokerAccountStatus.ACTIVE);
        assertThat(account.getDetachedAt()).isNull();
        assertThat(account.getMaskedAccountNumber()).isEqualTo("*****9999");
        assertThat(account.getAccountType()).isEqualTo("종합");
        assertThat(account.getEncryptedAccountSequence()).isEqualTo("re-encrypted");
        assertThat(account.getEncryptionKeyVersion()).isEqualTo(2);
    }

    @Test
    void detachesEveryAccountWhenProviderReturnsNone() {
        BrokerConnection connection = connection();
        BrokerAccount account = account("*****1234");
        connection.reconcileVerifiedAccounts(List.of(account));

        connection.reconcileVerifiedAccounts(List.of());

        assertThat(connection.getAccounts()).containsExactly(account);
        assertThat(connection.getActiveAccounts()).isEmpty();
    }

    @Test
    void rejectsNullVerificationResult() {
        assertThatThrownBy(() -> connection().reconcileVerifiedAccounts(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("증권사 계좌 검증 결과가 필요합니다.");
    }

    private BrokerConnection connection() {
        return new BrokerConnection(
                new Member("broker@example.com", "broker-user"),
                BrokerProvider.TOSS_SECURITIES,
                "개인 토스증권"
        );
    }

    private BrokerAccount account(String maskedAccountNumber) {
        return new BrokerAccount(
                "encrypted-sequence", "sequence-iv", maskedAccountNumber, "위탁", 1
        );
    }
}
