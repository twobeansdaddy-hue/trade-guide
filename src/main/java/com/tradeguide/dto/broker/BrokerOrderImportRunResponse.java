package com.tradeguide.dto.broker;

import com.tradeguide.domain.broker.BrokerOrderImportRun;
import com.tradeguide.domain.broker.BrokerOrderImportRunStatus;
import com.tradeguide.domain.broker.BrokerOrderReconciliationStatus;
import com.tradeguide.domain.broker.BrokerProvider;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 주문 이력 가져오기 실행 한 건의 요약 응답이다. 목록에 쓰며 항목은 담지 않는다.
 *
 * <p>요청 구간과 실제 조회 구간을 둘 다 돌려주는 이유는, 증권사가 주문일 기준으로 조회하기 때문에
 * 요청하지 않은 날짜의 체결이 결과에 섞일 수 있어서다. 화면이 그 사실을 설명할 수 있어야 한다.
 *
 * <p>계좌번호는 마스킹된 형태만 담고 계좌 일련번호·자격 증명·증권사 원문 메시지는 담지 않는다.
 */
public record BrokerOrderImportRunResponse(
        Long id,
        BrokerProvider provider,
        Long brokerConnectionId,
        String maskedAccountNumber,
        LocalDate requestedOrderedFrom,
        LocalDate requestedOrderedTo,
        LocalDate queriedOrderedFrom,
        LocalDate queriedOrderedTo,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        BrokerOrderImportRunStatus status,
        BrokerOrderImportCountsResponse counts,
        BrokerOrderReconciliationStatus reconciliationStatus,
        LocalDateTime reconciliationSnapshotSyncedAt,
        String failureCode,
        String providerRequestId,
        Long executedByMemberId,
        CoverageResponse coverage
) {
    public static BrokerOrderImportRunResponse from(BrokerOrderImportRun run) {
        return new BrokerOrderImportRunResponse(
                run.getId(),
                run.getProvider(),
                run.getBrokerConnection().getId(),
                run.getBrokerAccount().getMaskedAccountNumber(),
                run.getRequestedOrderedFrom(),
                run.getRequestedOrderedTo(),
                run.getQueriedOrderedFrom(),
                run.getQueriedOrderedTo(),
                run.getStartedAt(),
                run.getFinishedAt(),
                run.getStatus(),
                BrokerOrderImportCountsResponse.from(run.getCounts()),
                run.getReconciliationStatus(),
                run.getReconciliationSnapshotSyncedAt(),
                run.getFailureCode(),
                run.getProviderRequestId(),
                run.getExecutedByMemberId(),
                CoverageResponse.from(run)
        );
    }

    /**
     * 서버 구간 분할이 요청 구간을 끝까지 커버했는지 나타낸다. {@code fullyCovered=false}일 때만
     * {@code coveredOrderedTo}·{@code nextOrderedFrom}이 값을 갖는다. {@code nextOrderedFrom}은
     * 저장하지 않고 응답 생성 시점에 {@code coveredOrderedTo.plusDays(1)}로 계산한다.
     */
    public record CoverageResponse(
            boolean fullyCovered,
            LocalDate coveredOrderedTo,
            LocalDate nextOrderedFrom
    ) {
        public static CoverageResponse from(BrokerOrderImportRun run) {
            LocalDate coveredOrderedTo = run.getCoveredOrderedTo();
            if (coveredOrderedTo == null) {
                return new CoverageResponse(true, null, null);
            }
            return new CoverageResponse(false, coveredOrderedTo, coveredOrderedTo.plusDays(1));
        }
    }
}
