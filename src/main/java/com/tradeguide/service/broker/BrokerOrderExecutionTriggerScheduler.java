package com.tradeguide.service.broker;

import com.tradeguide.domain.broker.BrokerOrderExecutionGrant;
import com.tradeguide.domain.broker.BrokerOrderExecutionGrantStatus;
import com.tradeguide.domain.broker.BrokerOrderSide;
import com.tradeguide.domain.portfolio.Portfolio;
import com.tradeguide.domain.portfolio.PortfolioBrokerLink;
import com.tradeguide.domain.strategy.AssetStrategyGuide;
import com.tradeguide.domain.strategy.AssetTradePlanPreview;
import com.tradeguide.domain.strategy.StrategyAction;
import com.tradeguide.domain.strategy.StrategyGuideBatch;
import com.tradeguide.domain.strategy.TradePlanPreviewBatch;
import com.tradeguide.domain.strategy.TradePlanPreviewStatus;
import com.tradeguide.domain.trade.Currency;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.domain.valuation.CurrencyValuationTotals;
import com.tradeguide.domain.valuation.HoldingValuation;
import com.tradeguide.domain.valuation.PortfolioValuation;
import com.tradeguide.repository.broker.BrokerOrderExecutionGrantRepository;
import com.tradeguide.repository.broker.PortfolioBrokerLinkRepository;
import com.tradeguide.service.strategy.PortfolioStrategyGuideService;
import com.tradeguide.service.strategy.TradePlanPreviewService;
import com.tradeguide.service.valuation.PortfolioValuationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * 오토매매 opt-in 동의(grant)를 실제 매수·매도 주문 결정으로 연결하는 트리거다.
 *
 * <p><b>매도(SELL)</b>는 보유 종목의 추세 이탈 신호를 그대로 전량 청산 주문으로
 * 옮긴다. <b>매수(BUY, 신규 진입)</b>는 새 포지션 사이징 로직을 만들지 않고,
 * 기존 {@link TradePlanPreviewService}(위험 한도·손절가 기반 검토용 매수 수량
 * 계산, 매매 화면이 이미 쓰는 로직)의 결과를 그대로 읽어 쓴다 - 계산 로직 자체는
 * 건드리지 않는다. 가용 현금 잔고 사전 확인은 하지 않는다(`CASH_BALANCE` 어댑터가
 * 아직 없음) - 자금 부족은 향후 실제 브로커 어댑터의 주문 거부(`FAILED`)로
 * 걸러지는 것을 최종 방어선으로 삼는다
 * (`docs/agent-tasks/claude-broker-order-execution-buy-trigger-20260918.md`).
 *
 * <p>기본값은 비활성이다({@code PremarketGuideScheduler}와 동일한 관례) - 운영자가
 * 명시적으로 켜야 한다. 켜져 있어도 실제 브로커 호출 여부는
 * {@link BrokerOrderExecutionService}가 `tradeguide.broker.order-execution.
 * live-enabled`로 따로 결정한다 - 이 스케줄러를 켜는 것과 실거래를 켜는 것은
 * 서로 다른 스위치다.
 */
@Component
@ConditionalOnProperty(
        name = "tradeguide.broker.order-execution.trigger-scheduler.enabled",
        havingValue = "true"
)
public class BrokerOrderExecutionTriggerScheduler {

    private static final Logger log = LoggerFactory.getLogger(BrokerOrderExecutionTriggerScheduler.class);

    private final BrokerOrderExecutionGrantRepository brokerOrderExecutionGrantRepository;
    private final PortfolioBrokerLinkRepository portfolioBrokerLinkRepository;
    private final PortfolioStrategyGuideService portfolioStrategyGuideService;
    private final TradePlanPreviewService tradePlanPreviewService;
    private final PortfolioValuationService portfolioValuationService;
    private final BrokerOrderExecutionService brokerOrderExecutionService;

    public BrokerOrderExecutionTriggerScheduler(
            BrokerOrderExecutionGrantRepository brokerOrderExecutionGrantRepository,
            PortfolioBrokerLinkRepository portfolioBrokerLinkRepository,
            PortfolioStrategyGuideService portfolioStrategyGuideService,
            TradePlanPreviewService tradePlanPreviewService,
            PortfolioValuationService portfolioValuationService,
            BrokerOrderExecutionService brokerOrderExecutionService
    ) {
        this.brokerOrderExecutionGrantRepository = brokerOrderExecutionGrantRepository;
        this.portfolioBrokerLinkRepository = portfolioBrokerLinkRepository;
        this.portfolioStrategyGuideService = portfolioStrategyGuideService;
        this.tradePlanPreviewService = tradePlanPreviewService;
        this.portfolioValuationService = portfolioValuationService;
        this.brokerOrderExecutionService = brokerOrderExecutionService;
    }

    @Scheduled(
            cron = "${tradeguide.broker.order-execution.trigger-scheduler.cron:0 30 9 * * MON-FRI}",
            zone = "America/New_York"
    )
    public void evaluateActiveGrants() {
        brokerOrderExecutionGrantRepository.findAllByStatus(BrokerOrderExecutionGrantStatus.ACTIVE)
                .forEach(this::evaluateGrantSafely);
    }

    private void evaluateGrantSafely(BrokerOrderExecutionGrant grant) {
        try {
            evaluateGrant(grant);
        } catch (RuntimeException exception) {
            // 한 grant의 실패가 다른 grant 처리를 막지 않게 한다(PremarketGuideScheduler와 동일 원칙).
            log.warn("오토매매 매도 트리거 평가에 실패했습니다. grantId={}", grant.getId(), exception);
        }
    }

