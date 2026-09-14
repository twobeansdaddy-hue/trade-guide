package com.tradeguide.domain.broker;

/**
 * 주문 이력 가져오기 실행 한 건의 결과 상태다.
 *
 * <p>이 조회·스테이징 단계에서 실행이 만들 수 있는 상태는 스테이징 완료와 실패뿐이다.
 * 승인·취소 여부는 이 상태에 포함되지 않는다. 같은 실행이 부분적으로만 승인되거나
 * 재승인으로 계속 바뀔 수 있어, 실행 한 번에 상태 하나로 표현할 수 없기 때문이다.
 * 그 사실은 {@link BrokerOrderLedgerLink}가 실행이 아니라 주문 한 건 단위로 담는다.
 */
public enum BrokerOrderImportRunStatus {
    /** 조회를 시작했고 아직 끝나지 않았다. */
    RUNNING,
    /** 조회와 분류가 끝났다. 매매 원장은 한 행도 바뀌지 않았다. */
    STAGED,
    /** 조회나 분류가 실패했다. 실패 사유는 정제된 코드로만 남는다. */
    FAILED
}
