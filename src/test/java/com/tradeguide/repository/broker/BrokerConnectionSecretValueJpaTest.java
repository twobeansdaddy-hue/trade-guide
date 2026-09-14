package com.tradeguide.repository.broker;

import com.tradeguide.domain.broker.BrokerConnection;
import com.tradeguide.domain.broker.BrokerConnectionSecretValue;
import com.tradeguide.domain.broker.BrokerProvider;
import com.tradeguide.domain.member.Member;
import com.tradeguide.repository.member.MemberRepository;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 자격 증명 저장 모델의 제약을 DB 수준에서 확인한다.
 *
 * <p>"같은 연결에 같은 항목이 두 행이면 안 된다"는 규칙은 애플리케이션 조건문이 아니라
 * 유니크 제약이 지켜야 한다. 조건문은 동시 요청 사이를 통과하고, 그 결과 어느 값이 진짜
 * 자격 증명인지 정해지지 않은 연결이 남는다.
 */
@DataJpaTest
class BrokerConnectionSecretValueJpaTest {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private BrokerConnectionRepository brokerConnectionRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void rejectsDuplicateFieldKeyForTheSameConnection() {
        BrokerConnection connection = savedConnection();

        assertThatThrownBy(() -> entityManager.getEntityManager().createNativeQuery("""
                INSERT INTO broker_connection_secret_values
                    (broker_connection_id, field_key, ciphertext, initialization_vector, encryption_key_version)
                VALUES (?, 'clientId', 'other-ciphertext', 'other-iv', 1)
                """).setParameter(1, connection.getId()).executeUpdate())
                .isInstanceOf(PersistenceException.class);
    }

    /** 같은 항목 키라도 연결이 다르면 공존한다. 제약은 연결 단위다. */
    @Test
    void allowsTheSameFieldKeyAcrossDifferentConnections() {
        savedConnection();
        savedConnection();

        entityManager.flush();
        entityManager.clear();

        Number count = (Number) entityManager.getEntityManager()
                .createNativeQuery(
                        "SELECT COUNT(*) FROM broker_connection_secret_values WHERE field_key = 'clientId'")
                .getSingleResult();
        assertThat(count.longValue()).isEqualTo(2L);
    }

    /** 연결이 사라지면 자격 증명 행도 함께 사라진다. 남으면 주인 없는 암호문이 된다. */
    @Test
    void deletesCredentialValuesWithTheOwningConnection() {
        BrokerConnection connection = savedConnection();
        entityManager.flush();

        brokerConnectionRepository.delete(connection);
        brokerConnectionRepository.flush();
        entityManager.clear();

        Number remaining = (Number) entityManager.getEntityManager()
                .createNativeQuery("SELECT COUNT(*) FROM broker_connection_secret_values")
                .getSingleResult();
        assertThat(remaining.longValue()).isZero();
    }

    private BrokerConnection savedConnection() {
        String unique = "broker-" + System.nanoTime();
        Member member = memberRepository.save(new Member(unique + "@example.com", unique));
        BrokerConnection connection =
                new BrokerConnection(member, BrokerProvider.TOSS_SECURITIES, "개인 토스증권");
        connection.replaceSecretValues(List.of(
                new BrokerConnectionSecretValue("clientId", "encrypted-client-id", "client-id-iv", 1),
                new BrokerConnectionSecretValue("clientSecret", "encrypted-client-secret", "client-secret-iv", 1)
        ));
        return brokerConnectionRepository.saveAndFlush(connection);
    }
}
