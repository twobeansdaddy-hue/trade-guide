package com.tradeguide.repository.broker;

import com.tradeguide.domain.broker.BrokerOrderLedgerLink;
import com.tradeguide.domain.broker.BrokerOrderLedgerLinkStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BrokerOrderLedgerLinkRepository extends JpaRepository<BrokerOrderLedgerLink, Long> {

    /**
     * 계좌 기준으로 활성 반영 링크를 읽는다. 포트폴리오 기준이 아닌 이유는 링크가 나중에 다른
     * 포트폴리오로 바뀔 수 있어서다. 계좌를 기준으로 삼아야 같은 주문이 두 포트폴리오에
     * 각각 들어가는 진짜 중복을 막을 수 있다.
     */
    @EntityGraph(attributePaths = {"item"})
    List<BrokerOrderLedgerLink> findAllByBrokerAccount_IdAndStatus(
            Long brokerAccountId,
            BrokerOrderLedgerLinkStatus status
    );

    /** 승인 취소는 실행(run) 단위로 요청되므로, 그 실행이 만든 활성 링크만 골라 읽는다. */
    @EntityGraph(attributePaths = {"item"})
    List<BrokerOrderLedgerLink> findAllByRun_IdAndStatus(
            Long runId,
            BrokerOrderLedgerLinkStatus status
    );
}
