package com.tradeguide.service.strategy;

import com.tradeguide.domain.holding.Holding;
import com.tradeguide.domain.strategy.AssetProfile;
import com.tradeguide.domain.strategy.AssetStrategyGuide;
import com.tradeguide.domain.strategy.InvestmentTrack;
import com.tradeguide.domain.strategy.StrategyGuideBatch;
import com.tradeguide.domain.strategy.StrategySignal;
import com.tradeguide.domain.strategy.UnavailableAsset;
import com.tradeguide.exception.MarketDataRateLimitExceededException;
import com.tradeguide.exception.MarketDataUnavailableException;
import com.tradeguide.repository.strategy.AssetProfileRepository;
import com.tradeguide.service.holding.HoldingService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class PortfolioCandidateStrategyGuideService {

    private final HoldingService holdingService;
    private final AssetProfileRepository assetProfileRepository;
    private final StrategyGuideService strategyGuideService;
    private final StrategyDecisionMaker strategyDecisionMaker;

    public PortfolioCandidateStrategyGuideService(
            HoldingService holdingService,
            AssetProfileRepository assetProfileRepository,
            StrategyGuideService strategyGuideService,
            StrategyDecisionMaker strategyDecisionMaker
    ) {
        this.holdingService = holdingService;
        this.assetProfileRepository = assetProfileRepository;
        this.strategyGuideService = strategyGuideService;
        this.strategyDecisionMaker = strategyDecisionMaker;
    }

    public StrategyGuideBatch getCandidateStrategyGuides(
            Long memberId,
            Long portfolioId
    ) {
        List<Holding> holdings = holdingService.getHoldings(memberId, portfolioId);
        List<AssetStrategyGuide> guides = new ArrayList<>();
        List<UnavailableAsset> unavailableAssets = new ArrayList<>();

        List<AssetProfile> candidateProfiles = assetProfileRepository
                .findAllByInvestmentTrack(InvestmentTrack.TRACK_A)
                .stream()
                .filter(assetProfile -> holdings.stream()
                        .noneMatch(holding ->
                                holding.getMarket() == assetProfile.getMarket()
                                        && holding.getTicker()
                                        .equals(assetProfile.getTicker())
                        )
                )
                .toList();

        for (int index = 0; index < candidateProfiles.size(); index++) {
            AssetProfile assetProfile = candidateProfiles.get(index);

            try {
                StrategySignal signal = strategyGuideService.getStrategySignal(
                        assetProfile.getMarket(),
                        assetProfile.getTicker()
                );

                guides.add(new AssetStrategyGuide(
                        assetProfile.getMarket(),
                        assetProfile.getTicker(),
                        strategyDecisionMaker.decideForCandidate(signal)
                ));
            } catch (MarketDataRateLimitExceededException exception) {
                unavailableAssets.add(new UnavailableAsset(
                        assetProfile.getMarket(),
                        assetProfile.getTicker(),
                        exception.getMessage()
                ));

                addRateLimitedAssets(
                        candidateProfiles,
                        index + 1,
                        unavailableAssets
                );

                break;
            } catch (MarketDataUnavailableException exception) {
                unavailableAssets.add(new UnavailableAsset(
                        assetProfile.getMarket(),
                        assetProfile.getTicker(),
                        exception.getMessage()
                ));
            }
        }

        return new StrategyGuideBatch(guides, unavailableAssets);
    }

    private void addRateLimitedAssets(
            List<AssetProfile> candidateProfiles,
            int startIndex,
            List<UnavailableAsset> unavailableAssets
    ) {
        for (int index = startIndex; index < candidateProfiles.size(); index++) {
            AssetProfile assetProfile = candidateProfiles.get(index);

            unavailableAssets.add(new UnavailableAsset(
                    assetProfile.getMarket(),
                    assetProfile.getTicker(),
                    "시장 데이터 요청 제한으로 조회하지 못했습니다."
            ));
        }
    }
}
