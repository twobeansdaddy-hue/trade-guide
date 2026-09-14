package com.tradeguide.repository.broker;

import com.tradeguide.domain.broker.BrokerConnection;
import com.tradeguide.domain.broker.BrokerConnectionSecretValue;
import com.tradeguide.domain.broker.BrokerProvider;
import com.tradeguide.domain.member.Member;
import com.tradeguide.repository.member.MemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class BrokerConnectionRepositoryTest {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private BrokerConnectionRepository brokerConnectionRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void persistsEncryptedCredentialValuesPerFieldAndDeletesThemWithConnection() {
        Member member = memberRepository.save(
                new Member("broker@example.com", "broker-user")
        );
        BrokerConnection connection = new BrokerConnection(
                member,
                BrokerProvider.TOSS_SECURITIES,
                "개인 토스증권"
        );
        connection.replaceSecretValues(List.of(
                new BrokerConnectionSecretValue("clientId", "encrypted-client-id", "client-id-iv", 1),
                new BrokerConnectionSecretValue("clientSecret", "encrypted-client-secret", "client-secret-iv", 1)
        ));

        BrokerConnection savedConnection = brokerConnectionRepository.saveAndFlush(connection);
        entityManager.clear();

        Number secretCount = (Number) entityManager.getEntityManager()
                .createNativeQuery("SELECT COUNT(*) FROM broker_connection_secret_values")
                .getSingleResult();
        assertThat(secretCount.longValue()).isEqualTo(2L);

        brokerConnectionRepository.deleteById(savedConnection.getId());
        brokerConnectionRepository.flush();

        Number remainingSecretCount = (Number) entityManager.getEntityManager()
                .createNativeQuery("SELECT COUNT(*) FROM broker_connection_secret_values")
                .getSingleResult();
        assertThat(remainingSecretCount.longValue()).isZero();
    }
}
