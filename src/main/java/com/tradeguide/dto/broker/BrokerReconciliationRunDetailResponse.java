package com.tradeguide.dto.broker;

import com.tradeguide.domain.broker.BrokerReconciliationRun;

import java.util.List;

/**
 * 정합성 점검 실행 한 건의 상세 응답이다. 요약에 종목별 줄을 더한 것이다.
 *
 * <p>줄 배열을 별도로 나눠 페이징하지 않는다. 보유 종목 수에 비례해 상한이 있어
 * 주문 이력 항목처럼 수천 건이 되지 않는다.
 */
public record BrokerReconciliationRunDetailResponse(
        BrokerReconciliationRunResponse run,
        List<BrokerReconciliationLineResponse> lines
) {
    public static BrokerReconciliationRunDetailResponse from(BrokerReconciliationRun run) {
        return new BrokerReconciliationRunDetailResponse(
                BrokerReconciliationRunResponse.from(run),
                run.getLines().stream().map(BrokerReconciliationLineResponse::from).toList()
        );
    }
}
