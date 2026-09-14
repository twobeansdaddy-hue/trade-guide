package com.tradeguide.domain.broker;

/**
 * 일괄 개시 잔고 반영에서 한 종목이 빠진 구체적 사유다.
 *
 * <p>일괄 반영은 "다 됐다" 또는 "다 안 됐다"로만 답해서는 안 된다. 사용자는 자기 계좌의
 * 어떤 종목이 왜 안 들어갔는지 알아야 다음 행동(수동 등록, 취소 이력 확인, 종목 상태 확인)을
 * 고를 수 있다. 그래서 제외는 조용한 누락이 아니라 사유가 붙은 결과로 돌려준다.
 *
 * <p>증권사 원문 오류 메시지나 계좌 식별 값은 어떤 사유에도 담지 않는다.
 */
public enum BrokerOpeningBalanceSkipReason {

    /** 최신 스냅샷 비교 결과가 {@code ONLY_IN_BROKER}가 아니다. 개시 잔고 대상이 아니다. */
    NOT_ONLY_IN_BROKER,

    /**
     * Trade Guide 매매 원장이 현재 표현할 수 없는 시장의 종목이다.
     * 조회·스냅샷·비교로는 계속 볼 수 있지만 원장에는 반영하지 않는다
     * ({@link BrokerProvider#getLedgerWritableMarkets()}).
     */
    UNSUPPORTED_MARKET,

    /** 같은 종목의 활성 개시 잔고 승인 이력이 이미 있다. */
    ALREADY_APPROVED,

    /** 같은 종목의 개시 잔고를 사용자가 이미 취소한 적이 있다. 일괄 반영이 그 결정을 되돌리지 않는다. */
    PREVIOUSLY_REVOKED,

    /** 같은 종목의 매매 원장 기록이 이미 있다. 개시 잔고를 얹으면 원장 이력과 충돌한다. */
    LEDGER_CONFLICT,

    /** 자산 카탈로그의 상장 상태가 활성이 아니다. */
    INACTIVE_LISTING
}
