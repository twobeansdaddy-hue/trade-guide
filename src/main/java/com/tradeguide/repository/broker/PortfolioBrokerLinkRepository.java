package com.tradeguide.repository.broker;

import com.tradeguide.domain.portfolio.PortfolioBrokerLink;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PortfolioBrokerLinkRepository extends JpaRepository<PortfolioBrokerLink, Long> {

    // 응답 매핑과 미리보기가 연결·계좌를 바로 사용하므로 함께 조회한다.
    @EntityGraph(attributePaths = {"brokerConnection", "brokerAccount"})
    List<PortfolioBrokerLink> findAllByPortfolio_IdOrderByLinkedAtAsc(Long portfolioId);

    @EntityGraph(attributePaths = {"brokerConnection", "brokerAccount"})
    Optional<PortfolioBrokerLink> findByPortfolio_Id(Long portfolioId);

    Optional<PortfolioBrokerLink> findByPortfolio_IdAndBrokerConnection_Id(Long portfolioId, Long brokerConnectionId);

    void deleteAllByBrokerConnection_Id(Long brokerConnectionId);
}
