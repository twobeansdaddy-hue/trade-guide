package com.tradeguide.service.strategy;

import com.tradeguide.domain.broker.PortfolioBrokerHoldingSnapshot;
import com.tradeguide.domain.holding.Holding;
import com.tradeguide.domain.strategy.AssetStrategyGuide;
import com.tradeguide.domain.strategy.EmptyHoldingsGuidance;
import com.tradeguide.domain.strategy.EmptyHoldingsReason;
import com.tradeguide.domain.strategy.PortfolioAssetStrategyProfile;
import com.tradeguide.domain.strategy.StrategyGuideBatch;
import com.tradeguide.domain.strategy.StrategyGuideUnavailableReason;
import com.tradeguide.domain.strategy.StrategySignal;
import com.tradeguide.domain.strategy.UnavailableAsset;
import com.tradeguide.exception.AssetProfileNotFoundException;
import com.tradeguide.exception.MarketDataRateLimitExceededException;
import com.tradeguide.exception.MarketDataUnavailableException;
import com.tradeguide.repository.broker.PortfolioBrokerHoldingSnapshotRepository;
import com.tradeguide.repository.strategy.PortfolioAssetStrategyProfileRepository;
import com.tradeguide.service.holding.HoldingService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class PortfolioStrategyGuideService {

    private final HoldingService holdingService;
    private final StrategyGuideService strategyGuideService;
    private final StrategyDecisionMaker strategyDecisionMaker;
    private final PortfolioAssetStrategyProfileRepository portfolioAssetStrategyProfileRepository;
    private final PortfolioBrokerHoldingSnapshotRepository portfolioBrokerHoldingSnapshotRepository;

    public PortfolioStrategyGuideService(
            HoldingService holdingService,
            StrategyGuideService strategyGuideService,
            StrategyDecisionMaker strategyDecisionMaker,
            PortfolioAssetStrategyProfileRepository portfolioAssetStrategyProfileRepository,
            PortfolioBrokerHoldingSnapshotRepository portfolioBrokerHoldingSnapshotRepository
    ) {
        this.holdingService = holdingService;
        this.strategyGuideService = strategyGuideService;
        this.strategyDecisionMaker = strategyDecisionMaker;
        this.portfolioAssetStrategyProfileRepository = portfolioAssetStrategyProfileRepository;
        this.portfolioBrokerHoldingSnapshotRepository = portfolioBrokerHoldingSnapshotRepository;
    }

    public StrategyGuideBatch getPortfolioStrategyGuides(
            Long memberId,
            Long portfolioId
    ) {
        List<Holding> holdings = holdingService.getHoldings(
                memberId,
                portfolioId
        );

        if (holdings.isEmpty()) {
            return new StrategyGuideBatch(
                    List.of(),
                    List.of(),
                    resolveEmptyHoldingsGuidance(portfolioId)
            );
        }

        List<AssetStrategyGuide> guides = new ArrayList<>();
        List<UnavailableAsset> unavailableAssets = new ArrayList<>();

        for (int index = 0; index < holdings.size(); index++) {
            Holding holding = holdings.get(index);

            try {
                StrategySignal signal = resolveSignal(portfolioId, holding);

                guides.add(new AssetStrategyGuide(
                        holding.getMarket(),
                        holding.getTicker(),
                        strategyDecisionMaker.decideForHolding(signal)
                ));
            } catch (AssetProfileNotFoundException exception) {
                unavailableAssets.add(new UnavailableAsset(
                        holding.getMarket(),
                        holding.getTicker(),
                        exception.getMessage(),
                        StrategyGuideUnavailableReason.ASSET_PROFILE_NOT_FOUND
                ));
            } catch (MarketDataRateLimitExceededException exception) {
                unavailableAssets.add(new UnavailableAsset(
                        holding.getMarket(),
                        holding.getTicker(),
                        exception.getMessage(),
                        StrategyGuideUnavailableReason.MARKET_DATA_RATE_LIMIT_EXCEEDED
                ));

                addRateLimitedAssets(
                        holdings,
                        index + 1,
                        unavailableAssets
                );

                break;
            } catch (MarketDataUnavailableException exception) {
                unavailableAssets.add(new UnavailableAsset(
                        holding.getMarket(),
                        holding.getTicker(),
                        exception.getMessage(),
                        StrategyGuideUnavailableReason.MARKET_DATA_UNAVAILABLE
                ));
            }
        }

        return new StrategyGuideBatch(guides, unavailableAssets);
    }

    /**
     * 원장 기반 보유 종목이 0건일 때 안내 문구를 고른다. 증권사 스냅샷은 있지만 원장에
     * 반영되지 않은 경우와, 아예 참고할 스냅샷조차 없는 경우를 구분해 다음 행동(개시 잔고
     * 반영/보유 종목 반영 또는 매매 기록 등록)을 다르게 안내한다. 전략 프로필 저장은 이
     * 판단에 관여하지 않는다 - 오직 원장과 증권사 스냅샷 상태만 본다.
     */
    private EmptyHoldingsGuidance resolveEmptyHoldingsGuidance(Long portfolioId) {
        boolean hasUnreflectedSnapshot = portfolioBrokerHoldingSnapshotRepository
                .findFirstByPortfolio_IdOrderBySyncedAtDesc(portfolioId)
                .map(PortfolioBrokerHoldingSnapshot::getItems)
                .map(items -> !items.isEmpty())
                .orElse(false);

        if (hasUnreflectedSnapshot) {
            return new EmptyHoldingsGuidance(
                    EmptyHoldingsReason.BROKER_SNAPSHOT_NOT_REFLECTED,
                    "증권사 보유 종목 스냅샷은 있지만 아직 매매 원장에 반영되지 않아 보유 종목 가이드를 계산할 수 없습니다. "
                            + "개시 잔고 반영 또는 보유 종목 반영을 진행해 주세요."
            );
        }

        return new EmptyHoldingsGuidance(
                EmptyHoldingsReason.NO_BROKER_SNAPSHOT,
                "이 포트폴리오에는 매매 원장 기반 보유 종목이 없습니다. 전략 프로필 저장만으로는 보유 수량이 생기지 않으므로, "
                        + "매매 기록을 등록하거나 증권사 계좌를 연동해 보유 종목을 반영해 주세요."
        );
    }

    /**
     * 이 포트폴리오에 보유 종목 재정의가 있으면 그 트랙으로, 없으면 기존 전역
     * {@link com.tradeguide.domain.strategy.AssetProfile} 조회로 신호를 계산한다. 재정의는
     * 이 포트폴리오에만 적용되며 다른 포트폴리오나 후보 가이드에는 영향을 주지 않는다.
     */
    private StrategySignal resolveSignal(Long portfolioId, Holding holding) {
        Optional<PortfolioAssetStrategyProfile> override = portfolioAssetStrategyProfileRepository
                .findByPortfolio_IdAndMarketAndTicker(
                        portfolioId,
                        holding.getMarket(),
                        holding.getTicker()
                );

        if (override.isPresent()) {
            return strategyGuideService.getStrategySignal(
                    holding.getMarket(),
                    holding.getTicker(),
                    override.get().getInvestmentTrack()
            );
        }

        return strategyGuideService.getStrategySignal(
                holding.getMarket(),
                holding.getTicker()
        );
    }

    private void addRateLimitedAssets(
            List<Holding> holdings,
            int startIndex,
            List<UnavailableAsset> unavailableAssets
    ) {
        for (int index = startIndex; index < holdings.size(); index++) {
            Holding holding = holdings.get(index);

            unavailableAssets.add(new UnavailableAsset(
                    holding.getMarket(),
                    holding.getTicker(),
                    "시장 데이터 요청 제한으로 조회하지 못했습니다.",
                    StrategyGuideUnavailableReason.MARKET_DATA_RATE_LIMIT_EXCEEDED
            ));
        }
    }
}
