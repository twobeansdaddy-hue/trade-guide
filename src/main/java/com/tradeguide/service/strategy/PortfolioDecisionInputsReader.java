package com.tradeguide.service.strategy;

import com.tradeguide.domain.broker.PortfolioBrokerHoldingSnapshot;
import com.tradeguide.domain.holding.Holding;
import com.tradeguide.domain.portfolio.Portfolio;
import com.tradeguide.domain.risk.PortfolioAssetRiskOverride;
import com.tradeguide.domain.strategy.AssetProfile;
import com.tradeguide.domain.strategy.InvestmentTrack;
import com.tradeguide.domain.strategy.PortfolioAssetStrategyProfile;
import com.tradeguide.domain.strategy.PremarketGuideCandidateSource;
import com.tradeguide.domain.strategy.PremarketGuidePortfolioStateDigests;
import com.tradeguide.domain.trade.TradeTransaction;
import com.tradeguide.exception.PortfolioNotFoundException;
import com.tradeguide.repository.broker.PortfolioBrokerHoldingSnapshotRepository;
import com.tradeguide.repository.portfolio.PortfolioRepository;
import com.tradeguide.repository.risk.PortfolioAssetRiskOverrideRepository;
import com.tradeguide.repository.strategy.AssetProfileRepository;
import com.tradeguide.repository.strategy.PortfolioAssetStrategyProfileRepository;
import com.tradeguide.repository.strategy.PortfolioCandidateAssetRepository;
import com.tradeguide.repository.trade.TradeTransactionRepository;
import com.tradeguide.service.holding.HoldingCalculator;
import com.tradeguide.service.strategy.PortfolioDecisionInputs.AssetKey;
import com.tradeguide.service.strategy.PortfolioDecisionInputs.CandidateRef;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 장전 가이드의 포트폴리오 결정 입력을 한 DB 스냅샷에서 읽는다. 호출 쪽 트랜잭션과 분리된 짧은 읽기 전용
 * {@code REPEATABLE READ} 트랜잭션이므로 여러 쿼리가 같은 시점의 커밋 상태를 보고, 외부 시세 호출 전에 끝난다.
 */
@Component
public class PortfolioDecisionInputsReader {

    private final Clock clock;
    private final PortfolioRepository portfolioRepository;
    private final TradeTransactionRepository tradeTransactionRepository;
    private final HoldingCalculator holdingCalculator;
    private final PortfolioAssetStrategyProfileRepository strategyProfileRepository;
    private final PortfolioAssetRiskOverrideRepository riskOverrideRepository;
    private final PortfolioCandidateAssetRepository candidateAssetRepository;
    private final AssetProfileRepository assetProfileRepository;
    private final PortfolioBrokerHoldingSnapshotRepository brokerHoldingSnapshotRepository;
    private final PortfolioStateDigest digest;

