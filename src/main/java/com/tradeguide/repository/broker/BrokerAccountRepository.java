package com.tradeguide.repository.broker;

import com.tradeguide.domain.broker.BrokerAccount;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 계좌 단위 암호화 키 로테이션 배치가 사용하는 직접 접근 경로다
 * (docs/agent-tasks/broker-credential-encryption-key-rotation.md §2.4, §12-4).
 *
 * <p>다른 모든 기능은 여전히 {@code BrokerConnection} 애그리게이트를 통해서만 계좌에
 * 접근한다. 로테이션 배치만 예외인 이유는 §{@link BrokerConnectionSecretValueRepository}와 같다.
 */
public interface BrokerAccountRepository extends JpaRepository<BrokerAccount, Long> {

    Page<BrokerAccount> findByEncryptionKeyVersionNotOrderByIdAsc(int targetVersion, Pageable pageable);

    long countByEncryptionKeyVersionNot(int targetVersion);

    /** 사전 점검이 실제 존재하는 구버전 키를 확인하는 데 쓴다(§5-2). 값은 담지 않는다. */
    @Query("SELECT DISTINCT a.encryptionKeyVersion FROM BrokerAccount a WHERE a.encryptionKeyVersion <> :targetVersion")
    List<Integer> findDistinctEncryptionKeyVersionsExcluding(@Param("targetVersion") int targetVersion);

    /**
     * 조건부 UPDATE다. SET 절에는 암호문·IV·키 버전만 둔다. {@code status}, {@code detached_at},
     * {@code masked_account_number}는 절대 건드리지 않는다 — 그렇지 않으면 {@code DETACHED}
     * 계좌가 로테이션만으로 {@code ACTIVE}로 되돌아가는 부작용이 생긴다(§2.4, §6.1-3).
     */
    @Modifying
    @Query("""
            UPDATE BrokerAccount a
            SET a.encryptedAccountSequence = :ciphertext,
                a.accountSequenceInitializationVector = :initializationVector,
                a.encryptionKeyVersion = :newVersion
            WHERE a.id = :id
              AND a.encryptionKeyVersion = :expectedCurrentVersion
            """)
    int rotateEncryptionIfVersionMatches(
            @Param("id") Long id,
            @Param("ciphertext") String ciphertext,
            @Param("initializationVector") String initializationVector,
            @Param("newVersion") int newVersion,
            @Param("expectedCurrentVersion") int expectedCurrentVersion
    );
}
