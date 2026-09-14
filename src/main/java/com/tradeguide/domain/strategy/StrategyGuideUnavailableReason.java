package com.tradeguide.domain.strategy;

/**
 * 보유 종목 가이드 계산에서 한 종목을 계산하지 못한 이유를 화면이 메시지 문구가 아니라
 * 코드로 분기할 수 있게 구분한다.
 */
public enum StrategyGuideUnavailableReason {

    /** 이 종목에 적용할 전략 프로필이 전역 카탈로그와 포트폴리오 재정의 어디에도 없다. */
    ASSET_PROFILE_NOT_FOUND,

    /** 외부 시장 데이터 제공자 호출이 실패했다. 재시도할 수 있다. */
    MARKET_DATA_UNAVAILABLE,

    /** 외부 시장 데이터 제공자의 요청 제한에 걸렸다. 잠시 후 재시도할 수 있다. */
    MARKET_DATA_RATE_LIMIT_EXCEEDED
}
