package com.tradeguide.repository.broker;

import com.tradeguide.domain.broker.BrokerOrderImportRun;
import com.tradeguide.domain.broker.BrokerOrderImportRunStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface BrokerOrderImportRunRepository extends JpaRepository<BrokerOrderImportRun, Long> {

    /**
     * 목록 응답은 연결·계좌 표시 값을 바로 사용하므로 함께 조회한다. 항목은 목록에 싣지 않는다.
     *
     * <p>함께 읽는 것은 단일 연관뿐이라 페이징이 DB에서 그대로 수행된다. 컬렉션을 함께
     * 페치하면 페이징이 메모리로 내려가므로, 실행 항목은 여기서 읽지 않는다.
     */
    @EntityGraph(attributePaths = {"brokerConnection", "brokerAccount"})
    Page<BrokerOrderImportRun> findAllByPortfolio_Id(Long portfolioId, Pageable pageable);

    @EntityGraph(attributePaths = {"brokerConnection", "brokerAccount"})
    Optional<BrokerOrderImportRun> findByPortfolio_IdAndId(Long portfolioId, Long runId);

    /**
     * 실행 승인과 의심 항목 재판정이 실행 행을 쓰기 잠금으로 읽는다.
     *
     * <p>같은 실행에서 두 쓰기가 겹치면 한쪽이 커밋할 때까지 다른 쪽이 기다린다. 그래서 승인이
     * 읽은 재판정 집합과 승인 응답의 건수가 커밋된 상태와 어긋나지 않고, 같은 실행의 동시 승인도
     * 유니크 제약 경합까지 가지 않고 순서대로 처리된다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT run FROM BrokerOrderImportRun run WHERE run.portfolio.id = :portfolioId AND run.id = :runId")
    Optional<BrokerOrderImportRun> findWithLockByPortfolioIdAndId(
            @Param("portfolioId") Long portfolioId,
            @Param("runId") Long runId
    );

    /**
     * 같은 계좌의 직전 실행을 항목까지 함께 읽는다. 같은 구간을 두 번 실행했을 때 주문 식별자가
     * 바뀌었는지 보려면 직전 실행이 무엇을 봤는지 알아야 한다.
     */
    @EntityGraph(attributePaths = {"items"})
    Optional<BrokerOrderImportRun> findFirstByBrokerAccount_IdAndStatusOrderByStartedAtDesc(
            Long brokerAccountId,
            BrokerOrderImportRunStatus status
    );

    /**
     * 중복 호출 가드의 쿨다운 기준 시각을 구한다. 상태(성공/실패)에 관계없이 최신 시도 하나를
     * 본다. 실패한 시도도 이미 증권사를 호출했으므로 쿨다운 계산에 포함해야 한다.
     */
    Optional<BrokerOrderImportRun> findFirstByPortfolio_IdOrderByStartedAtDesc(Long portfolioId);
}
