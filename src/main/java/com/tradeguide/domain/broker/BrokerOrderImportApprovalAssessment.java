package com.tradeguide.domain.broker;

import java.time.LocalDateTime;

/**
 * 실행 한 건에 대한 <b>승인 판정 집계</b>다. 화면이 항목 배열을 세어 승인 버튼을 열지 말지
 * 정하지 않도록, 그 판정에 필요한 값을 서버가 계산해 담는다.
 *
 * <p>이 값을 서버로 옮긴 이유는 응답 크기가 아니라 정확성이다. 항목을 나눠 읽는 순간
 * 화면이 가진 배열은 실행의 일부일 뿐이고, 그 일부를 세어 내린 판정은 조용히 틀린다.
 * 승인은 매매 원장을 바꾸는 유일한 경로이므로 그 판정이 조용히 틀리면 안 된다.
 *
 * <p>이 record는 아무것도 바꾸지 않는다. 판정을 <b>보고</b>할 뿐이며, 실제 승인 가부는
 * {@link com.tradeguide.service.broker.BrokerOrderImportApprovalWriter}가 승인 시점에
 * 다시 검사한다. 조회와 승인 사이에 원장이 바뀔 수 있으므로 이 값을 승인의 근거로 삼지 않는다.
 *
 * <p>건수의 관계는 다음과 같다. 반영 후보는 유효 상태가 {@code STAGED}인 항목, 즉 분류기가
 * 처음부터 반영 후보로 본 항목과 반영 허용으로 재판정된 의심 항목을 합친 것이다.
 * 재판정이 하나도 없으면 첫 식은 예전과 같은 {@code stagedCount = eligibleCount + baselineExcludedCount}다.
 *
 * <pre>{@code
 * stagedCount + overrideAllowedCount = eligibleCount + baselineExcludedCount
 * eligibleCount                      = writableCount + alreadyLinkedCount
 * suspectedCount                     = overrideAllowedCount + overrideKeptExcludedCount + unresolvedSuspectedCount
 * }</pre>
 *
 * @param stagedCount           분류기가 반영 후보로 분류한 주문 수({@link BrokerOrderStagingStatus#STAGED}). 재판정과 무관한 원래 값이다
 * @param eligibleCount         반영 후보 중 활성 개시 잔고 기준 시각 이후 체결된 주문 수
 * @param baselineExcludedCount 기준 시각 이전 체결이라 반영 대상에서 빠진 반영 후보 수
 * @param alreadyLinkedCount    반영 대상이지만 이미 원장에 연계돼 다시 쓰지 않을 주문 수
 * @param writableCount         지금 승인하면 새로 기록될 주문 수
 * @param excludedCount         거래가 아니거나 반영에 필요한 값이 없어 제외된 주문 수
 * @param suspectedCount        사람 판단이 필요한 주문 수(수기 매매 중복 의심 + 식별자 중복 의심). 재판정과 무관한 원래 값이다
 * @param overrideAllowedCount  의심 항목 중 반영 허용으로 재판정돼 반영 후보에 든 주문 수
 * @param overrideKeptExcludedCount 의심 항목 중 제외 유지로 재판정된 주문 수
 * @param unresolvedSuspectedCount  아직 재판정하지 않은 의심 주문 수. 이 주문들은 반영되지 않는다
 * @param amountMismatchCount   평균가 × 수량과 체결 금액이 어긋나 값을 믿을 수 없는 주문 수
 * @param baselineAt            활성 개시 잔고 기준 시각. 없으면 {@code null}이며 아무것도 걸러지지 않는다
 * @param fullyCovered          서버 구간 분할이 요청 구간을 끝까지 커버했는지 여부
 * @param coverageAcknowledgementRequired 불완전 이력 확인이 아직 필요한지 여부.
 *                              {@code blocker}가 {@link BrokerOrderImportApprovalBlocker#INCOMPLETE_COVERAGE_NOT_ACKNOWLEDGED}일 때 참이다
 * @param approvable            지금 승인 요청을 보내면 통과할지 여부
 * @param blocker               승인할 수 없는 이유. {@code approvable}가 참이면 {@code null}
 */
