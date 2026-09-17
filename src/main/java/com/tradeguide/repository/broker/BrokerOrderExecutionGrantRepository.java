package com.tradeguide.repository.broker;

import com.tradeguide.domain.broker.BrokerOrderExecutionGrant;
import com.tradeguide.domain.broker.BrokerOrderExecutionGrantStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BrokerOrderExecutionGrantRepository extends JpaRepository<BrokerOrderExecutionGrant, Long> {

    Optional<BrokerOrderExecutionGrant> findByMember_IdAndId(Long memberId, Long grantId);

    Optional<BrokerOrderExecutionGrant> findByMember_IdAndBrokerConnection_Id(Long memberId, Long brokerConnectionId);

    boolean existsByBrokerConnection_Id(Long brokerConnectionId);

    /** 트리거 스케줄러가 순회할 대상이다 - PAUSED/REVOKED는 애초에 대상이 아니다. */
    List<BrokerOrderExecutionGrant> findAllByStatus(BrokerOrderExecutionGrantStatus status);
}
