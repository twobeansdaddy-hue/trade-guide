package com.tradeguide.dto.strategy;

import com.tradeguide.domain.strategy.InvestmentTrack;
import com.tradeguide.domain.trade.Market;

import java.time.LocalDateTime;

/**
 * 보유 종목 한 건에 대한 전략 프로필 상태다. {@code overrideTrack}은 이 포트폴리오에만
 * 적용되는 재정의이고, {@code globalTrack}은 전역 {@link com.tradeguide.domain.strategy.AssetProfile}의
 * 값이다(등록되어 있지 않으면 {@code null}). {@code effectiveTrack}은 보유 종목 가이드 생성이
 * 실제로 사용하는 값으로, 재정의가 있으면 재정의, 없으면 전역 값이며 둘 다 없으면 {@code null}이다.
 */
public class PortfolioAssetStrategyProfileResponse {

    private final Market market;
    private final String ticker;
    private final InvestmentTrack overrideTrack;
    private final InvestmentTrack globalTrack;
    private final InvestmentTrack effectiveTrack;
    private final LocalDateTime updatedAt;

    public PortfolioAssetStrategyProfileResponse(
            Market market,
            String ticker,
            InvestmentTrack overrideTrack,
            InvestmentTrack globalTrack,
            InvestmentTrack effectiveTrack,
            LocalDateTime updatedAt
    ) {
        this.market = market;
        this.ticker = ticker;
        this.overrideTrack = overrideTrack;
        this.globalTrack = globalTrack;
        this.effectiveTrack = effectiveTrack;
        this.updatedAt = updatedAt;
    }

    public static PortfolioAssetStrategyProfileResponse of(
            Market market,
            String ticker,
            InvestmentTrack overrideTrack,
            InvestmentTrack globalTrack,
            LocalDateTime updatedAt
    ) {
        InvestmentTrack effectiveTrack = overrideTrack != null ? overrideTrack : globalTrack;

        return new PortfolioAssetStrategyProfileResponse(
                market,
                ticker,
                overrideTrack,
                globalTrack,
                effectiveTrack,
                updatedAt
        );
    }

    public Market getMarket() {
        return market;
    }

    public String getTicker() {
        return ticker;
    }

    public InvestmentTrack getOverrideTrack() {
        return overrideTrack;
    }

    public InvestmentTrack getGlobalTrack() {
        return globalTrack;
    }

    public InvestmentTrack getEffectiveTrack() {
        return effectiveTrack;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
