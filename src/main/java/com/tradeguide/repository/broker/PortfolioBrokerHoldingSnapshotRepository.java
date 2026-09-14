package com.tradeguide.repository.broker;

import com.tradeguide.domain.broker.PortfolioBrokerHoldingSnapshot;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PortfolioBrokerHoldingSnapshotRepository extends JpaRepository<PortfolioBrokerHoldingSnapshot, Long> {

    // 최근 스냅샷 응답은 항목·연결·계좌를 바로 사용하므로 함께 조회한다.
    @EntityGraph(attributePaths = {"items", "brokerConnection", "brokerAccount"})
    Optional<PortfolioBrokerHoldingSnapshot> findFirstByPortfolio_IdOrderBySyncedAtDesc(Long portfolioId);

    // 증권사 연결을 삭제할 때 이 연결에서 파생된 스냅샷만 함께 정리한다.
    @EntityGraph(attributePaths = {"items"})
    List<PortfolioBrokerHoldingSnapshot> findAllByBrokerConnection_Id(Long brokerConnectionId);

    // 파생 삭제로 항목까지 JPA cascade/orphanRemoval을 거치게 한다.
    void deleteAllByBrokerConnection_Id(Long brokerConnectionId);
}
