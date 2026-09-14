package com.tradeguide.service.market;

import com.tradeguide.exception.MarketDataRateLimitExceededException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Twelve Data 호출의 전역 429 쿨다운과 사용량을 추적한다.
 *
 * <p>Twelve Data API 키는 프로세스 전체에서 공유되는 단일 자원이므로, 한 번 429를 받으면
 * 그 직후 다른 포트폴리오·스레드의 호출도 같은 제한에 걸릴 가능성이 높다. 429를 받은 뒤
 * 일정 시간 동안 이 프로세스의 모든 Twelve Data 가격 조회를 외부 호출 없이 즉시 차단해
 * 제한을 더 악화시키지 않는다. 이 쿨다운은 Twelve Data가 문서화한 제한이 아니라 우리
 * 쪽의 보수적 기본값이며, 응답의 {@code Retry-After} 헤더가 있으면 그 값을 우선한다.
 *
 * <p>사용량 카운터는 심볼당 크레딧이 소비된다는 전제(다종목 배치 호출도 심볼 수만큼
 * 소비)를 관측 가능하게 만들기 위한 것이다. 별도 API로 노출하지는 않으며, 로그와
 * 테스트로 검증한다.
 */
@Component
public class TwelveDataRateLimitTracker {

    private static final Logger log = LoggerFactory.getLogger(TwelveDataRateLimitTracker.class);

    private final Clock clock;
    private final Duration defaultCooldown;

    private final AtomicReference<Instant> cooldownUntil = new AtomicReference<>();
    private final AtomicLong totalRequestCount = new AtomicLong();
    private final AtomicLong totalSymbolCreditsUsed = new AtomicLong();
    private final AtomicInteger rateLimitHitCount = new AtomicInteger();
    private final AtomicReference<Instant> lastRateLimitAt = new AtomicReference<>();

    public TwelveDataRateLimitTracker(
            Clock clock,
            @Value("${twelve-data.rate-limit-cooldown-seconds:30}") long defaultCooldownSeconds
    ) {
        this.clock = clock;
        this.defaultCooldown = Duration.ofSeconds(defaultCooldownSeconds);
    }

    /**
     * 쿨다운 중이면 외부 호출 없이 즉시 차단한다.
     *
     * @throws MarketDataRateLimitExceededException 아직 쿨다운이 끝나지 않았을 때
     */
    public void requireNotCoolingDown() {
        Instant until = cooldownUntil.get();
        Instant now = clock.instant();
        if (until != null && now.isBefore(until)) {
            long retryAfterSeconds = Duration.between(now, until).getSeconds() + 1;
            throw new MarketDataRateLimitExceededException(
                    "Twelve Data 요청 제한으로 쿨다운 중입니다. 약 " + retryAfterSeconds + "초 후 다시 시도해 주세요.",
                    null,
                    retryAfterSeconds
            );
        }
    }

    /**
     * 성공한 호출의 심볼 수를 사용량에 더한다. 배치 호출이면 심볼 수만큼 크레딧이
     * 소비된다는 전제를 그대로 반영한다.
     */
    public void recordSuccess(int symbolCount) {
        totalRequestCount.incrementAndGet();
        totalSymbolCreditsUsed.addAndGet(symbolCount);
        log.debug("Twelve Data 가격 조회 성공: symbolCount={}, totalRequests={}, totalSymbolCredits={}",
                symbolCount, totalRequestCount.get(), totalSymbolCreditsUsed.get());
    }

    /**
     * 429 응답을 받았을 때 호출한다. 이후 {@code retryAfter} 동안(없으면 기본 쿨다운 동안)
     * 이 프로세스의 모든 Twelve Data 가격 조회를 즉시 차단한다.
     */
    public void recordRateLimited(Duration retryAfter) {
        Instant now = clock.instant();
        Duration cooldown = (retryAfter != null && !retryAfter.isNegative() && !retryAfter.isZero())
                ? retryAfter
                : defaultCooldown;

        Instant newCooldownUntil = now.plus(cooldown);
        cooldownUntil.updateAndGet(existing ->
                existing != null && existing.isAfter(newCooldownUntil) ? existing : newCooldownUntil);
        rateLimitHitCount.incrementAndGet();
        lastRateLimitAt.set(now);

        log.warn("Twelve Data 429 수신, 전역 쿨다운 적용: retryAfterSeconds={}, cooldownUntil={}, hitCount={}",
                cooldown.getSeconds(), cooldownUntil.get(), rateLimitHitCount.get());
    }

    public Snapshot snapshot() {
        return new Snapshot(
                totalRequestCount.get(),
                totalSymbolCreditsUsed.get(),
                rateLimitHitCount.get(),
                lastRateLimitAt.get(),
                cooldownUntil.get()
        );
    }

    /** 사용량 관측을 위한 읽기 전용 스냅샷이다. */
    public record Snapshot(
            long totalRequestCount,
            long totalSymbolCreditsUsed,
            int rateLimitHitCount,
            Instant lastRateLimitAt,
            Instant cooldownUntil
    ) {
    }
}
