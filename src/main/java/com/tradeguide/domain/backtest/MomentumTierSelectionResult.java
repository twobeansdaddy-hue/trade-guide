package com.tradeguide.domain.backtest;

import java.util.List;
import java.util.Set;

/**
 * 한 리밸런싱 시점에 상위/유지 이원 기준(top/hold tier)을 적용해 계산한
 * 새 보유 종목 구성과, 기존 보유 대비 편입/유지/이탈 종목 목록.
 */
public class MomentumTierSelectionResult {

    private final Set<String> newHoldings;
    private final List<String> enteredTickers;
    private final List<String> retainedTickers;
    private final List<String> exitedTickers;

    public MomentumTierSelectionResult(
            Set<String> newHoldings,
            List<String> enteredTickers,
            List<String> retainedTickers,
            List<String> exitedTickers
    ) {
        this.newHoldings = newHoldings;
        this.enteredTickers = enteredTickers;
        this.retainedTickers = retainedTickers;
        this.exitedTickers = exitedTickers;
    }

    public Set<String> getNewHoldings() {
        return newHoldings;
    }

    public List<String> getEnteredTickers() {
        return enteredTickers;
    }

    public List<String> getRetainedTickers() {
        return retainedTickers;
    }

    public List<String> getExitedTickers() {
        return exitedTickers;
    }
}
