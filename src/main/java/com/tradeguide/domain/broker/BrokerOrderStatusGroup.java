package com.tradeguide.domain.broker;

/**
 * 주문 이력 조회의 라이프사이클 그룹이다.
 *
 * <p>{@link #CLOSED}는 종료된 주문을 기간·커서로 순회할 수 있는 백필 경로이고,
 * {@link #OPEN}은 진행 중 주문을 한 번에 전량 돌려받는 표시 전용 경로다.
 * {@code OPEN}은 페이지 크기와 커서가 무시되므로 순회하지 않는다.
 */
public enum BrokerOrderStatusGroup {
    OPEN,
    CLOSED
}
