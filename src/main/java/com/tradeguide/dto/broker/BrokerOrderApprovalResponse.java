package com.tradeguide.dto.broker;

import com.tradeguide.service.broker.BrokerOrderImportApprovalWriter;

import java.time.LocalDateTime;

/**
 * 주문 이력 실행 승인 한 번의 응답이다. 안전하게 노출할 수 있는 건수·시각·회원 ID만 담으며,
 * 자격 증명·계좌 일련번호·증권사 원문 오류는 어디에도 담지 않는다.
 *
 * <p>{@code writtenCount}가 0이어도 오류가 아니다. 이미 반영된 실행을 다시 승인하면
 * {@code alreadyLinkedCount}만 올라가고 새 매매 기록은 만들지 않는다.
 *
 * <p>{@code overrideAllowedCount}는 추가 필드다. 반영 후보 가운데 반영 허용 재판정으로 들어온
 * 의심 항목 수이며(기준 시각 필터 전), 재판정이 없으면 0이다.
 */
public record BrokerOrderApprovalResponse(
        Long runId,
        int eligibleCount,
        int baselineExcludedCount,
        int alreadyLinkedCount,
        int writtenCount,
        LocalDateTime approvedAt,
        Long approvedByMemberId,
        LocalDateTime baselineAt,
        boolean coverageAcknowledged,
        int overrideAllowedCount
) {
    public static BrokerOrderApprovalResponse from(BrokerOrderImportApprovalWriter.ApprovalResult result) {
        return new BrokerOrderApprovalResponse(
                result.runId(),
                result.eligibleCount(),
                result.baselineExcludedCount(),
                result.alreadyLinkedCount(),
                result.writtenCount(),
                result.approvedAt(),
                result.approvedByMemberId(),
                result.baselineAt(),
                result.coverageAcknowledged(),
                result.overrideAllowedCount()
        );
    }
}
