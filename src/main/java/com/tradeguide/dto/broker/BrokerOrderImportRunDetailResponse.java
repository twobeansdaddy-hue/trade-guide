package com.tradeguide.dto.broker;

import com.tradeguide.service.broker.BrokerOrderImportService;

import java.util.List;

/**
 * 실행 한 건의 상세 응답이다. 요약에 <b>승인 판정 집계</b>와 대조 결과를 더한 것이다.
 *
 * <p>주문 항목 배열은 담지 않는다. 실행 하나에 수천 건이 담길 수 있어 전부 실으면 응답이 수 MB에
 * 이르고, 그 크기는 조회 기간에 따라 상한이 없다. 항목의 정본은
 * {@code GET .../{runId}/items} 페이징 조회다.
 *
 * <p>항목을 뺄 수 있게 된 조건이 {@code approval}이다. 예전에는 승인 버튼의 판정(반영 후보 수,
 * 기준 시각 이전 제외 수, 승인 가능 여부)을 화면이 항목 배열을 세어 만들었다. 항목을 나눠 읽는
 * 순간 그 셈은 실행의 일부만 보고 내린 판정이 되어 조용히 틀린다. 그래서 판정에 필요한 집계를
 * 서버가 계산해 여기에 담는다. 크기를 줄이는 것보다 이쪽이 이 필드를 옮긴 진짜 이유다.
 *
 * <p>대조 결과는 계속 담는다. 보유 종목 수에 비례해 상한이 있고, 별도 호출로 나누면 화면이
 * 건너뛸 수 있다. 대조는 이 기능에서 건너뛰면 안 되는 유일한 안전망이다.
 */
public record BrokerOrderImportRunDetailResponse(
        BrokerOrderImportRunResponse run,
        BrokerOrderImportApprovalAssessmentResponse approval,
        List<BrokerOrderImportReconciliationLineResponse> reconciliation
) {
    public static BrokerOrderImportRunDetailResponse from(BrokerOrderImportService.RunDetail detail) {
        return new BrokerOrderImportRunDetailResponse(
                BrokerOrderImportRunResponse.from(detail.run()),
                BrokerOrderImportApprovalAssessmentResponse.from(detail.approval()),
                detail.reconciliationLines().stream()
                        .map(BrokerOrderImportReconciliationLineResponse::from)
                        .toList()
        );
    }
}
