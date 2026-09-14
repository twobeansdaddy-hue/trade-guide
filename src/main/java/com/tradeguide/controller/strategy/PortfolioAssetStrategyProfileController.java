package com.tradeguide.controller.strategy;

import com.tradeguide.domain.trade.Market;
import com.tradeguide.dto.strategy.PortfolioAssetStrategyProfileResponse;
import com.tradeguide.dto.strategy.PortfolioAssetStrategyProfileUpsertRequest;
import com.tradeguide.service.auth.MemberAccessService;
import com.tradeguide.service.strategy.PortfolioAssetStrategyProfileService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 포트폴리오 범위의 투자 트랙 재정의 API다. 재정의는 이 포트폴리오의 보유 종목 가이드에만
 * 영향을 주며, 전역 {@code /api/admin/asset-profiles} 카탈로그나 다른 포트폴리오,
 * 후보 가이드는 바꾸지 않는다.
 */
@RestController
@RequestMapping("/api/members/{memberId}/portfolios/{portfolioId}/strategy-profiles")
public class PortfolioAssetStrategyProfileController {

    private final PortfolioAssetStrategyProfileService portfolioAssetStrategyProfileService;
    private final MemberAccessService memberAccessService;

    public PortfolioAssetStrategyProfileController(
            PortfolioAssetStrategyProfileService portfolioAssetStrategyProfileService,
            MemberAccessService memberAccessService
    ) {
        this.portfolioAssetStrategyProfileService = portfolioAssetStrategyProfileService;
        this.memberAccessService = memberAccessService;
    }

    @ModelAttribute
    void requireMemberAccess(@PathVariable Long memberId, Authentication authentication) {
        memberAccessService.requireMemberAccess(authentication, memberId);
    }

    @GetMapping
    public List<PortfolioAssetStrategyProfileResponse> getHeldAssetStrategyProfiles(
            @PathVariable Long memberId,
            @PathVariable Long portfolioId
    ) {
        return portfolioAssetStrategyProfileService
                .getHeldAssetStrategyProfiles(memberId, portfolioId)
                .stream()
                .map(result -> PortfolioAssetStrategyProfileResponse.of(
                        result.market(),
                        result.ticker(),
                        result.overrideTrack(),
                        result.globalTrack(),
                        result.updatedAt()
                ))
                .toList();
    }

    @PutMapping("/{market}/{ticker}")
    public PortfolioAssetStrategyProfileResponse upsertAssetStrategyProfile(
            @PathVariable Long memberId,
            @PathVariable Long portfolioId,
            @PathVariable Market market,
            @PathVariable String ticker,
            @Valid @RequestBody PortfolioAssetStrategyProfileUpsertRequest request
    ) {
        PortfolioAssetStrategyProfileService.PortfolioAssetStrategyProfileResult result =
                portfolioAssetStrategyProfileService.upsert(
                        memberId,
                        portfolioId,
                        market,
                        ticker,
                        request.getInvestmentTrack()
                );

        return PortfolioAssetStrategyProfileResponse.of(
                result.market(),
                result.ticker(),
                result.overrideTrack(),
                result.globalTrack(),
                result.updatedAt()
        );
    }

    @DeleteMapping("/{market}/{ticker}")
    public ResponseEntity<Void> deleteAssetStrategyProfile(
            @PathVariable Long memberId,
            @PathVariable Long portfolioId,
            @PathVariable Market market,
            @PathVariable String ticker
    ) {
        portfolioAssetStrategyProfileService.delete(memberId, portfolioId, market, ticker);
        return ResponseEntity.noContent().build();
    }
}