    public PortfolioDecisionInputsReader(
            Clock clock,
            PortfolioRepository portfolioRepository,
            TradeTransactionRepository tradeTransactionRepository,
            HoldingCalculator holdingCalculator,
            PortfolioAssetStrategyProfileRepository strategyProfileRepository,
            PortfolioAssetRiskOverrideRepository riskOverrideRepository,
            PortfolioCandidateAssetRepository candidateAssetRepository,
            AssetProfileRepository assetProfileRepository,
            PortfolioBrokerHoldingSnapshotRepository brokerHoldingSnapshotRepository,
            PortfolioStateDigest digest
    ) {
        this.clock = clock;
        this.portfolioRepository = portfolioRepository;
        this.tradeTransactionRepository = tradeTransactionRepository;
        this.holdingCalculator = holdingCalculator;
        this.strategyProfileRepository = strategyProfileRepository;
        this.riskOverrideRepository = riskOverrideRepository;
        this.candidateAssetRepository = candidateAssetRepository;
        this.assetProfileRepository = assetProfileRepository;
        this.brokerHoldingSnapshotRepository = brokerHoldingSnapshotRepository;
        this.digest = digest;
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW, isolation = Isolation.REPEATABLE_READ)
    public PortfolioDecisionInputs read(Long memberId, Long portfolioId) {
        Portfolio portfolio = portfolioRepository.findByMember_IdAndId(memberId, portfolioId)
                .orElseThrow(() -> new PortfolioNotFoundException("포트폴리오를 찾을 수 없습니다."));

        List<TradeTransaction> transactions =
                tradeTransactionRepository.findAllByPortfolio_IdOrderByTradedAtAsc(portfolioId);
        List<Holding> holdings = holdingCalculator.calculate(transactions);

        Map<AssetKey, InvestmentTrack> strategyOverrides = strategyProfileRepository
                .findAllByPortfolio_Id(portfolioId).stream()
                .collect(Collectors.toMap(
                        profile -> new AssetKey(profile.getMarket(), profile.getTicker()),
                        PortfolioAssetStrategyProfile::getInvestmentTrack));
        BigDecimal portfolioStopLossRatio = portfolio.getRiskPolicy() == null
                ? null
                : portfolio.getRiskPolicy().getStopLossRatio();
        Map<AssetKey, BigDecimal> stopLossOverrides = riskOverrideRepository
                .findAllByPortfolio_Id(portfolioId).stream()
                .collect(Collectors.toMap(
                        override -> new AssetKey(override.getMarket(), override.getTicker()),
                        PortfolioAssetRiskOverride::getStopLossRatio));

        List<AssetProfile> catalog = assetProfileRepository.findAll();
        List<CandidateRef> portfolioCandidates = candidateAssetRepository
                .findAllByPortfolio_IdAndInvestmentTrack(portfolioId, InvestmentTrack.TRACK_A).stream()
                .map(candidate -> new CandidateRef(
                        candidate.getMarket(), candidate.getTicker(), candidate.getInvestmentTrack()))
                .toList();
        PremarketGuideCandidateSource candidateSource = portfolioCandidates.isEmpty()
                ? PremarketGuideCandidateSource.GLOBAL_CATALOG
                : PremarketGuideCandidateSource.PORTFOLIO;
        List<CandidateRef> candidates = portfolioCandidates.isEmpty()
                ? catalog.stream()
                        .filter(profile -> profile.getInvestmentTrack() == InvestmentTrack.TRACK_A)
                        .map(profile -> new CandidateRef(profile.getMarket(), profile.getTicker(), null))
                        .toList()
                : portfolioCandidates;

        Optional<PortfolioBrokerHoldingSnapshot> brokerSnapshot =
                brokerHoldingSnapshotRepository.findFirstByPortfolio_IdOrderBySyncedAtDesc(portfolioId);
        Long brokerSnapshotId = brokerSnapshot.map(PortfolioBrokerHoldingSnapshot::getId).orElse(null);
        boolean brokerSnapshotHasItems = brokerSnapshot
                .map(snapshot -> !snapshot.getItems().isEmpty())
                .orElse(false);

        String ledgerSha256 = digest.ledger(transactions);
        String holdingsSha256 = digest.holdings(holdings);
        String strategyOverridesSha256 = digest.strategyOverrides(strategyOverrides);
        String riskSettingsSha256 = digest.riskSettings(portfolioStopLossRatio, stopLossOverrides);
        String candidateSetSha256 = digest.candidates(candidateSource, candidates);
        String assetCatalogSha256 = digest.assetCatalog(catalog);
        PremarketGuidePortfolioStateDigests digests = new PremarketGuidePortfolioStateDigests(
                PortfolioStateDigest.SCHEMA_VERSION,
                clock.instant(),
                ledgerSha256,
                transactions.size(),
                holdingsSha256,
                strategyOverridesSha256,
                riskSettingsSha256,
                candidateSource,
                candidateSetSha256,
                assetCatalogSha256,
                brokerSnapshotId,
                digest.state(ledgerSha256, holdingsSha256, strategyOverridesSha256, riskSettingsSha256,
                        candidateSetSha256, assetCatalogSha256, brokerSnapshotId, brokerSnapshotHasItems));

        return new PortfolioDecisionInputs(portfolioId, holdings, strategyOverrides, portfolioStopLossRatio,
                stopLossOverrides, candidateSource, candidates, brokerSnapshotHasItems, digests);
    }
}
