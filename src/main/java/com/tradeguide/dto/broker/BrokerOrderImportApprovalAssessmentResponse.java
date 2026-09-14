package com.tradeguide.dto.broker;

import com.tradeguide.domain.broker.BrokerOrderImportApprovalAssessment;
import com.tradeguide.domain.broker.BrokerOrderImportApprovalBlocker;

import java.time.LocalDateTime;

/**
 * 승인 판정 집계 응답이다. 화면이 항목 배열을 세지 않고도 "지금 승인할 수 있는가, 없다면 왜,
 * 승인하면 몇 건이 원장에 들어가는가"에 답할 수 있게 하는 값만 담는다.
 *
 * <p>{@code approvable}은 조회 시점의 판정이지 승인 보장이 아니다. 조회와 승인 사이에 원장이
 * 바뀔 수 있어 승인 시점에 서버가 같은 검사를 다시 한다. 화면은 이 값으로 버튼을 열되,
 * 승인 응답이 거부로 오는 경우를 여전히 처리해야 한다.
 *
 * <p>재판정 세 필드({@code overrideAllowedCount}, {@code overrideKeptExcludedCount},
 * {@code unresolvedSuspectedCount})는 추가 필드다. 기존 필드의 이름과 의미는 바꾸지 않았으며,
 * 재판정이 없는 실행에서는 기존 값이 예전과 같다.
 */
public record BrokerOrderImportApprovalAssessmentResponse(
        int stagedCount,
        int eligibleCount,
        int baselineExcludedCount,
        int alreadyLinkedCount,
        int writableCount,
        int excludedCount,
        int suspectedCount,
        int overrideAllowedCount,
        int overrideKeptExcludedCount,
        int unresolvedSuspectedCount,
        int amountMismatchCount,
        LocalDateTime baselineAt,
        boolean fullyCovered,
        boolean coverageAcknowledgementRequired,
        boolean approvable,
        BrokerOrderImportApprovalBlocker blocker
) {
    public static BrokerOrderImportApprovalAssessmentResponse from(
            BrokerOrderImportApprovalAssessment assessment) {
        return new BrokerOrderImportApprovalAssessmentResponse(
                assessment.stagedCount(),
                assessment.eligibleCount(),
                assessment.baselineExcludedCount(),
                assessment.alreadyLinkedCount(),
                assessment.writableCount(),
                assessment.excludedCount(),
                assessment.suspectedCount(),
                assessment.overrideAllowedCount(),
                assessment.overrideKeptExcludedCount(),
                assessment.unresolvedSuspectedCount(),
                assessment.amountMismatchCount(),
                assessment.baselineAt(),
                assessment.fullyCovered(),
                assessment.coverageAcknowledgementRequired(),
                assessment.approvable(),
                assessment.blocker()
        );
    }
}
