package com.tradeguide.service.broker;

import com.tradeguide.domain.broker.BrokerCredentials;
import com.tradeguide.domain.broker.BrokerOrderExecutionGrant;
import com.tradeguide.domain.broker.BrokerOrderExecutionGrantStatus;
import com.tradeguide.domain.broker.BrokerOrderExecutionRun;
import com.tradeguide.domain.broker.BrokerOrderSide;
import com.tradeguide.repository.broker.BrokerOrderExecutionRunRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * 서킷브레이커를 통과한 신호에 대해 주문을 결정하고 기록하는 오케스트레이션이다.
 *
 * <p><b>이 서비스는 {@code tradeguide.broker.order-execution.live-enabled}가 꺼져</b>
 * <b>있는 한(기본값) 실제 브로커를 절대 호출하지 않는다.</b> 켜져 있어도, 네 가지
 * 안전장치(동의 상태 → 전략 일치 → 포지션 한도 → 일일 주문 한도) 중 하나라도
 * 실패하면 여전히 호출하지 않는다 - 안전장치는 dry-run 여부와 무관하게 항상 먼저
 * 적용된다.
 *
 * <p>이 슬라이스는 실제 Toss 어댑터를 만들지 않는다
 * (`docs/agent-tasks/claude-broker-order-execution-dry-run-implementation-20260917.md`).
 * {@link BrokerProviderRegistry#requireOrderSubmissionProvider}가 예외를 던지는
 * 동안에는 {@code liveEnabled=true}로 설정해도 이 서비스가 안전하게 실패한다
 * (provider가 없으므로) - 그래서 기본값이 false인 것과 별개로, 이 슬라이스
 * 시점에는 어차피 실거래가 발생할 수 없다는 이중 안전장치가 있다.
 */
@Service
public class BrokerOrderExecutionService {

    static final String REASON_GRANT_NOT_ACTIVE = "GRANT_NOT_ACTIVE";
    static final String REASON_STRATEGY_MISMATCH = "STRATEGY_MISMATCH";
    static final String REASON_POSITION_SIZE_EXCEEDED = "POSITION_SIZE_EXCEEDED";
    static final String REASON_DAILY_ORDER_COUNT_EXCEEDED = "DAILY_ORDER_COUNT_EXCEEDED";

    private final BrokerOrderExecutionRunRepository brokerOrderExecutionRunRepository;
    private final BrokerProviderRegistry brokerProviderRegistry;
    private final BrokerCredentialLoader brokerCredentialLoader;
    private final boolean liveEnabled;

    public BrokerOrderExecutionService(
            BrokerOrderExecutionRunRepository brokerOrderExecutionRunRepository,
            BrokerProviderRegistry brokerProviderRegistry,
            BrokerCredentialLoader brokerCredentialLoader,
            @Value("${tradeguide.broker.order-execution.live-enabled:false}") boolean liveEnabled
    ) {
        this.brokerOrderExecutionRunRepository = brokerOrderExecutionRunRepository;
        this.brokerProviderRegistry = brokerProviderRegistry;
        this.brokerCredentialLoader = brokerCredentialLoader;
        this.liveEnabled = liveEnabled;
    }

    /**
     * 안전장치를 검증하고, 통과하면 (live 모드에서만) 실제로 제출하거나 (기본값에서는)
     * dry-run으로 기록한다. 안전장치를 통과하지 못하면 {@code REJECTED_BY_SAFEGUARD}로
     * 기록하고 제출을 시도하지 않는다.
     *
     * @param requestedNotionalValue 이 주문의 예상 명목 금액(수량 × 예상 체결가) - 포지션
     *                                한도 검증에만 쓰고, 실제 제출 요청에는 수량만 나간다.
     * @param accountAssetValue      포지션 한도 계산의 분모. 호출자가 이미 계산해 전달한다 -
     *                                이 서비스는 계좌 평가금액을 직접 조회하지 않는다(Handoff
     *                                참고).
     */
    @Transactional
    public BrokerOrderExecutionRun executeOrder(
            BrokerOrderExecutionGrant grant,
            String strategyId,
            String ticker,
            BrokerOrderSide side,
            BigDecimal requestedQuantity,
            BigDecimal requestedNotionalValue,
            BigDecimal accountAssetValue
    ) {
        if (requestedNotionalValue == null || requestedNotionalValue.signum() <= 0) {
            throw new IllegalArgumentException("주문 명목 금액은 0보다 커야 합니다.");
        }
        if (accountAssetValue == null || accountAssetValue.signum() <= 0) {
            throw new IllegalArgumentException("계좌 자산 총액은 0보다 커야 합니다.");
        }

        LocalDateTime now = LocalDateTime.now();
        BrokerOrderExecutionRun run = BrokerOrderExecutionRun.start(grant, ticker, side, requestedQuantity, now);

        String rejectionReason = evaluateSafeguards(grant, strategyId, requestedNotionalValue, accountAssetValue);
        if (rejectionReason != null) {
            run.markRejectedBySafeguard(LocalDateTime.now(), rejectionReason);
            return brokerOrderExecutionRunRepository.save(run);
        }

        if (!liveEnabled) {
            run.markSubmitted(LocalDateTime.now(), true);
            return brokerOrderExecutionRunRepository.save(run);
        }

        BrokerOrderSubmissionResult result = submitLive(grant, ticker, side, requestedQuantity);
        if (result.success()) {
            run.markSubmitted(LocalDateTime.now(), false);
        } else {
            run.markFailed(LocalDateTime.now(), result.failureReasonCode());
        }
        return brokerOrderExecutionRunRepository.save(run);
    }

    /** 안전장치 네 가지를 고정된 순서로 검증한다. 통과하면 {@code null}, 아니면 실패 사유 코드를 돌려준다. */
    private String evaluateSafeguards(
            BrokerOrderExecutionGrant grant,
            String strategyId,
            BigDecimal requestedNotionalValue,
            BigDecimal accountAssetValue
    ) {
        if (grant.getStatus() != BrokerOrderExecutionGrantStatus.ACTIVE) {
            return REASON_GRANT_NOT_ACTIVE;
        }
        if (!grant.getStrategyId().equals(strategyId)) {
            return REASON_STRATEGY_MISMATCH;
        }

        BigDecimal positionSizePercent = requestedNotionalValue.divide(accountAssetValue, 6, RoundingMode.HALF_UP);
        if (positionSizePercent.compareTo(grant.getMaxPositionSizePerOrderPercent()) > 0) {
            return REASON_POSITION_SIZE_EXCEEDED;
        }

        LocalDateTime todayStart = LocalDateTime.now().toLocalDate().atStartOfDay();
        long todayOrderCount = brokerOrderExecutionRunRepository
                .countByGrant_IdAndStartedAtGreaterThanEqual(grant.getId(), todayStart);
        if (todayOrderCount >= grant.getMaxDailyOrderCount()) {
            return REASON_DAILY_ORDER_COUNT_EXCEEDED;
        }

        return null;
    }

    private BrokerOrderSubmissionResult submitLive(
            BrokerOrderExecutionGrant grant,
            String ticker,
            BrokerOrderSide side,
            BigDecimal requestedQuantity
    ) {
        BrokerOrderSubmissionProvider provider =
                brokerProviderRegistry.requireOrderSubmissionProvider(grant.getBrokerConnection().getProvider());
        BrokerCredentials credentials = brokerCredentialLoader.load(grant.getBrokerConnection());
        String accountSequence = grant.getBrokerConnection().getActiveAccounts().stream()
                .findFirst()
                .map(brokerCredentialLoader::loadAccountSequence)
                .orElseThrow(() -> new IllegalStateException("주문을 제출할 활성 계좌가 없습니다."));

        BrokerOrderSubmissionRequest request =
                new BrokerOrderSubmissionRequest(accountSequence, ticker, side, requestedQuantity);
        return provider.submit(credentials, request);
    }
}
