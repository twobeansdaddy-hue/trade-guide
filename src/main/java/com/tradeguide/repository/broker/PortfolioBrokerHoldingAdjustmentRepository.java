package com.tradeguide.repository.broker;

import com.tradeguide.domain.broker.PortfolioBrokerHoldingAdjustment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PortfolioBrokerHoldingAdjustmentRepository extends JpaRepository<PortfolioBrokerHoldingAdjustment, Long> {

    Optional<PortfolioBrokerHoldingAdjustment> findBySnapshotItem_Id(Long snapshotItemId);

    Optional<PortfolioBrokerHoldingAdjustment> findByPortfolio_IdAndId(Long portfolioId, Long adjustmentId);

    /**
     * 감사 이력을 페이지 단위로 읽는다. 정렬은 호출자가 {@link Pageable}로 지정하며,
     * 승인 시각만으로는 순서가 정해지지 않으므로 id 같은 동점 기준이 함께 있어야 한다.
     */
    Page<PortfolioBrokerHoldingAdjustment> findAllByPortfolio_Id(Long portfolioId, Pageable pageable);

    // 증권사 연결 삭제 전에 이 연결의 스냅샷 항목으로 승인된 잔고 조정 이력을 확인한다.
    List<PortfolioBrokerHoldingAdjustment> findAllBySnapshotItem_Snapshot_BrokerConnection_Id(Long brokerConnectionId);
}
