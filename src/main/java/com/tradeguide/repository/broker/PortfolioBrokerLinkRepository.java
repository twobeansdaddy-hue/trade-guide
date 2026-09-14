package com.tradeguide.repository.broker;

import com.tradeguide.domain.portfolio.PortfolioBrokerLink;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
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

    /**
     * 재검증에서 분리된 계좌를 가리키던 링크만 정리한다. 계좌 행은 과거 스냅샷이 참조하므로
     * 남기고, 더 이상 조회할 수 없는 계좌를 가리키는 링크만 끊는다.
     * 호출부는 빈 목록으로 호출하지 않는다.
     */
    void deleteAllByBrokerAccount_IdIn(Collection<Long> brokerAccountIds);
}
