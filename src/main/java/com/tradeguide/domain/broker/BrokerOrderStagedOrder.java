package com.tradeguide.domain.broker;

/**
 * 분류가 끝난 주문 한 건이다. 제공자가 보고한 값({@link BrokerOrderRecord})과, 이 서비스가
 * 내린 판정을 함께 들고 있다.
 *
 * <p>판정을 레코드 안에 섞지 않고 이 타입으로 감싸는 이유는 둘의 출처가 다르기 때문이다.
 * 레코드는 증권사가 말한 것이고 판정은 우리가 말한 것이다. 나중에 판정 규칙이 바뀌어도
 * 증권사가 말한 값은 그대로 남아 있어야 재해석할 수 있다.
 *
 * <p>{@code amountMismatch}는 평균가 × 수량과 체결 금액의 괴리가 평균가 반올림으로 설명되지
 * 않는다는 뜻이다. 이때도 값을 자체 재계산으로 보정하지 않는다. 증권사 화면과 값이 달라지면
 * 사용자가 어느 쪽을 믿어야 할지 알 수 없게 된다.
 */
public record BrokerOrderStagedOrder(
        BrokerOrderRecord record,
        String displayName,
        BrokerOrderStagingStatus stagingStatus,
        BrokerOrderSkipReason skipReason,
        String contentFingerprint,
        boolean amountMismatch,
        boolean feeUnknown,
        boolean buyTax
) {
    public BrokerOrderStagedOrder {
        if (record == null || stagingStatus == null) {
            throw new IllegalArgumentException("주문 이력 항목 분류 정보가 올바르지 않습니다.");
        }
        if (contentFingerprint == null || contentFingerprint.isBlank()) {
            throw new IllegalArgumentException("주문 이력 항목 지문은 필수입니다.");
        }
        if (stagingStatus == BrokerOrderStagingStatus.STAGED && skipReason != null) {
            throw new IllegalArgumentException("반영 후보 항목에는 제외 사유가 없어야 합니다.");
        }
        if (stagingStatus != BrokerOrderStagingStatus.STAGED && skipReason == null) {
            // 사유 없는 제외는 "조용한 누락"과 구분되지 않는다.
            throw new IllegalArgumentException("반영 후보가 아닌 항목에는 제외 사유가 필요합니다.");
        }
        if (displayName == null || displayName.isBlank()) {
            displayName = record.ticker();
        }
    }
}
