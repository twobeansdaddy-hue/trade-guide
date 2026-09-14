package com.tradeguide.repository.broker;

import com.tradeguide.domain.broker.PortfolioBrokerHoldingImport;
import com.tradeguide.domain.broker.PortfolioBrokerHoldingImportStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PortfolioBrokerHoldingImportRepository extends JpaRepository<PortfolioBrokerHoldingImport, Long> {

    Optional<PortfolioBrokerHoldingImport> findBySnapshotItem_Id(Long snapshotItemId);

    Optional<PortfolioBrokerHoldingImport> findByPortfolio_IdAndId(Long portfolioId, Long importId);

    /**
     * 감사 이력을 페이지 단위로 읽는다. 정렬은 호출자가 {@link Pageable}로 지정하며,
     * 승인 시각만으로는 순서가 정해지지 않는다. 일괄 반영은 여러 건이 같은 승인 시각을
     * 갖기 때문에, 페이지 경계에서 항목이 중복되거나 빠지지 않으려면 id 같은 동점 기준이
     * 함께 있어야 한다.
     */
    Page<PortfolioBrokerHoldingImport> findAllByPortfolio_Id(Long portfolioId, Pageable pageable);

    /**
     * 일괄 개시 잔고 반영에서 이미 승인·취소된 종목을 걸러내기 위한 조회다.
     * 종목 후보 수만큼 질의를 반복하지 않도록 티커 목록으로 한 번에 읽고, 시장 비교는
     * 호출자가 수행한다(같은 티커가 다른 시장에 존재할 수 있다).
     */
    List<PortfolioBrokerHoldingImport> findAllByPortfolio_IdAndTickerIn(
            Long portfolioId,
            Collection<String> tickers
    );

    /**
     * 증권사 주문 이력 반영의 기준점이다. 활성 개시 잔고가 있으면 그 중 가장 최근 승인 시각
     * 이후에 체결된 주문만 원장 반영 후보로 삼는다.
     */
    Optional<PortfolioBrokerHoldingImport> findFirstByPortfolio_IdAndStatusOrderByApprovedAtDesc(
            Long portfolioId,
            PortfolioBrokerHoldingImportStatus status
    );

    // 증권사 연결 삭제 전에 이 연결의 스냅샷 항목으로 승인된 개시 잔고 이력을 확인한다.
    List<PortfolioBrokerHoldingImport> findAllBySnapshotItem_Snapshot_BrokerConnection_Id(Long brokerConnectionId);
}
