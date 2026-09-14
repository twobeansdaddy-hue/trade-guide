package com.tradeguide.service.strategy;

import com.tradeguide.domain.strategy.*;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Locale;

@Component
public class StrategyDecisionMaker {

    private static final String NO_STOP_LOSS_MESSAGE =
            "검증된 손절 규칙이 설정되지 않아 손절가를 자동 산출하지 않습니다.";

    public StrategyDecision decideForHolding(StrategySignal signal) {
        return decideForHolding(signal, null, null);
    }

    public StrategyDecision decideForHolding(
            StrategySignal signal,
            BigDecimal averagePurchasePrice,
            BigDecimal stopLossRatio
    ) {
        if (signal.getTrend() == StrategyTrend.ABOVE_LONG_AVERAGE) {
            return new StrategyDecision(
                    StrategyAction.HOLD,
                    "상승 추세가 유지되고 있어 현재 보유 수량을 유지합니다.",
                    signal,
                    createGuidance(
                            "HOLDING",
                            "이미 보유 중인 종목이므로 신규 매수 시점이 아니라 보유 상태를 검토합니다.",
                            averagePurchasePrice,
                            stopLossRatio,
                            "평균 매입가"
                    )
            );
        }

        return new StrategyDecision(
                StrategyAction.SELL,
                "하락 추세가 유지되고 있어 현재 보유 수량의 매도를 검토합니다.",
                signal,
                createGuidance(
                        "HOLDING",
                        "신규 매수 시점이 아니며, 하락 추세에 따른 보유 수량 정리를 검토합니다.",
                        averagePurchasePrice,
                        stopLossRatio,
                        "평균 매입가"
                )
        );
    }

    public StrategyDecision decideForCandidate(StrategySignal signal) {
        return decideForCandidate(signal, null);
    }

    public StrategyDecision decideForCandidate(
            StrategySignal signal,
            BigDecimal stopLossRatio
    ) {
        if (signal.getTrend() == StrategyTrend.ABOVE_LONG_AVERAGE
                && signal.getWeeksSinceCross() != null
                && signal.getWeeksSinceCross() <= 4) {
            return new StrategyDecision(
                    StrategyAction.BUY,
                    "상승 추세가 유지되고 있고 최근 교차 후 4주 이내여서 신규 진입을 검토합니다.",
                    signal,
                    createGuidance(
                            "ELIGIBLE_NOW",
                            "상승 추세이며 최근 교차 후 0~4주 구간이므로 신규 진입을 검토할 수 있습니다.",
                            signal.getReferencePrice(),
                            stopLossRatio,
                            "전략 기준 가격"
                    )
            );
        }

        return new StrategyDecision(
                StrategyAction.WATCH,
                "신규 진입 조건이 충족되지 않아 관찰합니다.",
                signal,
                createGuidance(
                        "WAIT",
                        "현재 Track A 신규 진입 조건을 충족하지 않아 관찰합니다.",
                        signal.getReferencePrice(),
                        stopLossRatio,
                        "전략 기준 가격"
                )
        );
    }

    private StrategyDecisionGuidance createGuidance(
            String entryTimingStatus,
            String entryTimingMessage,
            BigDecimal basePrice,
            BigDecimal stopLossRatio,
            String basePriceLabel
    ) {
        if (stopLossRatio != null
                && basePrice != null
                && basePrice.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal stopLossPrice = basePrice
                    .multiply(BigDecimal.ONE.subtract(stopLossRatio));
            return new StrategyDecisionGuidance(
                    entryTimingStatus,
                    entryTimingMessage,
                    "USER_DEFINED",
                    stopLossRatio,
                    stopLossPrice,
                    String.format(
                            Locale.ROOT,
                            "사용자가 설정한 손절 기준 %.2f%%를 %s에 적용한 검토용 가격입니다. 실제 체결가는 시장 상황에 따라 달라질 수 있습니다.",
                            stopLossRatio.multiply(BigDecimal.valueOf(100)),
                            basePriceLabel
                    )
            );
        }

        return new StrategyDecisionGuidance(
                entryTimingStatus,
                entryTimingMessage,
                "NOT_CONFIGURED",
                null,
                null,
                NO_STOP_LOSS_MESSAGE
        );
    }
}
