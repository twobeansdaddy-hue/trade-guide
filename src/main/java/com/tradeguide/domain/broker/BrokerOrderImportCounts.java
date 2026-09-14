package com.tradeguide.domain.broker;

/**
 * 주문 이력 가져오기 실행 한 건의 사유별 건수다.
 *
 * <p>사유를 하나로 뭉치지 않는 이유는 사유마다 사용자가 할 일이 다르기 때문이다.
 * "3건 제외"는 아무 행동도 만들지 못하지만, "부분 체결 보류 2건, 수수료 미상 1건"은
 * 다시 조회할지 문의할지를 사용자가 스스로 판단하게 한다.
 *
 * <p>앞쪽 열두 개는 서로 배타적인 분류이고 다음 불변식을 만족한다.
 *
 * <pre>{@code
 * fetchedCount = stagedCount + notFilledCount + pendingSettlementCount + controlRecordCount
 *              + missingExecutionTimeCount + missingAveragePriceCount + unknownStatusCount
 *              + unsupportedMarketCount + unsupportedCurrencyCount
 *              + alreadyImportedCount + manualOverlapSuspectedCount + duplicateSuspectedCount
 * }</pre>
 *
 * <p>뒤쪽 다섯 개는 분류가 아니라 <b>신호</b>다. 같은 주문이 신호 여러 개를 동시에 켤 수 있고
 * 배타 분류와도 겹치므로 위 합계에 넣지 않는다.
 *
 * <ul>
 *   <li>{@code unknownEnumCount} — 제공자 명세가 바뀌었을 수 있다는 신호</li>
 *   <li>{@code duplicateFetchCount} — 커서 순회가 같은 주문을 다시 돌려준 횟수</li>
 *   <li>{@code amountMismatchCount} — 평균가 × 수량과 체결 금액의 괴리가 반올림으로 설명되지 않는다</li>
 *   <li>{@code feeUnknownCount} — 수수료가 비어 있어 취득원가가 그만큼 낮게 잡힌다</li>
 *   <li>{@code buyTaxCount} — 매수인데 세금이 붙어 있다. 세금은 원장 계산에 넣지 않으므로 반드시 알린다</li>
 * </ul>
 *
 * <p>{@code openOrderCount}는 진행 중 주문 수다. 원장 반영 대상이 아니라 표시 전용이며
 * 종료 주문 조회와 별개 경로로 얻으므로 역시 합계에 넣지 않는다.
 */
public record BrokerOrderImportCounts(
        int fetchedCount,
        int stagedCount,
        int notFilledCount,
        int pendingSettlementCount,
        int controlRecordCount,
        int missingExecutionTimeCount,
        int missingAveragePriceCount,
        int unknownStatusCount,
        int unsupportedMarketCount,
        int unsupportedCurrencyCount,
        int alreadyImportedCount,
        int manualOverlapSuspectedCount,
        int duplicateSuspectedCount,
        int unknownEnumCount,
        int duplicateFetchCount,
        int amountMismatchCount,
        int feeUnknownCount,
        int buyTaxCount,
        int openOrderCount
) {
    public BrokerOrderImportCounts {
        if (fetchedCount < 0
                || stagedCount < 0
                || notFilledCount < 0
                || pendingSettlementCount < 0
                || controlRecordCount < 0
                || missingExecutionTimeCount < 0
                || missingAveragePriceCount < 0
                || unknownStatusCount < 0
                || unsupportedMarketCount < 0
                || unsupportedCurrencyCount < 0
                || alreadyImportedCount < 0
                || manualOverlapSuspectedCount < 0
                || duplicateSuspectedCount < 0
                || unknownEnumCount < 0
                || duplicateFetchCount < 0
                || amountMismatchCount < 0
                || feeUnknownCount < 0
                || buyTaxCount < 0
                || openOrderCount < 0) {
            throw new IllegalArgumentException("주문 이력 가져오기 건수는 0 이상이어야 합니다.");
        }
        if (fetchedCount != sum(
                stagedCount, notFilledCount, pendingSettlementCount, controlRecordCount,
                missingExecutionTimeCount, missingAveragePriceCount, unknownStatusCount,
                unsupportedMarketCount, unsupportedCurrencyCount,
                alreadyImportedCount, manualOverlapSuspectedCount, duplicateSuspectedCount)) {
            // 여기서 막지 않으면 어딘가에서 주문이 조용히 사라진 실행이 그대로 저장된다.
            throw new IllegalArgumentException("조회 건수와 사유별 건수 합계가 일치하지 않습니다.");
        }
    }

    public static BrokerOrderImportCounts empty() {
        return new BrokerOrderImportCounts(
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    /** 배타 분류 건수의 합. {@code fetchedCount}와 같아야 한다. */
    public int classifiedCount() {
        return sum(
                stagedCount, notFilledCount, pendingSettlementCount, controlRecordCount,
                missingExecutionTimeCount, missingAveragePriceCount, unknownStatusCount,
                unsupportedMarketCount, unsupportedCurrencyCount,
                alreadyImportedCount, manualOverlapSuspectedCount, duplicateSuspectedCount);
    }

    private static int sum(int... values) {
        int total = 0;
        for (int value : values) {
            total += value;
        }
        return total;
    }
}
