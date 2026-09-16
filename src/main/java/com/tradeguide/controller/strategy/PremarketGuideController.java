package com.tradeguide.controller.strategy;

import com.tradeguide.dto.strategy.PremarketGuideResponse;
import com.tradeguide.service.auth.MemberAccessService;
import com.tradeguide.service.strategy.PremarketGuideService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/members/{memberId}/portfolios/{portfolioId}/premarket-guide")
public class PremarketGuideController {

    private final PremarketGuideService premarketGuideService;
    private final MemberAccessService memberAccessService;

    public PremarketGuideController(
            PremarketGuideService premarketGuideService,
            MemberAccessService memberAccessService
    ) {
        this.premarketGuideService = premarketGuideService;
        this.memberAccessService = memberAccessService;
    }

    @ModelAttribute
    void requireMemberAccess(@PathVariable Long memberId, Authentication authentication) {
        memberAccessService.requireMemberAccess(authentication, memberId);
    }

    @GetMapping("/today")
    public PremarketGuideResponse getToday(
            @PathVariable Long memberId,
            @PathVariable Long portfolioId
    ) {
        return premarketGuideService.getToday(memberId, portfolioId);
    }

    @PostMapping("/today")
    public PremarketGuideResponse generateToday(
            @PathVariable Long memberId,
            @PathVariable Long portfolioId,
            @RequestParam(defaultValue = "false") boolean force
    ) {
        return premarketGuideService.generateToday(memberId, portfolioId, force);
    }
}
