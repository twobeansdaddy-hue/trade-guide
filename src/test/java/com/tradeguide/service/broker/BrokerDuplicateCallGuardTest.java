package com.tradeguide.service.broker;

import com.tradeguide.exception.BrokerCallCooldownException;
import com.tradeguide.exception.BrokerCallInProgressException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BrokerDuplicateCallGuardTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-09-08T00:00:00Z"), ZoneOffset.UTC);

    private final BrokerDuplicateCallGuard guard = new BrokerDuplicateCallGuard();

    @Test
    void rejectsASecondAcquireOfTheSameKeyWhileTheFirstIsStillHeld() {
        guard.acquire(20L, BrokerCallType.ORDER_HISTORY_IMPORT);

        assertThatThrownBy(() -> guard.acquire(20L, BrokerCallType.ORDER_HISTORY_IMPORT))
                .isInstanceOf(BrokerCallInProgressException.class)
                .hasMessageContaining("주문 이력 가져오기");
    }

    @Test
    void allowsDifferentPortfoliosToAcquireTheSameCallTypeConcurrently() {
        guard.acquire(20L, BrokerCallType.ORDER_HISTORY_IMPORT);

        assertThatCode(() -> guard.acquire(21L, BrokerCallType.ORDER_HISTORY_IMPORT))
                .doesNotThrowAnyException();
    }

    @Test
    void allowsTheSamePortfolioToAcquireDifferentCallTypesConcurrently() {
        guard.acquire(20L, BrokerCallType.ORDER_HISTORY_IMPORT);

        assertThatCode(() -> guard.acquire(20L, BrokerCallType.HOLDING_PREVIEW))
                .doesNotThrowAnyException();
    }

    @Test
    void allowsReacquiringAfterRelease() {
        guard.acquire(20L, BrokerCallType.ORDER_HISTORY_IMPORT);
        guard.release(20L, BrokerCallType.ORDER_HISTORY_IMPORT);

        assertThatCode(() -> guard.acquire(20L, BrokerCallType.ORDER_HISTORY_IMPORT))
                .doesNotThrowAnyException();
    }

    @Test
    void passesTheFirstCallBecauseThereIsNoPriorCallTime() {
        assertThatCode(() -> guard.checkCooldown(BrokerCallType.ORDER_HISTORY_IMPORT, null, FIXED_CLOCK))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsACallStrictlyWithinTheCooldownWindow() {
        LocalDateTime lastCalledAt = LocalDateTime.now(FIXED_CLOCK).minusSeconds(4);

        assertThatThrownBy(() ->
                guard.checkCooldown(BrokerCallType.ORDER_HISTORY_IMPORT, lastCalledAt, FIXED_CLOCK))
                .isInstanceOf(BrokerCallCooldownException.class)
                .satisfies(exception ->
                        assertThat(((BrokerCallCooldownException) exception).retryAfterSeconds()).isPositive());
    }

    @Test
    void allowsACallExactlyAtTheCooldownBoundary() {
        LocalDateTime lastCalledAt = LocalDateTime.now(FIXED_CLOCK).minus(BrokerCallType.ORDER_HISTORY_IMPORT.cooldown());

        assertThatCode(() ->
                guard.checkCooldown(BrokerCallType.ORDER_HISTORY_IMPORT, lastCalledAt, FIXED_CLOCK))
                .doesNotThrowAnyException();
    }

    @Test
    void allowsACallAfterTheCooldownWindowHasFullyPassed() {
        LocalDateTime lastCalledAt = LocalDateTime.now(FIXED_CLOCK).minusSeconds(30);

        assertThatCode(() ->
                guard.checkCooldown(BrokerCallType.ORDER_HISTORY_IMPORT, lastCalledAt, FIXED_CLOCK))
                .doesNotThrowAnyException();
    }

    @Test
    void everyCallTypeUsesTheSameFiveSecondCooldown() {
        for (BrokerCallType type : BrokerCallType.values()) {
            assertThat(type.cooldown().toSeconds()).isEqualTo(5);
            assertThat(type.description()).isNotBlank();
        }
    }
}