public record BrokerOrderImportApprovalAssessment(
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

    public BrokerOrderImportApprovalAssessment {
        if (stagedCount < 0 || eligibleCount < 0 || alreadyLinkedCount < 0
                || overrideAllowedCount < 0 || overrideKeptExcludedCount < 0 || unresolvedSuspectedCount < 0) {
            throw new IllegalArgumentException("주문 이력 승인 판정 건수는 0 이상이어야 합니다.");
        }
        if (eligibleCount > stagedCount + overrideAllowedCount) {
            // 기준 시각으로 걸러낸 결과가 후보보다 많을 수는 없다. 여기서 막지 않으면
            // 화면이 실제보다 많은 건수를 반영 대상으로 안내한다.
            throw new IllegalArgumentException("반영 대상 건수가 반영 후보 건수를 넘을 수 없습니다.");
        }
        if (alreadyLinkedCount > eligibleCount) {
            throw new IllegalArgumentException("이미 연계된 건수가 반영 대상 건수를 넘을 수 없습니다.");
        }
        if (approvable != (blocker == null)) {
            throw new IllegalArgumentException("승인 가능 여부와 불가 사유는 서로를 배타적으로 설명해야 합니다.");
        }
    }

    /**
     * 실행의 저장된 사유별 건수와, 조회 시점에 계산한 건수로 판정을 만든다.
     *
     * <p>{@code stagedCount}를 인자로 받지 않고 {@link BrokerOrderImportCounts}에서 꺼내는 이유는,
     * 배타 분류 건수는 이미 실행에 저장돼 있어 다시 세면 두 값이 갈라질 수 있어서다.
     * 반대로 {@code eligibleCount}·{@code alreadyLinkedCount}·재판정 건수는 저장할 수 없다. 개시
     * 잔고 기준점, 원장 연계, 재판정은 실행 이후에도 생기므로 조회 시점에 계산해야 맞다.
     */
    public static BrokerOrderImportApprovalAssessment of(
            BrokerOrderImportRunStatus runStatus,
            BrokerOrderReconciliationStatus reconciliationStatus,
            BrokerOrderImportCounts counts,
            int eligibleCount,
            int alreadyLinkedCount,
            int overrideAllowedCount,
            int overrideKeptExcludedCount,
            LocalDateTime baselineAt,
            boolean fullyCovered,
            boolean coverageAcknowledged
    ) {
        int stagedCount = counts.stagedCount();
        int excludedCount = counts.notFilledCount()
                + counts.pendingSettlementCount()
                + counts.controlRecordCount()
                + counts.missingExecutionTimeCount()
                + counts.missingAveragePriceCount()
                + counts.unknownStatusCount()
                + counts.unsupportedMarketCount()
                + counts.unsupportedCurrencyCount()
                + counts.alreadyImportedCount();
        int suspectedCount = counts.manualOverlapSuspectedCount() + counts.duplicateSuspectedCount();
        if (overrideAllowedCount + overrideKeptExcludedCount > suspectedCount) {
            // 재판정은 의심 항목에만 붙는다. 이 관계가 깨졌다면 화면에 보여 줄 남은 의심 건수가 음수가 된다.
            throw new IllegalArgumentException("재판정 건수가 의심 항목 건수를 넘을 수 없습니다.");
        }
        int candidateCount = stagedCount + overrideAllowedCount;

        BrokerOrderImportApprovalBlocker blocker = resolveBlocker(
                runStatus, reconciliationStatus, candidateCount, eligibleCount,
                fullyCovered, coverageAcknowledged);

        return new BrokerOrderImportApprovalAssessment(
                stagedCount,
                eligibleCount,
                candidateCount - eligibleCount,
                alreadyLinkedCount,
                eligibleCount - alreadyLinkedCount,
                excludedCount,
                suspectedCount,
                overrideAllowedCount,
                overrideKeptExcludedCount,
                suspectedCount - overrideAllowedCount - overrideKeptExcludedCount,
                counts.amountMismatchCount(),
                baselineAt,
                fullyCovered,
                blocker == BrokerOrderImportApprovalBlocker.INCOMPLETE_COVERAGE_NOT_ACKNOWLEDGED,
                blocker == null,
                blocker
        );
    }

    /**
     * 승인 거부 조건을 {@code BrokerOrderImportApprovalWriter}와 같은 순서로 본다. 순서가 다르면
     * 같은 실행에 대해 화면이 말하는 이유와 서버가 돌려주는 이유가 달라진다.
     *
     * <p>불완전 이력 확인 검사는 맨 끝에 둔다 — 기존 다섯 단계를 전부 통과해 승인 가능이 될
     * 상황에서만 추가로 본다. 대조가 {@code MATCHED}인 부분 커버 실행은 이 검사를 건너뛴다.
     * 대조가 이미 이력 누락을 잡아내는 사례에 이중으로 경고하지 않기 위해서다.
     *
     * <p>재판정하지 않은 의심 항목은 승인을 막지 않는다. 그 항목은 반영 후보가 아니므로 반영되지
     * 않을 뿐이고, 나머지 반영 후보는 기존과 같이 승인할 수 있다.
     */
    private static BrokerOrderImportApprovalBlocker resolveBlocker(
            BrokerOrderImportRunStatus runStatus,
            BrokerOrderReconciliationStatus reconciliationStatus,
            int candidateCount,
            int eligibleCount,
            boolean fullyCovered,
            boolean coverageAcknowledged
    ) {
        if (runStatus != BrokerOrderImportRunStatus.STAGED) {
            return BrokerOrderImportApprovalBlocker.RUN_NOT_STAGED;
        }
        if (reconciliationStatus == BrokerOrderReconciliationStatus.MISMATCHED) {
            return BrokerOrderImportApprovalBlocker.RECONCILIATION_MISMATCHED;
        }
        if (reconciliationStatus == BrokerOrderReconciliationStatus.REPLAY_FAILED) {
            return BrokerOrderImportApprovalBlocker.RECONCILIATION_REPLAY_FAILED;
        }
        if (eligibleCount == 0) {
            // 후보가 애초에 없었던 것과, 후보는 있었으나 기준 시각이 전부 걸러낸 것은 다른 문제다.
            // 앞은 조회 기간을, 뒤는 개시 잔고 기준점을 다시 보게 해야 한다.
            return candidateCount > 0
                    ? BrokerOrderImportApprovalBlocker.ALL_BEFORE_BASELINE
                    : BrokerOrderImportApprovalBlocker.NO_STAGED_ITEMS;
        }
        if (!fullyCovered && reconciliationStatus != BrokerOrderReconciliationStatus.MATCHED
                && !coverageAcknowledged) {
            return BrokerOrderImportApprovalBlocker.INCOMPLETE_COVERAGE_NOT_ACKNOWLEDGED;
        }
        return null;
    }
}
