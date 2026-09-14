package com.tradeguide.service.broker;

import com.tradeguide.exception.BrokerCallCooldownException;
import com.tradeguide.exception.BrokerCallInProgressException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 같은 포트폴리오의 같은 증권사 조회 동작(미리보기·스냅샷 갱신·주문 이력 가져오기)이 겹치거나
 * 짧은 간격으로 반복되는 것을 막는다.
 *
 * <p>{@code inFlight}는 애플리케이션 메모리 상태다. 재시작하면 초기화되고, 다중 인스턴스
 * 배포에서는 인스턴스별로 따로 논다. 이 서비스가 새로 만드는 위험이 아니라 이미 받아들여진
 * 단일 인스턴스 전제의 한계다.
 *
 * <p>이 잠금은 증권사 호출이 실제로 끝나야(성공이든 실패든 예외든) {@code finally}에서 풀린다.
 * 호출에 쓰는 {@code RestClient}에 읽기 타임아웃이 없으면, 네트워크가 멈춘 채 응답이 오지
 * 않는 상황에서 잠금이 사실상 무기한 풀리지 않는다. 그래서 이 가드는 반드시 명시적
 * connect/read timeout이 설정된 브로커 HTTP 클라이언트({@link com.tradeguide.config.BrokerHttpClientConfig})와
 * 함께 배포한다.
 */
@Component
public class BrokerDuplicateCallGuard {

    private final ConcurrentHashMap<Key, Boolean> inFlight = new ConcurrentHashMap<>();

    /** 이미 같은 키가 진행 중이면 409로 거부한다. 통과하면 반드시 {@link #release}를 호출해야 한다. */
    public void acquire(Long portfolioId, BrokerCallType type) {
        if (inFlight.putIfAbsent(new Key(portfolioId, type), Boolean.TRUE) != null) {
            throw new BrokerCallInProgressException(
                    "같은 포트폴리오의 " + type.description() + " 조회가 이미 진행 중입니다. 잠시 후 다시 시도하세요.");
        }
    }

    public void release(Long portfolioId, BrokerCallType type) {
        inFlight.remove(new Key(portfolioId, type));
    }

    /**
     * 직전 호출 시각이 쿨다운 이내면 429로 거부한다. {@code lastCalledAt}이 {@code null}이면
     * 첫 호출이므로 무조건 통과한다.
     */
    public void checkCooldown(BrokerCallType type, LocalDateTime lastCalledAt, Clock clock) {
        if (lastCalledAt == null) {
            return;
        }

        Duration cooldown = type.cooldown();
        Duration elapsed = Duration.between(lastCalledAt, LocalDateTime.now(clock));
        if (elapsed.compareTo(cooldown) < 0) {
            long remainingSeconds = cooldown.minus(elapsed).toSeconds() + 1;
            throw new BrokerCallCooldownException(
                    remainingSeconds + "초 후 다시 시도할 수 있습니다.", remainingSeconds);
        }
    }

    private record Key(Long portfolioId, BrokerCallType type) {
    }
}
