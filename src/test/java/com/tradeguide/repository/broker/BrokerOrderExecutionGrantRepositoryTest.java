package com.tradeguide.repository.broker;

import com.tradeguide.domain.broker.BrokerConnection;
import com.tradeguide.domain.broker.BrokerOrderExecutionGrant;
import com.tradeguide.domain.broker.BrokerProvider;
import com.tradeguide.domain.member.Member;
import com.tradeguide.repository.member.MemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class BrokerOrderExecutionGrantRepositoryTest {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private BrokerConnectionRepository brokerConnectionRepository;

    @Autowired
    private BrokerOrderExecutionGrantRepository brokerOrderExecutionGrantRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void findsGrantByMemberAndConnection() {
        Member member = memberRepository.save(new Member("grant-repo@example.com", "grant-repo-user"));
        BrokerConnection connection = brokerConnectionRepository.saveAndFlush(
                new BrokerConnection(member, BrokerProvider.TOSS_SECURITIES, "개인 토스증권"));
        BrokerOrderExecutionGrant grant = new BrokerOrderExecutionGrant(
                member, connection, "track-a-weekly-ma-crossover", new BigDecimal("0.10"), 5, "v1");
        brokerOrderExecutionGrantRepository.saveAndFlush(grant);
        entityManager.clear();

        Optional<BrokerOrderExecutionGrant> found = brokerOrderExecutionGrantRepository
                .findByMember_IdAndBrokerConnection_Id(member.getId(), connection.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getStrategyId()).isEqualTo("track-a-weekly-ma-crossover");
    }

    @Test
    void doesNotFindGrantForDifferentMember() {
        Member owner = memberRepository.save(new Member("grant-owner-repo@example.com", "grant-owner-repo"));
        Member stranger = memberRepository.save(new Member("grant-stranger-repo@example.com", "grant-stranger-repo"));
        BrokerConnection connection = brokerConnectionRepository.saveAndFlush(
                new BrokerConnection(owner, BrokerProvider.TOSS_SECURITIES, "개인 토스증권"));
        BrokerOrderExecutionGrant grant = new BrokerOrderExecutionGrant(
                owner, connection, "track-a-weekly-ma-crossover", new BigDecimal("0.10"), 5, "v1");
        brokerOrderExecutionGrantRepository.saveAndFlush(grant);
        entityManager.clear();

        Optional<BrokerOrderExecutionGrant> found = brokerOrderExecutionGrantRepository
                .findByMember_IdAndId(stranger.getId(), grant.getId());

        assertThat(found).isEmpty();
    }

    @Test
    void enforcesOneGrantPerBrokerConnection() {
        Member member = memberRepository.save(new Member("grant-unique@example.com", "grant-unique-user"));
        BrokerConnection connection = brokerConnectionRepository.saveAndFlush(
                new BrokerConnection(member, BrokerProvider.TOSS_SECURITIES, "개인 토스증권"));
        brokerOrderExecutionGrantRepository.saveAndFlush(new BrokerOrderExecutionGrant(
                member, connection, "track-a-weekly-ma-crossover", new BigDecimal("0.10"), 5, "v1"));

        BrokerOrderExecutionGrant duplicate = new BrokerOrderExecutionGrant(
                member, connection, "track-a-weekly-ma-crossover", new BigDecimal("0.05"), 3, "v1");

        assertThatThrownBy(() -> brokerOrderExecutionGrantRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void existsByBrokerConnectionIdReflectsPersistedGrant() {
        Member member = memberRepository.save(new Member("grant-exists@example.com", "grant-exists-user"));
        BrokerConnection connection = brokerConnectionRepository.saveAndFlush(
                new BrokerConnection(member, BrokerProvider.TOSS_SECURITIES, "개인 토스증권"));

        assertThat(brokerOrderExecutionGrantRepository.existsByBrokerConnection_Id(connection.getId())).isFalse();

        brokerOrderExecutionGrantRepository.saveAndFlush(new BrokerOrderExecutionGrant(
                member, connection, "track-a-weekly-ma-crossover", new BigDecimal("0.10"), 5, "v1"));

        assertThat(brokerOrderExecutionGrantRepository.existsByBrokerConnection_Id(connection.getId())).isTrue();
    }
}
