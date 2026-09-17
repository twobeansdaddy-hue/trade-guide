package com.tradeguide.domain.broker;

import com.tradeguide.domain.member.Member;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BrokerOrderExecutionGrantTest {

    private final Member member = new Member("grant-owner@example.com", "grant-owner");
    private final BrokerConnection connection =
            new BrokerConnection(member, BrokerProvider.TOSS_SECURITIES, "개인 토스증권");

    @Test
    void createsActiveGrantOnConsent() {
        BrokerOrderExecutionGrant grant = newGrant();

        assertThat(grant.getStatus()).isEqualTo(BrokerOrderExecutionGrantStatus.ACTIVE);
        assertThat(grant.getStrategyId()).isEqualTo("track-a-weekly-ma-crossover");
        assertThat(grant.getConsentedAt()).isNotNull();
    }

    @Test
    void rejectsBlankStrategyId() {
        assertThatThrownBy(() -> new BrokerOrderExecutionGrant(
                member, connection, " ", new BigDecimal("0.10"), 5, "v1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonPositivePositionSize() {
        assertThatThrownBy(() -> new BrokerOrderExecutionGrant(
                member, connection, "track-a-weekly-ma-crossover", BigDecimal.ZERO, 5, "v1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonPositiveDailyOrderCount() {
        assertThatThrownBy(() -> new BrokerOrderExecutionGrant(
                member, connection, "track-a-weekly-ma-crossover", new BigDecimal("0.10"), 0, "v1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // 킬스위치(PAUSED/REVOKED로 가는 경로)를 ACTIVE 재개 경로보다 먼저 검증한다
    // (docs/agent-tasks/claude-broker-order-execution-grant-implementation-20260917.md 가드레일).

    @Test
    void pauseSwitchesActiveGrantToPaused() {
        BrokerOrderExecutionGrant grant = newGrant();

        grant.pause();

        assertThat(grant.getStatus()).isEqualTo(BrokerOrderExecutionGrantStatus.PAUSED);
    }

    @Test
    void pauseIsIdempotentFromPausedState() {
        BrokerOrderExecutionGrant grant = newGrant();
        grant.pause();

        grant.pause();

        assertThat(grant.getStatus()).isEqualTo(BrokerOrderExecutionGrantStatus.PAUSED);
    }

    @Test
    void revokeIsTerminalAndCannotBePausedAfterward() {
        BrokerOrderExecutionGrant grant = newGrant();

        grant.revoke();

        assertThat(grant.getStatus()).isEqualTo(BrokerOrderExecutionGrantStatus.REVOKED);
        assertThatThrownBy(grant::pause).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void revokeIsTerminalAndCannotBeReactivated() {
        BrokerOrderExecutionGrant grant = newGrant();

        grant.revoke();

        assertThatThrownBy(grant::reactivate).isInstanceOf(IllegalStateException.class);
        assertThat(grant.getStatus()).isEqualTo(BrokerOrderExecutionGrantStatus.REVOKED);
    }

    @Test
    void reactivateOnlyWorksFromPausedState() {
        BrokerOrderExecutionGrant grant = newGrant();
        grant.pause();

        grant.reactivate();

        assertThat(grant.getStatus()).isEqualTo(BrokerOrderExecutionGrantStatus.ACTIVE);
    }

    @Test
    void reactivateFromActiveStateIsRejected() {
        BrokerOrderExecutionGrant grant = newGrant();

        assertThatThrownBy(grant::reactivate).isInstanceOf(IllegalStateException.class);
    }

    private BrokerOrderExecutionGrant newGrant() {
        return new BrokerOrderExecutionGrant(
                member, connection, "track-a-weekly-ma-crossover", new BigDecimal("0.10"), 5, "v1");
    }
}
