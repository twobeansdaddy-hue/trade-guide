package com.tradeguide.repository.broker;

import com.tradeguide.domain.broker.BrokerOrderExecutionRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface BrokerOrderExecutionRunRepository extends JpaRepository<BrokerOrderExecutionRun, Long> {

    List<BrokerOrderExecutionRun> findAllByGrant_IdOrderByStartedAtDesc(Long grantId);

    /** 일일 주문 한도(서킷브레이커) 판정에 쓴다 - {@code from} 이후 시작된 실행 시도 건수. */
    long countByGrant_IdAndStartedAtGreaterThanEqual(Long grantId, LocalDateTime from);
}
