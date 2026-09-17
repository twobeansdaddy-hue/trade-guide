package com.tradeguide.service.broker;

import com.tradeguide.domain.broker.BrokerConnection;
import com.tradeguide.domain.broker.BrokerOrderExecutionGrant;
import com.tradeguide.domain.broker.BrokerOrderExecutionGrantStatus;
import com.tradeguide.domain.broker.BrokerProvider;
import com.tradeguide.domain.member.Member;
import com.tradeguide.repository.broker.BrokerConnectionRepository;
import com.tradeguide.repository.broker.BrokerOrderExecutionGrantRepository;
import com.tradeguide.repository.member.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BrokerOrderExecutionGrantServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private BrokerConnectionRepository brokerConnectionRepository;

    @Mock
    private BrokerOrderExecutionGrantRepository brokerOrderExecutionGrantRepository;

    private BrokerOrderExecutionGrantService service;

    private Member member;
    private BrokerConnection connection;

    @BeforeEach
    void setUp() {
        service = new BrokerOrderExecutionGrantService(
                memberRepository, brokerConnectionRepository, brokerOrderExecutionGrantRepository);
        member = new Member("service-owner@example.com", "service-owner");
        connection = new BrokerConnection(member, BrokerProvider.TOSS_SECURITIES, "개인 토스증권");
    }

    @Test
    void createsGrantForAllowedStrategy() {
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        when(brokerConnectionRepository.findByMember_IdAndId(1L, 2L)).thenReturn(Optional.of(connection));
        when(brokerOrderExecutionGrantRepository.existsByBrokerConnection_Id(2L)).thenReturn(false);
        when(brokerOrderExecutionGrantRepository.save(any(BrokerOrderExecutionGrant.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        BrokerOrderExecutionGrant grant = service.createGrant(
                1L, 2L, "track-a-weekly-ma-crossover", new BigDecimal("0.10"), 5, "v1");

        assertThat(grant.getStatus()).isEqualTo(BrokerOrderExecutionGrantStatus.ACTIVE);
    }

    @Test
    void rejectsStrategyNotOnWhitelist() {
        assertThatThrownBy(() -> service.createGrant(
                1L, 2L, "unverified-macro-overlay", new BigDecimal("0.10"), 5, "v1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("허용하지 않는 전략");
    }

    @Test
    void rejectsPositionSizeAboveSystemHardCap() {
        assertThatThrownBy(() -> service.createGrant(
                1L, 2L, "track-a-weekly-ma-crossover", new BigDecimal("0.21"), 5, "v1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("포지션 한도");
    }

    @Test
    void rejectsDailyOrderCountAboveSystemHardCap() {
        assertThatThrownBy(() -> service.createGrant(
                1L, 2L, "track-a-weekly-ma-crossover", new BigDecimal("0.10"), 11, "v1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("일일 주문 한도");
    }

    @Test
    void rejectsSecondGrantForSameConnection() {
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        when(brokerConnectionRepository.findByMember_IdAndId(1L, 2L)).thenReturn(Optional.of(connection));
        when(brokerOrderExecutionGrantRepository.existsByBrokerConnection_Id(2L)).thenReturn(true);

        assertThatThrownBy(() -> service.createGrant(
                1L, 2L, "track-a-weekly-ma-crossover", new BigDecimal("0.10"), 5, "v1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("이미 자동 주문 동의가 존재");
    }

    @Test
    void rejectsCreationForConnectionNotOwnedByMember() {
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        when(brokerConnectionRepository.findByMember_IdAndId(1L, 2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createGrant(
                1L, 2L, "track-a-weekly-ma-crossover", new BigDecimal("0.10"), 5, "v1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // 킬스위치(pause/revoke) 경로를 재개(reactivate) 경로보다 먼저 검증한다.

    @Test
    void pauseGrantOnlyAffectsOwnersGrant() {
        BrokerOrderExecutionGrant grant = new BrokerOrderExecutionGrant(
                member, connection, "track-a-weekly-ma-crossover", new BigDecimal("0.10"), 5, "v1");
        when(brokerOrderExecutionGrantRepository.findByMember_IdAndId(1L, 99L)).thenReturn(Optional.of(grant));

        BrokerOrderExecutionGrant paused = service.pauseGrant(1L, 99L);

        assertThat(paused.getStatus()).isEqualTo(BrokerOrderExecutionGrantStatus.PAUSED);
    }

    @Test
    void pauseGrantRejectsAccessForNonOwner() {
        when(brokerOrderExecutionGrantRepository.findByMember_IdAndId(77L, 99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.pauseGrant(77L, 99L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void revokeGrantRejectsAccessForNonOwner() {
        when(brokerOrderExecutionGrantRepository.findByMember_IdAndId(77L, 99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.revokeGrant(77L, 99L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void revokeGrantIsTerminal() {
        BrokerOrderExecutionGrant grant = new BrokerOrderExecutionGrant(
                member, connection, "track-a-weekly-ma-crossover", new BigDecimal("0.10"), 5, "v1");
        when(brokerOrderExecutionGrantRepository.findByMember_IdAndId(1L, 99L)).thenReturn(Optional.of(grant));

        BrokerOrderExecutionGrant revoked = service.revokeGrant(1L, 99L);

        assertThat(revoked.getStatus()).isEqualTo(BrokerOrderExecutionGrantStatus.REVOKED);
    }

    @Test
    void reactivateGrantOnlyWorksFromPausedState() {
        BrokerOrderExecutionGrant grant = new BrokerOrderExecutionGrant(
                member, connection, "track-a-weekly-ma-crossover", new BigDecimal("0.10"), 5, "v1");
        grant.pause();
        when(brokerOrderExecutionGrantRepository.findByMember_IdAndId(1L, 99L)).thenReturn(Optional.of(grant));

        BrokerOrderExecutionGrant reactivated = service.reactivateGrant(1L, 99L);

        assertThat(reactivated.getStatus()).isEqualTo(BrokerOrderExecutionGrantStatus.ACTIVE);
    }
}
