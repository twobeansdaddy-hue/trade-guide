package com.tradeguide.domain.broker;

/**
 * 실행 한 건을 지금 승인할 수 없는 이유다. 승인 가능하면 값이 없다.
 *
 * <p>이유를 불리언 하나로 뭉치지 않는 까닭은 사용자가 할 일이 이유마다 다르기 때문이다.
 * "승인 불가"만 보여 주면 다음 행동이 없지만, "대조 불일치"는 스냅샷을 갱신하게 하고
 * "기준 시각 이후 주문 없음"은 조회 기간을 넓히게 한다.
 *
 * <p>여기 값은 {@link com.tradeguide.service.broker.BrokerOrderImportApprovalWriter}가 실제로
 * 승인을 거부하는 조건과 같은 규칙에서 나온다. 두 곳의 규칙이 갈라지면 화면은 열려 있는데
 * 서버가 거부하거나, 서버는 허용하는데 화면이 막는 상태가 된다.
 */
public enum BrokerOrderImportApprovalBlocker {
    /** 조회가 실패했거나 아직 끝나지 않아 승인할 대상 자체가 없다. */
    RUN_NOT_STAGED,
    /** 보유 수량 대조가 불일치다. 이력이 잘렸거나 어떤 주문이 두 번 계상됐다는 신호다. */
    RECONCILIATION_MISMATCHED,
    /** 재생 검증이 성립하지 않았다. 이력 앞부분이 잘렸을 때 나타난다. */
    RECONCILIATION_REPLAY_FAILED,
    /** 반영 후보가 한 건도 없다. 전부 정상 제외이거나 이미 반영된 경우다. */
    NO_STAGED_ITEMS,
    /** 반영 후보는 있으나 전부 활성 개시 잔고 기준 시각 이전 체결이라 반영 대상이 아니다. */
    ALL_BEFORE_BASELINE,
    /**
     * 이 실행이 요청 구간을 끝까지 커버하지 못했고(fullyCovered=false), 보유 수량 대조가
     * {@code MATCHED}가 아니어서 이력 누락 가능성을 대조만으로 배제할 수 없는데, 사용자가
     * 아직 그 사실을 확인하지 않았다.
     */
    INCOMPLETE_COVERAGE_NOT_ACKNOWLEDGED
}
