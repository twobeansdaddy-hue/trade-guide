package com.tradeguide.service.broker;

import java.time.Duration;

/**
 * {@link BrokerDuplicateCallGuard}가 잠금·쿨다운 키로 구분하는 증권사 조회 동작 종류다.
 *
 * <p>세 동작 모두 원장을 건드리지 않는 조회 경로이고, 쿨다운 값은 토스증권이 문서화한 호출
 * 빈도 제한이 아니라 실수 더블클릭·같은 화면 경쟁 재시도를 막기 위한 우리 쪽 보수적 기본값이다.
 */
public enum BrokerCallType {

    HOLDING_PREVIEW("보유 종목 미리보기"),
    HOLDING_SNAPSHOT("보유 종목 스냅샷 갱신"),
    ORDER_HISTORY_IMPORT("주문 이력 가져오기");

    private static final Duration COOLDOWN = Duration.ofSeconds(5);

    private final String description;

    BrokerCallType(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }

    public Duration cooldown() {
        return COOLDOWN;
    }
}
