package com.tradeguide.service.broker;

import com.tradeguide.domain.broker.BrokerConnection;
import com.tradeguide.domain.broker.BrokerOrderExecutionGrant;
import com.tradeguide.domain.broker.BrokerOrderExecutionRun;
import com.tradeguide.domain.broker.BrokerOrderExecutionRunStatus;
import com.tradeguide.domain.broker.BrokerOrderSide;
import com.tradeguide.domain.broker.BrokerProvider;
import com.tradeguide.domain.member.Member;
import com.tradeguide.repository.broker.BrokerOrderExecutionRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BrokerOrderExecutionServiceTest {

    @Mock
    private BrokerOrderExecutionRunRepository brokerOrderExecutionRunRepository;

    @Mock
    private BrokerProviderRegistry brokerProviderRegistry;

    @Mock
    private BrokerCredentialLoader brokerCredentialLoader;

    @Mock
    private BrokerOrderSubmissionProvider brokerOrderSubmissionProvider;

    private Member member;
    private BrokerConnection connection;
    private BrokerOrderExecutionGrant grant;

    @BeforeEach
    void setUp() {
        member = new Member("execution-owner@example.com", "execution-owner");
        connection = new BrokerConnection(member, BrokerProvider.TOSS_SECURITIES, "개인 토스증권");
        grant = new BrokerOrderExecutionGrant(
                member, connection, "track-a-weekly-ma-crossover", new BigDecimal("0.10"), 5, "v1");
        when(brokerOrderExecutionRunRepository.save(any(BrokerOrderExecutionRun.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private BrokerOrderExecutionService serviceWith(boolean liveEnabled) {
        return new BrokerOrderExecutionService(
                brokerOrderExecutionRunRepository, brokerProviderRegistry, brokerCredentialLoader, liveEnabled);
    }

    // 안전장치 네 가지를 고정 순서로 검증한다 - 아래 네 테스트는 그 순서를 그대로 따른다.

    @Test
    void rejectsWhenGrantIsNotActive() {
        grant.pause();
        BrokerOrderExecutionService service = serviceWith(false);

        BrokerOrderExecutionRun run = service.executeOrder(
                grant, "track-a-weekly-ma-crossover", "SOXL", BrokerOrderSide.BUY,
                new BigDecimal("10"), new BigDecimal("1000"), new BigDecimal("100000"));

        assertThat(run.getStatus()).isEqualTo(BrokerOrderExecutionRunStatus.REJECTED_BY_SAFEGUARD);
        assertThat(run.getFailureReasonCode()).isEqualTo(BrokerOrderExecutionService.REASON_GRANT_NOT_ACTIVE);
        verifyNoInteractions(brokerProviderRegistry);
    }

    @Test
    void rejectsWhenStrategyIdDoesNotMatchGrant() {
        BrokerOrderExecutionService service = serviceWith(false);

        BrokerOrderExecutionRun run = service.executeOrder(
                grant, "some-other-strategy", "SOXL", BrokerOrderSide.BUY,
                new BigDecimal("10"), new BigDecimal("1000"), new BigDecimal("100000"));

        assertThat(run.getStatus()).isEqualTo(BrokerOrderExecutionRunStatus.REJECTED_BY_SAFEGUARD);
        assertThat(run.getFailureReasonCode()).isEqualTo(BrokerOrderExecutionService.REASON_STRATEGY_MISMATCH);
    }

    @Test
    void rejectsWhenPositionSizeExceedsGrantLimit() {
        BrokerOrderExecutionService service = serviceWith(false);

        // 10% 한도인데 요청 명목금액이 계좌 자산의 20%
        BrokerOrderExecutionRun run = service.executeOrder(
                grant, "track-a-weekly-ma-crossover", "SOXL", BrokerOrderSide.BUY,
                new BigDecimal("10"), new BigDecimal("20000"), new BigDecimal("100000"));

        assertThat(run.getStatus()).isEqualTo(BrokerOrderExecutionRunStatus.REJECTED_BY_SAFEGUARD);
        assertThat(run.getFailureReasonCode()).isEqualTo(BrokerOrderExecutionService.REASON_POSITION_SIZE_EXCEEDED);
    }

    @Test
    void rejectsWhenDailyOrderCountReachesGrantLimit() {
        when(brokerOrderExecutionRunRepository.countByGrant_IdAndStartedAtGreaterThanEqual(any(), any()))
                .thenReturn(5L); // grant.maxDailyOrderCount == 5
        BrokerOrderExecutionService service = serviceWith(false);

        BrokerOrderExecutionRun run = service.executeOrder(
                grant, "track-a-weekly-ma-crossover", "SOXL", BrokerOrderSide.BUY,
                new BigDecimal("10"), new BigDecimal("1000"), new BigDecimal("100000"));

        assertThat(run.getStatus()).isEqualTo(BrokerOrderExecutionRunStatus.REJECTED_BY_SAFEGUARD);
        assertThat(run.getFailureReasonCode())
                .isEqualTo(BrokerOrderExecutionService.REASON_DAILY_ORDER_COUNT_EXCEEDED);
    }

    @Test
    void doesNotCallProviderWhenLiveDisabledEvenIfSafeguardsPass() {
        when(brokerOrderExecutionRunRepository.countByGrant_IdAndStartedAtGreaterThanEqual(any(), any()))
                .thenReturn(0L);
        BrokerOrderExecutionService service = serviceWith(false);

        BrokerOrderExecutionRun run = service.executeOrder(
                grant, "track-a-weekly-ma-crossover", "SOXL", BrokerOrderSide.BUY,
                new BigDecimal("10"), new BigDecimal("1000"), new BigDecimal("100000"));

        assertThat(run.getStatus()).isEqualTo(BrokerOrderExecutionRunStatus.SUBMITTED);
        assertThat(run.isDryRun()).isTrue();
        verifyNoInteractions(brokerProviderRegistry);
        verifyNoInteractions(brokerCredentialLoader);
    }

    @Test
    void callsProviderWhenLiveEnabledAndSafeguardsPass() {
        when(brokerOrderExecutionRunRepository.countByGrant_IdAndStartedAtGreaterThanEqual(any(), any()))
                .thenReturn(0L);
        when(brokerProviderRegistry.requireOrderSubmissionProvider(BrokerProvider.TOSS_SECURITIES))
                .thenReturn(brokerOrderSubmissionProvider);
        var credentials = org.mockito.Mockito.mock(com.tradeguide.domain.broker.BrokerCredentials.class);
        when(brokerCredentialLoader.load(connection)).thenReturn(credentials);

        var account = org.mockito.Mockito.mock(com.tradeguide.domain.broker.BrokerAccount.class);
        when(account.isActive()).thenReturn(true);
        connection.reconcileVerifiedAccounts(java.util.List.of(account));
        when(brokerCredentialLoader.loadAccountSequence(account)).thenReturn("acct-1");
        when(brokerOrderSubmissionProvider.submit(any(), any()))
                .thenReturn(BrokerOrderSubmissionResult.submitted("provider-order-1"));

        BrokerOrderExecutionService service = serviceWith(true);

        BrokerOrderExecutionRun run = service.executeOrder(
                grant, "track-a-weekly-ma-crossover", "SOXL", BrokerOrderSide.BUY,
                new BigDecimal("10"), new BigDecimal("1000"), new BigDecimal("100000"));

        assertThat(run.getStatus()).isEqualTo(BrokerOrderExecutionRunStatus.SUBMITTED);
        assertThat(run.isDryRun()).isFalse();
    }

    @Test
    void marksFailedWhenLiveEnabledAndProviderRejects() {
        when(brokerOrderExecutionRunRepository.countByGrant_IdAndStartedAtGreaterThanEqual(any(), any()))
                .thenReturn(0L);
        when(brokerProviderRegistry.requireOrderSubmissionProvider(BrokerProvider.TOSS_SECURITIES))
                .thenReturn(brokerOrderSubmissionProvider);
        var credentials = org.mockito.Mockito.mock(com.tradeguide.domain.broker.BrokerCredentials.class);
        when(brokerCredentialLoader.load(connection)).thenReturn(credentials);

        var account = org.mockito.Mockito.mock(com.tradeguide.domain.broker.BrokerAccount.class);
        when(account.isActive()).thenReturn(true);
        connection.reconcileVerifiedAccounts(java.util.List.of(account));
        when(brokerCredentialLoader.loadAccountSequence(account)).thenReturn("acct-1");
        when(brokerOrderSubmissionProvider.submit(any(), any()))
                .thenReturn(BrokerOrderSubmissionResult.failed("INSUFFICIENT_BUYING_POWER"));

        BrokerOrderExecutionService service = serviceWith(true);

        BrokerOrderExecutionRun run = service.executeOrder(
                grant, "track-a-weekly-ma-crossover", "SOXL", BrokerOrderSide.BUY,
                new BigDecimal("10"), new BigDecimal("1000"), new BigDecimal("100000"));

        assertThat(run.getStatus()).isEqualTo(BrokerOrderExecutionRunStatus.FAILED);
        assertThat(run.getFailureReasonCode()).isEqualTo("INSUFFICIENT_BUYING_POWER");
    }
}
