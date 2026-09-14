package com.tradeguide.service.strategy;

import com.tradeguide.domain.holding.Holding;
import com.tradeguide.domain.portfolio.Portfolio;
import com.tradeguide.domain.strategy.AssetProfile;
import com.tradeguide.domain.strategy.InvestmentTrack;
import com.tradeguide.domain.strategy.PortfolioAssetStrategyProfile;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.exception.PortfolioAssetNotHeldException;
import com.tradeguide.exception.PortfolioAssetStrategyProfileNotFoundException;
import com.tradeguide.exception.PortfolioNotFoundException;
import com.tradeguide.repository.portfolio.PortfolioRepository;
import com.tradeguide.repository.strategy.AssetProfileRepository;
import com.tradeguide.repository.strategy.PortfolioAssetStrategyProfileRepository;
import com.tradeguide.service.holding.HoldingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 포트폴리오 범위의 투자 트랙 재정의({@link PortfolioAssetStrategyProfile})를 관리한다.
 * 이 재정의는 이 포트폴리오의 보유 종목 가이드에만 영향을 주며, 전역
 * {@link AssetProfile} 카탈로그나 다른 포트폴리오, 후보 가이드는 변경하지 않는다.
 */
@Service
public class PortfolioAssetStrategyProfileService {

    private final PortfolioRepository portfolioRepository;
    private final HoldingService holdingService;
    private final PortfolioAssetStrategyProfileRepository portfolioAssetStrategyProfileRepository;
    private final AssetProfileRepository assetProfileRepository;
    private final StrategySelector strategySelector;
    private final Clock clock;

    public PortfolioAssetStrategyProfileService(
            PortfolioRepository portfolioRepository,
            HoldingService holdingService,
            PortfolioAssetStrategyProfileRepository portfolioAssetStrategyProfileRepository,
            AssetProfileRepository assetProfileRepository,
            StrategySelector strategySelector,
            Clock clock
    ) {
        this.portfolioRepository = portfolioRepository;
        this.holdingService = holdingService;
        this.portfolioAssetStrategyProfileRepository = portfolioAssetStrategyProfileRepository;
        this.assetProfileRepository = assetProfileRepository;
        this.strategySelector = strategySelector;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<PortfolioAssetStrategyProfileResult> getHeldAssetStrategyProfiles(
            Long memberId,
            Long portfolioId
    ) {
        requirePortfolio(memberId, portfolioId);

        List<Holding> holdings = holdingService.getHoldings(memberId, portfolioId);
        List<PortfolioAssetStrategyProfile> overrides =
                portfolioAssetStrategyProfileRepository.findAllByPortfolio_Id(portfolioId);

        return holdings.stream()
                .map(holding -> {
                    InvestmentTrack overrideTrack = overrides.stream()
                            .filter(override -> matches(override, holding.getMarket(), holding.getTicker()))
                            .findFirst()
                            .map(PortfolioAssetStrategyProfile::getInvestmentTrack)
                            .orElse(null);

                    LocalDateTime updatedAt = overrides.stream()
                            .filter(override -> matches(override, holding.getMarket(), holding.getTicker()))
                            .findFirst()
                            .map(PortfolioAssetStrategyProfile::getUpdatedAt)
                            .orElse(null);

                    InvestmentTrack globalTrack = assetProfileRepository
                            .findByMarketAndTicker(holding.getMarket(), holding.getTicker())
                            .map(AssetProfile::getInvestmentTrack)
                            .orElse(null);

                    return new PortfolioAssetStrategyProfileResult(
                            holding.getMarket(),
                            holding.getTicker(),
                            overrideTrack,
                            globalTrack,
                            updatedAt
                    );
                })
                .toList();
    }

    @Transactional
    public PortfolioAssetStrategyProfileResult upsert(
            Long memberId,
            Long portfolioId,
            Market market,
            String ticker,
            InvestmentTrack investmentTrack
    ) {
        Portfolio portfolio = requirePortfolio(memberId, portfolioId);
        requireHeld(memberId, portfolioId, market, ticker);
        requireSupportedTrack(investmentTrack);

        LocalDateTime now = LocalDateTime.now(clock);

        Optional<PortfolioAssetStrategyProfile> existing = portfolioAssetStrategyProfileRepository
                .findByPortfolio_IdAndMarketAndTicker(portfolioId, market, ticker);

        PortfolioAssetStrategyProfile override;
        if (existing.isPresent()) {
            override = existing.get();
            override.changeInvestmentTrack(investmentTrack, now);
            override = portfolioAssetStrategyProfileRepository.save(override);
        } else {
            override = new PortfolioAssetStrategyProfile(
                    portfolio,
                    market,
                    ticker,
                    investmentTrack,
                    now
            );
            override = portfolioAssetStrategyProfileRepository.save(override);
        }

        InvestmentTrack globalTrack = assetProfileRepository
                .findByMarketAndTicker(market, ticker)
                .map(AssetProfile::getInvestmentTrack)
                .orElse(null);

        return new PortfolioAssetStrategyProfileResult(
                override.getMarket(),
                override.getTicker(),
                override.getInvestmentTrack(),
                globalTrack,
                override.getUpdatedAt()
        );
    }

    @Transactional
    public void delete(
            Long memberId,
            Long portfolioId,
            Market market,
            String ticker
    ) {
        requirePortfolio(memberId, portfolioId);

        PortfolioAssetStrategyProfile override = portfolioAssetStrategyProfileRepository
                .findByPortfolio_IdAndMarketAndTicker(portfolioId, market, ticker)
                .orElseThrow(() -> new PortfolioAssetStrategyProfileNotFoundException(
                        "포트폴리오 전략 프로필 재정의를 찾을 수 없습니다: " + market + " / " + ticker
                ));

        portfolioAssetStrategyProfileRepository.delete(override);
    }

    /**
     * 실행 가능한 {@link TradingStrategy} 구현이 없는 트랙은 재정의로 저장할 수 없다.
     */
    private void requireSupportedTrack(InvestmentTrack investmentTrack) {
        strategySelector.select(investmentTrack);
    }

    private void requireHeld(Long memberId, Long portfolioId, Market market, String ticker) {
        boolean held = holdingService.getHoldings(memberId, portfolioId).stream()
                .anyMatch(holding -> holding.getMarket() == market
                        && holding.getTicker().equalsIgnoreCase(ticker));

        if (!held) {
            throw new PortfolioAssetNotHeldException(
                    "현재 보유하지 않은 종목에는 전략 프로필을 설정할 수 없습니다: " + market + " / " + ticker
            );
        }
    }

    private Portfolio requirePortfolio(Long memberId, Long portfolioId) {
        return portfolioRepository.findByMember_IdAndId(memberId, portfolioId)
                .orElseThrow(() -> new PortfolioNotFoundException("포트폴리오를 찾을 수 없습니다."));
    }

    private boolean matches(PortfolioAssetStrategyProfile override, Market market, String ticker) {
        return override.getMarket() == market && override.getTicker().equalsIgnoreCase(ticker);
    }

    /**
     * 보유 종목 한 건의 재정의/전역 트랙 상태를 담는 서비스 계층 결과다. 컨트롤러 DTO로
     * 변환하기 전 단계의 값 객체다.
     */
    public record PortfolioAssetStrategyProfileResult(
            Market market,
            String ticker,
            InvestmentTrack overrideTrack,
            InvestmentTrack globalTrack,
            LocalDateTime updatedAt
    ) {
    }
}
