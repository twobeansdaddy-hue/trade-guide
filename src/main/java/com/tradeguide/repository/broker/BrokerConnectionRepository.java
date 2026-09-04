package com.tradeguide.repository.broker;

import com.tradeguide.domain.broker.BrokerConnection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BrokerConnectionRepository extends JpaRepository<BrokerConnection, Long> {
    List<BrokerConnection> findAllByMember_IdOrderByCreatedAtDesc(Long memberId);

    Optional<BrokerConnection> findByMember_IdAndId(Long memberId, Long connectionId);
}