    private void evaluateGrant(BrokerOrderExecutionGrant grant) {
        Long memberId = grant.getMember().getId();
        List<PortfolioBrokerLink> links =
                portfolioBrokerLinkRepository.findAllByBrokerConnection_Id(grant.getBrokerConnection().getId());

        for (PortfolioBrokerLink link : links) {
            try {
                evaluatePortfolio(grant, memberId, link.getPortfolio());
            } catch (RuntimeException exception) {
                log.warn(
                        "오토매매 매도 트리거 평가에 실패했습니다. grantId={}, portfolioId={}",
                        grant.getId(), link.getPortfolio().getId(), exception
                );
            }
        }
    }

    private void evaluatePortfolio(BrokerOrderExecutionGrant grant, Long memberId, Portfolio portfolio) {
        Long portfolioId = portfolio.getId();
        PortfolioValuation valuation = portfolioValuationService.getPortfolioValuation(memberId, portfolioId);
        CurrencyValuationTotals usdTotals = valuation.getTotalsFor(Currency.USD);
        BigDecimal accountAssetValue = usdTotals == null ? null : usdTotals.getTotalMarketValue();
        if (accountAssetValue == null || accountAssetValue.signum() <= 0) {
            return;
        }

        StrategyGuideBatch batch = portfolioStrategyGuideService.getPortfolioStrategyGuides(memberId, portfolioId);
        for (AssetStrategyGuide guide : batch.getGuides()) {
            if (guide.getMarket() != Market.US) {
                continue; // USD만 다룬다 - 위험·매매 계산 범위를 기존 프로젝트 경계와 맞춘다.
            }
            if (guide.getStrategyDecision().getAction() != StrategyAction.SELL) {
                continue;
            }
            if (!matchesGrantStrategy(grant, guide)) {
                continue;
            }

            findHoldingValuation(valuation, guide.getTicker()).ifPresent(holdingValuation ->
                    brokerOrderExecutionService.executeOrder(
                            grant,
                            grant.getStrategyId(),
                            guide.getTicker(),
                            BrokerOrderSide.SELL,
                            holdingValuation.getQuantity(),
                            holdingValuation.getMarketValue(),
                            accountAssetValue
                    ));
        }

        evaluateBuyCandidates(grant, memberId, portfolioId, accountAssetValue);
    }

    /**
     * 신규 진입 후보를 평가한다. 수량·금액은 {@link TradePlanPreviewService}가 위험
     * 한도·손절가로 이미 계산한 값을 그대로 쓴다 - 여기서 다시 계산하지 않는다.
     * 포지션 한도(grant의 20% 상한) 재확인도 하지 않는다 -
     * {@link BrokerOrderExecutionService#executeOrder}가 명목금액/계좌자산 비율로
     * 이미 검증하므로 중복 계산이다.
     */
    private void evaluateBuyCandidates(
            BrokerOrderExecutionGrant grant,
            Long memberId,
            Long portfolioId,
            BigDecimal accountAssetValue
    ) {
        TradePlanPreviewBatch planBatch = tradePlanPreviewService.getTradePlanPreview(memberId, portfolioId);
        for (AssetTradePlanPreview plan : planBatch.getCandidatePlans()) {
            if (plan.getStatus() != TradePlanPreviewStatus.BUY) {
                continue;
            }
            if (plan.getMarket() != Market.US) {
                continue; // USD만 다룬다 - SELL 평가와 동일한 경계.
            }
            if (!matchesGrantStrategy(grant, plan)) {
                continue;
            }
            if (plan.getQuantity() == null || plan.getQuantity().signum() <= 0
                    || plan.getAmount() == null || plan.getAmount().signum() <= 0) {
                continue;
            }

            brokerOrderExecutionService.executeOrder(
                    grant,
                    grant.getStrategyId(),
                    plan.getTicker(),
                    BrokerOrderSide.BUY,
                    plan.getQuantity(),
                    plan.getAmount(),
                    accountAssetValue
            );
        }
    }

    /**
     * 이 종목의 SELL 신호를 낸 전략이 grant가 동의한 바로 그 전략인지 확인한다.
     * 포트폴리오 안의 다른 종목이 다른 전략(또는 오버라이드)으로 SELL을 냈다고
     * 해서 이 grant로 팔려나가면 안 된다.
     */
    private boolean matchesGrantStrategy(BrokerOrderExecutionGrant grant, AssetStrategyGuide guide) {
        var metadata = guide.getStrategyDecision().getSignal() == null
                ? null
                : guide.getStrategyDecision().getSignal().getMetadata();
        return metadata != null && grant.getStrategyId().equals(metadata.getStrategyId());
    }

    /** 매수 후보 판에 대한 같은 확인 - 다른 전략(또는 오버라이드)이 낸 BUY로 사지 않는다. */
    private boolean matchesGrantStrategy(BrokerOrderExecutionGrant grant, AssetTradePlanPreview plan) {
        var metadata = plan.getStrategyMetadata();
        return metadata != null && grant.getStrategyId().equals(metadata.getStrategyId());
    }

    private Optional<HoldingValuation> findHoldingValuation(PortfolioValuation valuation, String ticker) {
        return valuation.getHoldingValuations().stream()
                .filter(holdingValuation -> ticker.equals(holdingValuation.getTicker()))
                .findFirst();
    }
}
