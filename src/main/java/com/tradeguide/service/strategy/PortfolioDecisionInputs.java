package com.tradeguide.service.strategy;

import com.tradeguide.domain.holding.Holding;
import com.tradeguide.domain.strategy.InvestmentTrack;
import com.tradeguide.domain.strategy.PremarketGuideCandidateSource;
import com.tradeguide.domain.strategy.PremarketGuidePortfolioStateDigests;
import com.tradeguide.domain.trade.Market;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 한 시점에 읽은 포트폴리오 결정 입력. 장전 가이드의 보유·후보 배치가 원장과 설정을 각자 다시 읽지 않고
 * 이 값을 공유해, 생성 도중 다른 트랜잭션이 커밋해도 한 가이드 안의 판단이 같은 상태를 보게 한다.
 *
 * @param candidates 포트폴리오 후보 또는 전역 카탈로그 대체 목록. 보유 종목 제외는 호출 쪽에서 한다.
 * @param brokerSnapshotHasItems 보유 종목이 없을 때 안내 문구를 고르기 위한 최신 증권사 보유 스냅샷 존재 여부.
 */
public record PortfolioDecisionInputs(
        Long portfolioId,
        List<Holding> holdings,
        Map<AssetKey, InvestmentTrack> strategyOverrides,
        BigDecimal portfolioStopLossRatio,
        Map<AssetKey, BigDecimal> stopLossOverrides,
        PremarketGuideCandidateSource candidateSource,
        List<CandidateRef> candidates,
        boolean brokerSnapshotHasItems,
        PremarketGuidePortfolioStateDigests digests
) {
    public PortfolioDecisionInputs {
        holdings = List.copyOf(holdings);
        strategyOverrides = Map.copyOf(strategyOverrides);
        stopLossOverrides = Map.copyOf(stopLossOverrides);
        candidates = List.copyOf(candidates);
        if (portfolioId == null || candidateSource == null || digests == null) {
            throw new IllegalArgumentException("포트폴리오 결정 입력이 올바르지 않습니다.");
        }
    }

    public Optional<InvestmentTrack> strategyOverride(Holding holding) {
        return Optional.ofNullable(strategyOverrides.get(AssetKey.of(holding)));
    }

    /** 종목별 재정의가 있으면 그 값을, 없으면 포트폴리오 기본값을 쓴다. 둘 다 없으면 {@code null}. */
    public BigDecimal stopLossRatio(Holding holding) {
        return stopLossOverrides.getOrDefault(AssetKey.of(holding), portfolioStopLossRatio);
    }

    public record AssetKey(Market market, String ticker) {
        static AssetKey of(Holding holding) {
            return new AssetKey(holding.getMarket(), holding.getTicker());
        }
    }

    /** @param investmentTrack 포트폴리오 후보의 트랙. 전역 카탈로그 대체 목록이면 {@code null}. */
    public record CandidateRef(Market market, String ticker, InvestmentTrack investmentTrack) {
    }
}
