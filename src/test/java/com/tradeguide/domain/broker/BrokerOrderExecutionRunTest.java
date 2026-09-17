package com.tradeguide.domain.broker;

import com.tradeguide.domain.member.Member;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BrokerOrderExecutionRunTest {

    private final Member member = new Member("run-owner@example.com", "run-owner");
    private final BrokerConnection connection =
            new BrokerConnection(member, BrokerProvider.TOSS_SECURITIES, "개인 토스증권");
    private final BrokerOrderExecutionGrant grant = new BrokerOrderExecutionGrant(
            member, connection, "track-a-weekly-ma-crossover", new BigDecimal("0.10"), 5, "v1");

    @Test
    void startCopiesStrategyIdFromGrantAtStartTime() {
        BrokerOrderExecutionRun run = BrokerOrderExecutionRun.start(
                grant, "SOXL", BrokerOrderSide.BUY, new BigDecimal("10"), LocalDateTime.now());

        assertThat(run.getStrategyId()).isEqualTo("track-a-weekly-ma-crossover");
        assertThat(run.getStatus()).isEqualTo(BrokerOrderExecutionRunStatus.PENDING);
    }

    @Test
    void rejectsNonPositiveQuantity() {
        assertThatThrownBy(() -> BrokerOrderExecutionRun.start(
                grant, "SOXL", BrokerOrderSide.BUY, BigDecimal.ZERO, LocalDateTime.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBlankTicker() {
        assertThatThrownBy(() -> BrokerOrderExecutionRun.start(
                grant, " ", BrokerOrderSide.BUY, new BigDecimal("10"), LocalDateTime.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void markSubmittedRecordsCompletionTime() {
        BrokerOrderExecutionRun run = BrokerOrderExecutionRun.start(
                grant, "SOXL", BrokerOrderSide.BUY, new BigDecimal("10"), LocalDateTime.now());
        LocalDateTime completedAt = LocalDateTime.now();

        run.markSubmitted(completedAt, false);

        assertThat(run.getStatus()).isEqualTo(BrokerOrderExecutionRunStatus.SUBMITTED);
        assertThat(run.getCompletedAt()).isEqualTo(completedAt);
        assertThat(run.isDryRun()).isFalse();
    }

    @Test
    void markSubmittedAsDryRunIsDistinguishableFromLiveSubmission() {
        BrokerOrderExecutionRun run = BrokerOrderExecutionRun.start(
                grant, "SOXL", BrokerOrderSide.BUY, new BigDecimal("10"), LocalDateTime.now());

        run.markSubmitted(LocalDateTime.now(), true);

        assertThat(run.getStatus()).isEqualTo(BrokerOrderExecutionRunStatus.SUBMITTED);
        assertThat(run.isDryRun()).isTrue();
    }

    @Test
    void markRejectedBySafeguardRecordsReasonCode() {
        BrokerOrderExecutionRun run = BrokerOrderExecutionRun.start(
                grant, "SOXL", BrokerOrderSide.BUY, new BigDecimal("10"), LocalDateTime.now());

        run.markRejectedBySafeguard(LocalDateTime.now(), "MAX_DAILY_ORDER_COUNT_EXCEEDED");

        assertThat(run.getStatus()).isEqualTo(BrokerOrderExecutionRunStatus.REJECTED_BY_SAFEGUARD);
        assertThat(run.getFailureReasonCode()).isEqualTo("MAX_DAILY_ORDER_COUNT_EXCEEDED");
    }
}
