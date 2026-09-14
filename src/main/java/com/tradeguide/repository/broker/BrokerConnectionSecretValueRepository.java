package com.tradeguide.repository.broker;

import com.tradeguide.domain.broker.BrokerConnectionSecretValue;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 값 단위 암호화 키 로테이션 배치가 사용하는 직접 접근 경로다
 * (docs/agent-tasks/broker-credential-encryption-key-rotation.md §2.4, §12-4).
 *
 * <p>다른 모든 기능은 여전히 {@code BrokerConnectionRepository} → {@code BrokerConnection}
 * 애그리게이트를 통해서만 이 값에 접근한다. 로테이션 배치만 예외인 이유는, 전체 회원의
 * 구버전 행을 페이지 단위로 훑는 작업이 애그리게이트 경로로는 표현할 수 없기 때문이다.
 */
public interface BrokerConnectionSecretValueRepository extends JpaRepository<BrokerConnectionSecretValue, Long> {

    /**
     * 목표 버전이 아닌 행을 id 오름차순으로 페이지 조회한다. 호출자는 매번 0번 페이지만
     * 요청한다 — 처리된 행은 조건(<> targetVersion)에서 벗어나므로 다음 조회에서 자연히
     * 빠지고, 아직 처리하지 못한 행이 그 자리를 채운다(§6.1-2).
     */
    Page<BrokerConnectionSecretValue> findByEncryptionKeyVersionNotOrderByIdAsc(int targetVersion, Pageable pageable);

    long countByEncryptionKeyVersionNot(int targetVersion);

    /** 사전 점검이 실제 존재하는 구버전 키를 확인하는 데 쓴다(§5-2). 값은 담지 않는다. */
    @Query("SELECT DISTINCT s.encryptionKeyVersion FROM BrokerConnectionSecretValue s WHERE s.encryptionKeyVersion <> :targetVersion")
    List<Integer> findDistinctEncryptionKeyVersionsExcluding(@Param("targetVersion") int targetVersion);

    /**
     * 조건부 UPDATE다. {@code expectedCurrentVersion}이 그사이 바뀌었으면(사용자가
     * {@code replaceSecretValues}로 자격 증명을 교체한 경우 등) 영향받은 행이 0건이 되고,
     * 호출자는 이를 실패가 아니라 스킵으로 집계한다(§6.1-2, §6.3).
     */
    @Modifying
    @Query("""
            UPDATE BrokerConnectionSecretValue s
            SET s.ciphertext = :ciphertext,
                s.initializationVector = :initializationVector,
                s.encryptionKeyVersion = :newVersion
            WHERE s.id = :id
              AND s.encryptionKeyVersion = :expectedCurrentVersion
            """)
    int rotateEncryptionIfVersionMatches(
            @Param("id") Long id,
            @Param("ciphertext") String ciphertext,
            @Param("initializationVector") String initializationVector,
            @Param("newVersion") int newVersion,
            @Param("expectedCurrentVersion") int expectedCurrentVersion
    );
}
