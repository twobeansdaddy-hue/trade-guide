package com.tradeguide.repository.broker;

import com.tradeguide.domain.broker.BrokerReconciliationRun;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BrokerReconciliationRunRepository extends JpaRepository<BrokerReconciliationRun, Long> {

    /**
     * 목록 응답은 연결·계좌 표시 값을 바로 쓰므로 함께 조회한다. 줄은 목록에 싣지 않으므로
     * 함께 페치하지 않는다. 컬렉션을 함께 페치하면 페이징이 메모리로 내려간다.
     */
    @EntityGraph(attributePaths = {"brokerConnection", "brokerAccount"})
    Page<BrokerReconciliationRun> findAllByPortfolio_Id(Long portfolioId, Pageable pageable);

    @EntityGraph(attributePaths = {"brokerConnection", "brokerAccount", "lines.reasons"})
    Optional<BrokerReconciliationRun> findByPortfolio_IdAndId(Long portfolioId, Long runId);
}
