package com.tradeguide.service.strategy;

import com.tradeguide.domain.asset.AssetListing;
import com.tradeguide.domain.portfolio.Portfolio;
import com.tradeguide.domain.strategy.InvestmentTrack;
import com.tradeguide.domain.strategy.PortfolioCandidateAsset;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.exception.PortfolioCandidateAssetAlreadyExistsException;
import com.tradeguide.exception.PortfolioCandidateAssetNotFoundException;
import com.tradeguide.exception.PortfolioNotFoundException;
import com.tradeguide.repository.portfolio.PortfolioRepository;
import com.tradeguide.repository.strategy.PortfolioCandidateAssetRepository;
import com.tradeguide.service.asset.AssetListingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 포트폴리오별 Track A 후보 종목({@link PortfolioCandidateAsset})을 등록·조회·삭제한다.
 * 이 후보군은 이 포트폴리오의 후보 가이드 조회에만 영향을 주며, 전역
 * {@link com.tradeguide.domain.strategy.AssetProfile} 카탈로그나 다른 포트폴리오는
 * 변경하지 않는다.
 */
@Service
public class PortfolioCandidateAssetService {

    private final PortfolioRepository portfolioRepository;
    private final PortfolioCandidateAssetRepository portfolioCandidateAssetRepository;
    private final AssetListingService assetListingService;
    private final Clock clock;

    public PortfolioCandidateAssetService(
            PortfolioRepository portfolioRepository,
            PortfolioCandidateAssetRepository portfolioCandidateAssetRepository,
            AssetListingService assetListingService,
            Clock clock
    ) {
        this.portfolioRepository = portfolioRepository;
        this.portfolioCandidateAssetRepository = portfolioCandidateAssetRepository;
        this.assetListingService = assetListingService;
        this.clock = clock;
    }

    @Transactional
    public PortfolioCandidateAsset create(
            Long memberId,
            Long portfolioId,
            Market market,
            String ticker,
            String displayName
    ) {
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("표시명은 필수입니다.");
        }

        Portfolio portfolio = requirePortfolio(memberId, portfolioId);

        // AssetListing 검색·등록 경로를 재사용해 실재하지 않는 티커의 등록을 막고,
        // 시장/티커 표기를 이 포트폴리오의 매매 원장과 동일한 기준으로 정규화한다.
        AssetListing listing = assetListingService.ensureActiveListingForTrade(market, ticker);

        if (portfolioCandidateAssetRepository.existsByPortfolio_IdAndMarketAndTicker(
                portfolioId,
                listing.getMarket(),
                listing.getTicker()
        )) {
            throw new PortfolioCandidateAssetAlreadyExistsException(
                    "이미 등록된 후보 종목입니다: " + listing.getMarket() + " / " + listing.getTicker()
            );
        }

        PortfolioCandidateAsset candidate = new PortfolioCandidateAsset(
                portfolio,
                listing.getMarket(),
                listing.getTicker(),
                displayName.trim(),
                InvestmentTrack.TRACK_A,
                LocalDateTime.now(clock)
        );

        return portfolioCandidateAssetRepository.save(candidate);
    }

    @Transactional(readOnly = true)
    public List<PortfolioCandidateAsset> getCandidateAssets(Long memberId, Long portfolioId) {
        requirePortfolio(memberId, portfolioId);

        return portfolioCandidateAssetRepository.findAllByPortfolio_Id(portfolioId);
    }

    @Transactional
    public void delete(Long memberId, Long portfolioId, Market market, String ticker) {
        requirePortfolio(memberId, portfolioId);

        PortfolioCandidateAsset candidate = portfolioCandidateAssetRepository
                .findByPortfolio_IdAndMarketAndTicker(portfolioId, market, ticker)
                .orElseThrow(() -> new PortfolioCandidateAssetNotFoundException(
                        "포트폴리오 후보 종목을 찾을 수 없습니다: " + market + " / " + ticker
                ));

        portfolioCandidateAssetRepository.delete(candidate);
    }

    private Portfolio requirePortfolio(Long memberId, Long portfolioId) {
        return portfolioRepository.findByMember_IdAndId(memberId, portfolioId)
                .orElseThrow(() -> new PortfolioNotFoundException("포트폴리오를 찾을 수 없습니다."));
    }
}
