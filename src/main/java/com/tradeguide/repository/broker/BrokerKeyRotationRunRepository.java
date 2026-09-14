package com.tradeguide.repository.broker;

import com.tradeguide.domain.broker.BrokerKeyRotationRun;
import com.tradeguide.domain.broker.BrokerKeyRotationRunStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BrokerKeyRotationRunRepository extends JpaRepository<BrokerKeyRotationRun, Long> {

    /**
     * 진행 중인 실행이 있는지 확인한다. DB의 부분 유니크 인덱스가 같은 규칙을 저장 시점에
     * 한 번 더 강제하므로(§6.3), 이 조회는 "이어서 실행할 대상을 찾는" 용도이지 유일한
     * 방어선이 아니다.
     */
    Optional<BrokerKeyRotationRun> findFirstByStatus(BrokerKeyRotationRunStatus status);
}
