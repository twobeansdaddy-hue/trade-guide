package com.tradeguide.repository.broker;

import com.tradeguide.domain.broker.BrokerOrderImportItem;
import com.tradeguide.domain.broker.BrokerOrderOverrideDecision;
import com.tradeguide.domain.broker.BrokerOrderStagingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface BrokerOrderImportItemRepository extends JpaRepository<BrokerOrderImportItem, Long> {

    /**
     * 실행 한 건의 주문 항목을 조건에 맞게 한 페이지만 읽는다.
     *
     * <p>실행 하나에 수천 건이 담길 수 있으므로 상세 응답에 항목 전체를 싣는 대신 여기서 나눠 읽는다.
     * 조건이 비면 그 조건은 걸지 않으며, 종목 코드는 대소문자를 가리지 않고 정확히 일치할 때만 맞춘다.
     * 부분 일치로 두면 {@code A}가 거의 모든 종목을 통과시켜 거르기가 무의미해진다.
     *
     * <p>{@code run.id}로만 좁히므로 호출 전에 그 실행이 요청한 포트폴리오의 것인지 반드시 확인해야 한다.
     * 이 쿼리 자체는 소유권을 검사하지 않는다.
     */
    @Query("""
            SELECT item
            FROM BrokerOrderImportItem item
            WHERE item.run.id = :runId
              AND (:stagingStatus IS NULL OR item.stagingStatus = :stagingStatus)
              AND (:ticker IS NULL OR UPPER(item.ticker) = :ticker)
            """)
    Page<BrokerOrderImportItem> findRunItems(
            @Param("runId") Long runId,
            @Param("stagingStatus") BrokerOrderStagingStatus stagingStatus,
            @Param("ticker") String ticker,
            Pageable pageable
    );

    /**
     * 실행 범위 안에서 항목 하나를 찾는다. 항목 id만으로 찾으면 다른 실행의 항목에 닿을 수 있으므로
     * 호출자는 실행의 소유권을 먼저 확인한 뒤 이 조회를 쓴다.
     */
    Optional<BrokerOrderImportItem> findByIdAndRun_Id(Long id, Long runId);

    /**
     * 반영 후보 중 활성 개시 잔고 기준 시각 이후에 체결된 주문 수를 센다.
     *
     * <p>반영 후보는 <b>유효 상태</b>가 {@code STAGED}인 항목이다. 분류기가 처음부터
     * {@code STAGED}로 본 항목과, 의심 항목 중 반영 허용으로 재판정된 항목이 여기에 든다.
     * {@code BrokerOrderImportApprovalWriter}가 같은 규칙으로 후보를 고른다. 두 곳이 갈라지면
     * 화면이 안내한 반영 건수와 실제 반영 건수가 달라진다.
     *
     * <p>항목을 메모리로 끌어와 세지 않는 이유는, 그렇게 하면 응답에서 항목을 뺀 의미가 없어지기
     * 때문이다. 실행 하나에 수천 건이 담길 수 있고 그 전부를 세자고 읽으면 크기 문제가 응답에서
     * 힙으로 옮겨 갈 뿐이다.
     *
     * <p>{@code baseline}이 {@code null}이면 기준점이 없다는 뜻이며 아무것도 걸러내지 않는다.
     * {@code IS NULL} 검사에 {@code CAST}를 씌운 이유는 PostgreSQL이 타입을 알 수 없는
     * 파라미터를 거부하기 때문이다({@code could not determine data type of parameter}).
     * 파라미터가 비교식에만 쓰이면 타입이 정해지지만 {@code IS NULL}만으로는 정해지지 않는다.
     * 이 캐스트가 빠지면 개시 잔고를 한 번도 승인하지 않은 계좌의 상세 조회가 통째로 실패한다.
     *
     * <p>비교가 {@code >}인 것은 {@code BrokerOrderImportApprovalWriter}의
     * {@code filledAt.isAfter(baseline)}와 같은 경계를 쓰기 위해서다. 경계가 한 칸 어긋나면
     * 기준 시각에 정확히 체결된 주문이 개시 잔고와 이중 계상된다.
     *
     * <p>{@code run.id}로만 좁히므로 호출 전에 그 실행이 요청한 포트폴리오의 것인지 확인해야 한다.
     */
    @Query("""
            SELECT count(item)
            FROM BrokerOrderImportItem item
            WHERE item.run.id = :runId
              AND (item.stagingStatus = com.tradeguide.domain.broker.BrokerOrderStagingStatus.STAGED
                   OR EXISTS (
                       SELECT 1
                       FROM BrokerOrderImportItemOverride itemOverride
                       WHERE itemOverride.item.id = item.id
                         AND itemOverride.resultingStagingStatus
                             = com.tradeguide.domain.broker.BrokerOrderStagingStatus.STAGED
                   ))
              AND (CAST(:baseline AS Instant) IS NULL OR item.filledAt > :baseline)
            """)
    long countEligibleItems(@Param("runId") Long runId, @Param("baseline") Instant baseline);

    /**
     * 위 반영 대상 가운데 이미 매매 원장에 연계된 주문 수를 센다.
     *
     * <p>연계 여부를 실행이 아니라 <b>계좌</b> 기준으로 보는 것은
     * {@link BrokerOrderLedgerLinkRepository#findAllByBrokerAccount_IdAndStatus} 와 같은 이유다.
     * 같은 주문이 다른 실행으로 다시 조회돼도 원장에는 한 번만 들어가야 한다.
     */
    @Query("""
            SELECT count(item)
            FROM BrokerOrderImportItem item
            WHERE item.run.id = :runId
              AND (item.stagingStatus = com.tradeguide.domain.broker.BrokerOrderStagingStatus.STAGED
                   OR EXISTS (
                       SELECT 1
                       FROM BrokerOrderImportItemOverride itemOverride
                       WHERE itemOverride.item.id = item.id
                         AND itemOverride.resultingStagingStatus
                             = com.tradeguide.domain.broker.BrokerOrderStagingStatus.STAGED
                   ))
              AND (CAST(:baseline AS Instant) IS NULL OR item.filledAt > :baseline)
              AND EXISTS (
                  SELECT 1
                  FROM BrokerOrderLedgerLink link
                  WHERE link.brokerAccount.id = :brokerAccountId
                    AND link.status = com.tradeguide.domain.broker.BrokerOrderLedgerLinkStatus.ACTIVE
                    AND link.externalOrderId = item.externalOrderId
              )
            """)
    long countEligibleItemsAlreadyLinked(
            @Param("runId") Long runId,
            @Param("brokerAccountId") Long brokerAccountId,
            @Param("baseline") Instant baseline
    );

    /**
     * 실행의 재판정 수를 결정별로 센다. 재판정은 항목에 붙는 판단이라 승인 판정 집계와 같은 곳에서
     * 센다. {@code run.id}로만 좁히므로 소유권 확인은 호출자 책임이다.
     */
    @Query("""
            SELECT count(itemOverride)
            FROM BrokerOrderImportItemOverride itemOverride
            WHERE itemOverride.run.id = :runId
              AND itemOverride.decision = :decision
            """)
    long countOverridesByDecision(
            @Param("runId") Long runId,
            @Param("decision") BrokerOrderOverrideDecision decision
    );
}
