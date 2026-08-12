package com.tradeguide.service.strategy;

import com.tradeguide.domain.strategy.*;
import org.springframework.stereotype.Component;

@Component
public class StrategyDecisionMaker {

    public StrategyDecision decideForHolding(StrategySignal signal) {
        if (signal.getTrend() == StrategyTrend.ABOVE_LONG_AVERAGE) {
            return new StrategyDecision(
                    StrategyAction.HOLD,
                    "상승 추세가 유지되고 있어 현재 보유 수량을 유지합니다.",
                    signal
            );
        }

        return new StrategyDecision(
                StrategyAction.SELL,
                "하락 추세가 유지되고 있어 현재 보유 수량의 매도를 검토합니다.",
                signal
        );
    }

    public StrategyDecision decideForCandidate(StrategySignal signal) {
        if (signal.getTrend() == StrategyTrend.ABOVE_LONG_AVERAGE
                && signal.getSignalEvent() == StrategySignalEvent.CROSS_UP
                && Integer.valueOf(0).equals(signal.getWeeksSinceCross())) {
            return new StrategyDecision(
                    StrategyAction.BUY,
                    "이번 완료 주봉에서 상승 교차가 발생해 신규 진입을 검토합니다.",
                    signal
            );
        }

        return new StrategyDecision(
                StrategyAction.WATCH,
                "신규 진입 조건이 충족되지 않아 관찰합니다.",
                signal
        );
    }
}
