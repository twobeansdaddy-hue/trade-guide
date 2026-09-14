package com.tradeguide.exception;

/**
 * 증권사 연결을 삭제할 수 없는 도메인 상태일 때 발생한다. 현재는 이 연결의 스냅샷
 * 항목으로 승인해 아직 유효한({@code ACTIVE}) 개시 잔고 매매 기록이 남아 있는
 * 경우가 유일하다. 이 기록은 전용 취소 API로만 되돌릴 수 있으므로, 연결 삭제가
 * 임의로 원장을 지우지 않도록 사용자에게 취소를 먼저 요구한다.
 */
public class BrokerConnectionDeletionBlockedException extends RuntimeException {
    public BrokerConnectionDeletionBlockedException(String message) {
        super(message);
    }
}
