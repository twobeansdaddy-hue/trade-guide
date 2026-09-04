package com.tradeguide.controller.broker;

import com.tradeguide.dto.broker.BrokerHoldingPreviewResponse;
import com.tradeguide.dto.broker.BrokerLinkCandidateResponse;
import com.tradeguide.dto.broker.PortfolioBrokerLinkResponse;
import com.tradeguide.dto.broker.PortfolioBrokerLinkUpdateRequest;
import com.tradeguide.service.auth.MemberAccessService;
import com.tradeguide.service.broker.BrokerHoldingPreviewService;
import com.tradeguide.service.broker.PortfolioBrokerLinkService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 포트폴리오와 증권사 계좌 연결, 그리고 읽기 전용 보유 종목 미리보기 API다.
 * 주문 등록이나 매매 기록 생성 엔드포인트는 제공하지 않는다.
 */
@RestController
@RequestMapping("/api/members/{memberId}/portfolios/{portfolioId}")
public class PortfolioBrokerLinkController {

    private final PortfolioBrokerLinkService portfolioBrokerLinkService;
    private final BrokerHoldingPreviewService brokerHoldingPreviewService;
    private final MemberAccessService memberAccessService;

    public PortfolioBrokerLinkController(
            PortfolioBrokerLinkService portfolioBrokerLinkService,
            BrokerHoldingPreviewService brokerHoldingPreviewService,
            MemberAccessService memberAccessService
    ) {
        this.portfolioBrokerLinkService = portfolioBrokerLinkService;
        this.brokerHoldingPreviewService = brokerHoldingPreviewService;
        this.memberAccessService = memberAccessService;
    }

    @ModelAttribute
    void requireMemberAccess(@PathVariable Long memberId, Authentication authentication) {
        memberAccessService.requireMemberAccess(authentication, memberId);
    }

    @GetMapping("/broker-link-candidates")
    public List<BrokerLinkCandidateResponse> getBrokerLinkCandidates(
            @PathVariable Long memberId,
            @PathVariable Long portfolioId
    ) {
        return portfolioBrokerLinkService.getLinkCandidates(memberId, portfolioId).stream()
                .map(BrokerLinkCandidateResponse::from)
                .toList();
    }

    @GetMapping("/broker-links")
    public List<PortfolioBrokerLinkResponse> getBrokerLinks(
            @PathVariable Long memberId,
            @PathVariable Long portfolioId
    ) {
        return portfolioBrokerLinkService.getBrokerLinks(memberId, portfolioId).stream()
                .map(PortfolioBrokerLinkResponse::from)
                .toList();
    }

    @PutMapping("/broker-links/{connectionId}")
    public PortfolioBrokerLinkResponse linkBrokerAccount(
            @PathVariable Long memberId,
            @PathVariable Long portfolioId,
            @PathVariable Long connectionId,
            @Valid @RequestBody PortfolioBrokerLinkUpdateRequest request
    ) {
        return PortfolioBrokerLinkResponse.from(portfolioBrokerLinkService.linkBrokerAccount(
                memberId,
                portfolioId,
                connectionId,
                request.getBrokerAccountId()
        ));
    }

    @DeleteMapping("/broker-links/{connectionId}")
    public ResponseEntity<Void> unlinkBrokerAccount(
            @PathVariable Long memberId,
            @PathVariable Long portfolioId,
            @PathVariable Long connectionId
    ) {
        portfolioBrokerLinkService.unlinkBrokerAccount(memberId, portfolioId, connectionId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/broker-sync-preview")
    public BrokerHoldingPreviewResponse getBrokerSyncPreview(
            @PathVariable Long memberId,
            @PathVariable Long portfolioId
    ) {
        return BrokerHoldingPreviewResponse.from(
                brokerHoldingPreviewService.getHoldingPreview(memberId, portfolioId)
        );
    }
}
