package com.tradeguide.service.broker;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 보유 종목 미리보기({@code POST .../broker-sync-preview})의 마지막 호출 시각을 기억한다.
 *
 * <p>미리보기는 아무것도 저장하지 않는 순수 조회이므로, 쿨다운 기준 시각을 구할 수 있는
 * DB 컬럼이 없다. 그래서 이 값은 애플리케이션 메모리에만 있고, 재시작하면 초기화된다.
 * 완전한 보장이 아니라 실수 더블클릭 방지 목적임을 명시한다.
 */
@Component
public class BrokerHoldingPreviewCallTracker {

    private final ConcurrentHashMap<Long, LocalDateTime> lastCalledAt = new ConcurrentHashMap<>();

    public LocalDateTime get(Long portfolioId) {
        return lastCalledAt.get(portfolioId);
    }

    public void record(Long portfolioId, LocalDateTime calledAt) {
        lastCalledAt.put(portfolioId, calledAt);
    }
}
