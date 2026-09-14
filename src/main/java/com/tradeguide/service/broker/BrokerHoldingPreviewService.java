package com.tradeguide.service.broker;

import com.tradeguide.domain.broker.BrokerHoldingPreview;
import com.tradeguide.domain.broker.BrokerHoldingSnapshot;
import com.tradeguide.domain.holding.Holding;
import com.tradeguide.service.holding.HoldingService;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 포트폴리오에 연결된 증권사 계좌의 보유 종목을 읽기 전용으로 조회하고
 * Trade Guide 매매 기록과의 차이를 정리한다.
 *
 * <p>이 서비스는 어떤 경우에도 {@code TradeTransaction}을 생성·수정하지 않는다.
 * 증권사 스냅샷을 매매 기록으로 가져오는 동작은 별도의 명시적 가져오기 설계 이후에 추가한다.
 */
@Service
public class BrokerHoldingPreviewService {

    private final BrokerHoldingContextLoader brokerHoldingContextLoader;
    private final HoldingService holdingService;
    private final BrokerHoldingPreviewCalculator brokerHoldingPreviewCalculator;
    private final BrokerDuplicateCallGuard brokerDuplicateCallGuard;
    private final BrokerHoldingPreviewCallTracker brokerHoldingPreviewCallTracker;
    private final Clock clock;

    public BrokerHoldingPreviewService(
            BrokerHoldingContextLoader brokerHoldingContextLoader,
            HoldingService holdingService,
            BrokerHoldingPreviewCalculator brokerHoldingPreviewCalculator,
            BrokerDuplicateCallGuard brokerDuplicateCallGuard,
            BrokerHoldingPreviewCallTracker brokerHoldingPreviewCallTracker,
            Clock clock
    ) {
        this.brokerHoldingContextLoader = brokerHoldingContextLoader;
        this.holdingService = holdingService;
        this.brokerHoldingPreviewCalculator = brokerHoldingPreviewCalculator;
        this.brokerDuplicateCallGuard = brokerDuplicateCallGuard;
        this.brokerHoldingPreviewCallTracker = brokerHoldingPreviewCallTracker;
        this.clock = clock;
    }

    /**
     * 증권사 보유 종목을 조회해 원장과 비교한 결과만 돌려준다. 아무것도 저장하지 않는다.
     *
     * <p><b>이 메서드에는 트랜잭션이 없다.</b> 증권사 호출은 트랜잭션 밖에서 한다. 읽기
     * 트랜잭션이라도 외부 HTTP 응답을 기다리는 동안 커넥션을 붙잡으면, 증권사가 느려질 때
     * 커넥션 풀이 먼저 고갈된다. 연결 정보 조회는 {@link BrokerHoldingContextLoader}가,
     * 원장 보유 수량 계산은 {@link HoldingService}가 각자의 짧은 트랜잭션에서 처리한다.
     *
     * <p>두 읽기가 서로 다른 트랜잭션에서 일어나므로 그 사이의 원장 변경이 비교에 반영될 수
     * 있다. 미리보기는 저장하지 않는 화면용 비교이고, 원장에 반영하는 경로는 저장된 스냅샷을
     * 기준으로 다시 판정하므로 이 차이가 원장 정합성을 해치지 않는다.
     */
    public BrokerHoldingPreview getHoldingPreview(Long memberId, Long portfolioId) {
        brokerDuplicateCallGuard.acquire(portfolioId, BrokerCallType.HOLDING_PREVIEW);
        try {
            brokerDuplicateCallGuard.checkCooldown(
                    BrokerCallType.HOLDING_PREVIEW, brokerHoldingPreviewCallTracker.get(portfolioId), clock);

            BrokerHoldingContextLoader.HoldingContext context =
                    brokerHoldingContextLoader.load(memberId, portfolioId);

            BrokerHoldingSnapshot snapshot = context.fetchHoldings();
            List<Holding> tradeGuideHoldings = holdingService.getHoldings(memberId, portfolioId);

            // 쿨다운 기준 시각은 저장할 곳이 없으므로 호출이 성공한 뒤에만 메모리에 기록한다.
            // 실패한 시도까지 기록하면 설정을 고친 사용자가 남은 쿨다운 때문에 바로 재시도하지 못한다.
            brokerHoldingPreviewCallTracker.record(portfolioId, LocalDateTime.now(clock));

            return new BrokerHoldingPreview(
                    context.provider(),
                    context.brokerConnectionId(),
                    context.maskedAccountNumber(),
                    LocalDateTime.now(clock),
                    brokerHoldingPreviewCalculator.compare(snapshot.holdings(), tradeGuideHoldings),
                    snapshot.unsupportedMarketCount()
            );
        } finally {
            brokerDuplicateCallGuard.release(portfolioId, BrokerCallType.HOLDING_PREVIEW);
        }
    }
}
