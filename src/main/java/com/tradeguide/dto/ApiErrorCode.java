package com.tradeguide.dto;

/**
 * 클라이언트가 오류 메시지 문구에 의존하지 않고 분기할 수 있는 오류 구분 코드다.
 *
 * <p>기존 응답의 {@code message}와 HTTP 상태는 그대로 유지하고, 코드만 추가로 제공한다.
 */
public enum ApiErrorCode {

    /** 시장 데이터 제공자의 서버 설정 전제 조건이 없어 조회를 시작하지 못했다. 재시도로 해결되지 않는다. */
    MARKET_DATA_PROVIDER_NOT_CONFIGURED,

    /** 외부 시장 데이터 제공자 호출이 실패했다. 재시도할 수 있다. */
    MARKET_DATA_UNAVAILABLE,

    /** 외부 시장 데이터 제공자의 요청 제한에 걸렸다. 잠시 후 재시도할 수 있다. */
    MARKET_DATA_RATE_LIMIT_EXCEEDED,

    /**
     * 시장 데이터 제공자가 허용 IP 또는 자격 증명 문제로 접근을 거부했다. 서버 IP를
     * 제공자의 허용 목록에 등록하거나 자격 증명을 다시 확인해야 해결되며, 단순 재시도로는
     * 해결되지 않는다.
     */
    MARKET_DATA_PROVIDER_ACCESS_DENIED,

    /** 시장 데이터를 조회했지만 전략 판단에 쓸 수 없을 정도로 오래된 데이터다. */
    MARKET_DATA_STALE,

    /** 증권사 연결 기능의 서버 설정 또는 회원 연결 전제 조건이 없다. */
    BROKER_CONNECTION_UNAVAILABLE,

    /**
     * 요청한 동작을 이 증권사 제공자에서 아직 열지 않았다. 제공자가 기능을 선언하지 않았거나
     * 어댑터가 등록되어 있지 않다는 뜻이며, 재시도로 해결되지 않는다.
     * 화면은 {@code GET /api/broker-providers}의 {@code availableCapabilities}로
     * 이 상태를 미리 판단할 수 있다.
     */
    BROKER_CAPABILITY_UNAVAILABLE,

    /**
     * 증권사에서 가져온 종목의 시장을 Trade Guide 매매 원장이 아직 표현할 수 없다.
     * 재시도로 해결되지 않으며, 해당 종목은 조회·비교로만 볼 수 있다.
     * 화면은 메시지 문구가 아니라 이 코드로 분기한다.
     */
    BROKER_LEDGER_MARKET_UNSUPPORTED,

    /** 요청한 포트폴리오가 없거나 이 회원 소유가 아니다. 재시도로 해결되지 않는다. */
    PORTFOLIO_NOT_FOUND,

    /** 포트폴리오에 연결된 증권사 계좌 링크가 없다. 링크를 먼저 만들어야 해결된다. */
    PORTFOLIO_BROKER_LINK_NOT_FOUND,

    /**
     * 증권사 연결이 {@code CONNECTED} 상태가 아니라서 이 동작을 진행할 수 없다.
     * 연결을 다시 검증하면 해결될 수 있으므로 409로 매핑한다.
     */
    BROKER_CONNECTION_REVERIFICATION_REQUIRED,

    /** 실행의 보유 수량 대조 결과가 일치하지 않거나 재생 검증에 실패해 승인을 진행할 수 없다. */
    RECONCILIATION_MISMATCH,

    /** 활성 개시 잔고 기준 시각이 반영 후보를 모두 걸러내 승인할 항목이 남지 않았다. */
    BASELINE_EXCLUDED,

    /** 승인 직전 원장 재생 검증에서 초과 매도나 수량 불일치가 발견됐다. */
    REPLAY_VALIDATION_FAILED,

    /** 같은 포트폴리오의 같은 증권사 조회 동작이 이미 진행 중이다. 완료 후 다시 시도할 수 있다. */
    BROKER_CALL_IN_PROGRESS,

    /** 직전 같은 동작 호출 이후 최소 대기 시간이 지나지 않았다. 잠시 후 다시 시도할 수 있다. */
    BROKER_CALL_COOLDOWN,

    /**
     * 실행이 요청 구간을 끝까지 커버하지 못했고 보유 수량 대조로 이력 누락 가능성을 배제할 수
     * 없는데, 불완전한 채로 승인한다는 명시적 확인이 없다.
     */
    ORDER_IMPORT_COVERAGE_ACKNOWLEDGEMENT_REQUIRED,

    /**
     * 재판정할 수 없는 주문 항목이다. 사람 판단이 필요한 의심 항목(수기 매매 중복 의심, 식별자
     * 중복 의심)만 재판정할 수 있으며, 재시도로 해결되지 않는다.
     */
    ORDER_IMPORT_ITEM_NOT_OVERRIDABLE,

    /**
     * 이 주문 항목은 이미 다른 결정으로 재판정됐다. 재판정은 감사 기록이라 바꾸지 않으며,
     * 결정을 바꾸려면 주문 이력을 다시 가져와 새 실행의 항목에서 판단해야 한다.
     */
    ORDER_IMPORT_OVERRIDE_CONFLICT,

    /**
     * 현재 보유하지 않은 종목에 포트폴리오 전략 프로필 재정의를 설정하려 했다. 먼저 해당
     * 종목을 보유해야 해결된다.
     */
    PORTFOLIO_ASSET_NOT_HELD,

    /** 삭제하거나 조회하려는 포트폴리오 전략 프로필 재정의가 없다. */
    PORTFOLIO_ASSET_STRATEGY_PROFILE_NOT_FOUND,

    /**
     * 실행 가능한 {@code TradingStrategy} 구현이 없는 투자 트랙이다. 재정의로 저장할 수
     * 없으며, 재시도로 해결되지 않는다.
     */
    UNSUPPORTED_INVESTMENT_TRACK,

    /** 이미 이 포트폴리오에 등록된 후보 종목을 다시 등록하려 했다. */
    PORTFOLIO_CANDIDATE_ASSET_ALREADY_EXISTS,

    /** 조회하거나 삭제하려는 포트폴리오 후보 종목이 없다. */
    PORTFOLIO_CANDIDATE_ASSET_NOT_FOUND
}
