package com.tradeguide.service.strategy;

import com.tradeguide.domain.holding.Holding;
import com.tradeguide.domain.strategy.AssetProfile;
import com.tradeguide.domain.strategy.AssetStrategyGuide;
import com.tradeguide.domain.strategy.InvestmentTrack;
import com.tradeguide.domain.strategy.PortfolioCandidateAsset;
import com.tradeguide.domain.strategy.StrategyGuideBatch;
import com.tradeguide.domain.strategy.StrategyGuideUnavailableReason;
import com.tradeguide.domain.strategy.StrategySignal;
import com.tradeguide.domain.strategy.UnavailableAsset;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.exception.MarketDataRateLimitExceededException;
import com.tradeguide.exception.MarketDataUnavailableException;
import com.tradeguide.repository.strategy.AssetProfileRepository;
import com.tradeguide.repository.strategy.PortfolioCandidateAssetRepository;
import com.tradeguide.repository.portfolio.PortfolioRepository;
import com.tradeguide.service.holding.HoldingService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class PortfolioCandidateStrategyGuideService {

    private final HoldingService holdingService;
    private final AssetProfileRepository assetProfileRepository;
    private final PortfolioCandidateAssetRepository portfolioCandidateAssetRepository;
    private final StrategyGuideService strategyGuideService;
    private final StrategyDecisionMaker strategyDecisionMaker;
    private final PortfolioRepository portfolioRepository;

    public PortfolioCandidateStrategyGuideService(
            HoldingService holdingService,
            AssetProfileRepository assetProfileRepository,
            PortfolioCandidateAssetRepository portfolioCandidateAssetRepository,
            StrategyGuideService strategyGuideService,
            StrategyDecisionMaker strategyDecisionMaker,
            PortfolioRepository portfolioRepository
    ) {
        this.holdingService = holdingService;
        this.assetProfileRepository = assetProfileRepository;
        this.portfolioCandidateAssetRepository = portfolioCandidateAssetRepository;
        this.strategyGuideService = strategyGuideService;
        this.strategyDecisionMaker = strategyDecisionMaker;
        this.portfolioRepository = portfolioRepository;
    }

    public StrategyGuideBatch getCandidateStrategyGuides(
            Long memberId,
            Long portfolioId
    ) {
        List<Holding> holdings = holdingService.getHoldings(memberId, portfolioId);
        BigDecimal stopLossRatio = resolveStopLossRatio(memberId, portfolioId);

        return buildGuides(portfolioId, holdings, resolveCandidateRefs(portfolioId), stopLossRatio);
    }

    /**
     * 장전 가이드용. 보유 종목·후보군·손절 설정을 다시 조회하지 않고 한 시점에 읽은 {@code inputs}만 사용해,
     * 같은 가이드의 보유 배치와 같은 포트폴리오 상태로 보유 종목을 제외한다.
     */
    public StrategyGuideBatch getCandidateStrategyGuides(PortfolioDecisionInputs inputs) {
        List<CandidateAssetRef> candidateRefs = inputs.candidates().stream()
                .map(candidate -> new CandidateAssetRef(
                        candidate.market(), candidate.ticker(), candidate.investmentTrack()))
                .toList();

        return buildGuides(inputs.portfolioId(), inputs.holdings(), candidateRefs, inputs.portfolioStopLossRatio());
    }

    private StrategyGuideBatch buildGuides(
            Long portfolioId,
            List<Holding> holdings,
            List<CandidateAssetRef> allCandidateRefs,
            BigDecimal stopLossRatio
    ) {
        List<AssetStrategyGuide> guides = new ArrayList<>();
        List<UnavailableAsset> unavailableAssets = new ArrayList<>();

        List<CandidateAssetRef> candidateRefs = allCandidateRefs
                .stream()
                .filter(candidateRef -> holdings.stream()
                        .noneMatch(holding ->
                                holding.getMarket() == candidateRef.market()
                                        && holding.getTicker()
                                        .equals(candidateRef.ticker())
                        )
                )
                .toList();

        for (int index = 0; index < candidateRefs.size(); index++) {
            CandidateAssetRef candidateRef = candidateRefs.get(index);

            try {
                StrategySignal signal = resolveSignal(portfolioId, candidateRef);

                guides.add(new AssetStrategyGuide(
                        candidateRef.market(),
                        candidateRef.ticker(),
                        stopLossRatio == null
                                ? strategyDecisionMaker.decideForCandidate(signal)
                                : strategyDecisionMaker.decideForCandidate(signal, stopLossRatio)
                ));
            } catch (MarketDataRateLimitExceededException exception) {
                unavailableAssets.add(new UnavailableAsset(
                        candidateRef.market(),
                        candidateRef.ticker(),
                        exception.getMessage(),
                        StrategyGuideUnavailableReason.MARKET_DATA_RATE_LIMIT_EXCEEDED
                ));

                addRateLimitedAssets(
                        candidateRefs,
                        index + 1,
                        unavailableAssets
                );

                break;
            } catch (MarketDataUnavailableException exception) {
                unavailableAssets.add(new UnavailableAsset(
                        candidateRef.market(),
                        candidateRef.ticker(),
                        exception.getMessage(),
                        StrategyGuideUnavailableReason.MARKET_DATA_UNAVAILABLE
                ));
            }
        }

        return new StrategyGuideBatch(guides, unavailableAssets);
    }

    private StrategySignal resolveSignal(Long portfolioId, CandidateAssetRef candidateRef) {
        if (portfolioRepository.findById(portfolioId).isPresent()) {
            if (candidateRef.investmentTrack() != null) {
                return strategyGuideService.getStrategySignal(
                        portfolioId,
                        candidateRef.market(),
                        candidateRef.ticker(),
                        candidateRef.investmentTrack()
                );
            }

            return strategyGuideService.getStrategySignal(
                    portfolioId,
                    candidateRef.market(),
                    candidateRef.ticker()
            );
        }

        if (candidateRef.investmentTrack() != null) {
            return strategyGuideService.getStrategySignal(
                    candidateRef.market(),
                    candidateRef.ticker(),
                    candidateRef.investmentTrack()
            );
        }

        return strategyGuideService.getStrategySignal(
                candidateRef.market(),
                candidateRef.ticker()
        );
    }

    /**
     * 이 포트폴리오에 사용자가 직접 등록한 Track A 후보({@link PortfolioCandidateAsset})가
     * 있으면 그 후보군을 우선 사용한다. 없으면 기존 호환을 위해 전역
     * {@link AssetProfile} TRACK_A 카탈로그로 대체한다.
     */
    private List<CandidateAssetRef> resolveCandidateRefs(Long portfolioId) {
        List<PortfolioCandidateAsset> portfolioCandidates = portfolioCandidateAssetRepository
                .findAllByPortfolio_IdAndInvestmentTrack(portfolioId, InvestmentTrack.TRACK_A);

        if (!portfolioCandidates.isEmpty()) {
            return portfolioCandidates.stream()
                    .map(candidate -> new CandidateAssetRef(
                            candidate.getMarket(),
                            candidate.getTicker(),
                            candidate.getInvestmentTrack()
                    ))
                    .toList();
        }

        return assetProfileRepository
                .findAllByInvestmentTrack(InvestmentTrack.TRACK_A)
                .stream()
                .map(assetProfile -> new CandidateAssetRef(assetProfile.getMarket(), assetProfile.getTicker(), null))
                .toList();
    }

    private void addRateLimitedAssets(
            List<CandidateAssetRef> candidateRefs,
            int startIndex,
            List<UnavailableAsset> unavailableAssets
    ) {
        for (int index = startIndex; index < candidateRefs.size(); index++) {
            CandidateAssetRef candidateRef = candidateRefs.get(index);

            unavailableAssets.add(new UnavailableAsset(
                    candidateRef.market(),
                    candidateRef.ticker(),
                    "시장 데이터 요청 제한으로 조회하지 못했습니다.",
                    StrategyGuideUnavailableReason.MARKET_DATA_RATE_LIMIT_EXCEEDED
            ));
        }
    }

    private BigDecimal resolveStopLossRatio(Long memberId, Long portfolioId) {
        Optional<com.tradeguide.domain.portfolio.Portfolio> portfolioResult =
                portfolioRepository.findByMember_IdAndId(memberId, portfolioId);
        if (portfolioResult == null) {
            return null;
        }

        return portfolioResult
                .map(portfolio -> portfolio.getRiskPolicy())
                .map(riskPolicy -> riskPolicy.getStopLossRatio())
                .orElse(null);
    }

    /**
     * investmentTrack이 있으면 포트폴리오 후보에서 가져온 값으로, 전역 AssetProfile 조회 없이
     * 전략 신호를 계산한다. null이면 전역 카탈로그 대체 경로이며 기존처럼 전역 AssetProfile
     * 조회로 신호를 계산한다.
     */
    private record CandidateAssetRef(Market market, String ticker, InvestmentTrack investmentTrack) {
    }
}
