package com.tradeguide.service.portfolio;

import com.tradeguide.domain.broker.BrokerConnectionStatus;
import com.tradeguide.domain.broker.BrokerProvider;
import com.tradeguide.domain.member.Member;
import com.tradeguide.domain.market.MarketDataProvider;
import com.tradeguide.domain.market.PortfolioMarketDataPreference;
import com.tradeguide.domain.portfolio.Portfolio;
import com.tradeguide.domain.risk.PortfolioRiskPolicy;
import com.tradeguide.repository.broker.PortfolioBrokerLinkRepository;
import com.tradeguide.repository.member.MemberRepository;
import com.tradeguide.repository.portfolio.PortfolioRepository;
import com.tradeguide.exception.BrokerConnectionUnavailableException;
import com.tradeguide.exception.PortfolioRiskPolicyNotFoundException;
import com.tradeguide.service.market.MarketDataProviderCatalog;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class PortfolioService {

    private final MemberRepository memberRepository;
    private final PortfolioRepository portfolioRepository;
    private final MarketDataProviderCatalog marketDataProviderCatalog;
    private final PortfolioBrokerLinkRepository portfolioBrokerLinkRepository;

    public PortfolioService(
            MemberRepository memberRepository,
            PortfolioRepository portfolioRepository,
            MarketDataProviderCatalog marketDataProviderCatalog,
            PortfolioBrokerLinkRepository portfolioBrokerLinkRepository) {
        this.memberRepository = memberRepository;
        this.portfolioRepository = portfolioRepository;
        this.marketDataProviderCatalog = marketDataProviderCatalog;
        this.portfolioBrokerLinkRepository = portfolioBrokerLinkRepository;
    }

    public Portfolio createPortfolio(Long memberId, String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("포트폴리오 이름은 필수입니다.");
        }

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("회원을 찾을 수 없습니다."));

        Portfolio portfolio = new Portfolio(member, name);

        return portfolioRepository.save(portfolio);
    }

    public List<Portfolio> getPortfolios(Long memberId) {
        if (!memberRepository.existsById(memberId)) {
            throw new IllegalArgumentException("회원을 찾을 수 없습니다.");
        }
        return portfolioRepository.findAllByMember_Id(memberId);
    }

    public PortfolioRiskPolicy updateRiskPolicy(
            Long memberId,
            Long portfolioId,
            BigDecimal maxLossPerTradeRatio,
            BigDecimal maxSingleAssetExposureRatio
    ) {
        return updateRiskPolicy(
                memberId,
                portfolioId,
                maxLossPerTradeRatio,
                maxSingleAssetExposureRatio,
                null
        );
    }

    public PortfolioRiskPolicy updateRiskPolicy(
            Long memberId,
            Long portfolioId,
            BigDecimal maxLossPerTradeRatio,
            BigDecimal maxSingleAssetExposureRatio,
            BigDecimal stopLossRatio
    ) {
        Portfolio portfolio = findPortfolio(memberId, portfolioId);

        PortfolioRiskPolicy riskPolicy = new PortfolioRiskPolicy(
                maxLossPerTradeRatio, maxSingleAssetExposureRatio, stopLossRatio
        );

        portfolio.changeRiskPolicy(riskPolicy);
        portfolioRepository.save(portfolio);

        return riskPolicy;
    }

    public PortfolioRiskPolicy getRiskPolicy(Long memberId, Long portfolioId) {
        Portfolio portfolio = findPortfolio(memberId, portfolioId);
        PortfolioRiskPolicy riskPolicy = portfolio.getRiskPolicy();

        if (riskPolicy == null) {
            throw new PortfolioRiskPolicyNotFoundException("포트폴리오 위험 한도 정책이 설정되지 않았습니다.");
        }

        return riskPolicy;
    }

    public PortfolioMarketDataPreference getMarketDataPreference(
            Long memberId,
            Long portfolioId
    ) {
        return findPortfolio(memberId, portfolioId).getMarketDataPreference();
    }

    public PortfolioMarketDataPreference updateMarketDataPreference(
            Long memberId,
            Long portfolioId,
            MarketDataProvider provider
    ) {
        marketDataProviderCatalog.requireSelectable(provider);

        Portfolio portfolio = findPortfolio(memberId, portfolioId);
        requireVerifiedBrokerConnectionIfNeeded(portfolio, provider);

        PortfolioMarketDataPreference preference = PortfolioMarketDataPreference.unified(provider);
        portfolio.changeMarketDataPreference(preference);
        portfolioRepository.save(portfolio);

        return preference;
    }

    /**
     * {@link MarketDataProvider#requiresBrokerConnection()}인 제공자는 서버 설정만으로
     * 선택할 수 없다. 이 포트폴리오에 그 증권사의 <b>검증된</b> 연결이 실제로 연결되어
     * 있어야 한다. 현재는 토스증권만 해당한다.
     */
    private void requireVerifiedBrokerConnectionIfNeeded(Portfolio portfolio, MarketDataProvider provider) {
        if (provider != MarketDataProvider.TOSS_SECURITIES) {
            return;
        }

        boolean hasVerifiedTossConnection = portfolioBrokerLinkRepository
                .findByPortfolio_Id(portfolio.getId())
                .filter(link -> link.getBrokerConnection().getProvider() == BrokerProvider.TOSS_SECURITIES)
                .filter(link -> link.getBrokerConnection().getStatus() == BrokerConnectionStatus.CONNECTED)
                .isPresent();

        if (!hasVerifiedTossConnection) {
            throw new BrokerConnectionUnavailableException(
                    "토스증권을 시장 데이터 제공자로 선택하려면 먼저 포트폴리오에 검증된 토스증권 연결을 연결해야 합니다."
            );
        }
    }

    private Portfolio findPortfolio(Long memberId, Long portfolioId) {
        return portfolioRepository.findByMember_IdAndId(memberId, portfolioId)
                .orElseThrow(() -> new IllegalArgumentException("포트폴리오를 찾을 수 없습니다.")
                );
    }
}
