package com.tradeguide.controller.broker;

import com.tradeguide.domain.broker.BrokerHistoryPageRequest;
import com.tradeguide.dto.broker.BrokerHistoryPageResponse;
import com.tradeguide.dto.broker.BrokerOrderImportItemOverrideRequest;
import com.tradeguide.dto.broker.BrokerOrderImportItemOverrideResponse;
import com.tradeguide.service.auth.MemberAccessService;
import com.tradeguide.service.broker.BrokerOrderImportItemOverrideService;
import com.tradeguide.service.broker.BrokerOrderImportItemOverrideWriter;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 증권사 주문 이력 실행의 의심 항목 재판정 API다.
 *
 * <p>기존 실행·항목 조회와 승인 계약은 바꾸지 않고 경로만 더한다. 재판정은 원장을 바꾸지 않는다.
 * 반영 허용으로 재판정한 항목도 실행 승인({@code POST .../{runId}/approval})을 거쳐야만 원장에 들어간다.
 */
@RestController
@RequestMapping("/api/members/{memberId}/portfolios/{portfolioId}/broker-order-imports/{runId}")
public class BrokerOrderImportItemOverrideController {

    private final BrokerOrderImportItemOverrideService brokerOrderImportItemOverrideService;
    private final MemberAccessService memberAccessService;

    public BrokerOrderImportItemOverrideController(
            BrokerOrderImportItemOverrideService brokerOrderImportItemOverrideService,
            MemberAccessService memberAccessService
    ) {
        this.brokerOrderImportItemOverrideService = brokerOrderImportItemOverrideService;
        this.memberAccessService = memberAccessService;
    }

    @ModelAttribute
    void requireMemberAccess(@PathVariable Long memberId, Authentication authentication) {
        memberAccessService.requireMemberAccess(authentication, memberId);
    }

    /**
     * 항목 하나를 재판정한다. 새로 기록하면 201을, 같은 결정이 이미 기록돼 있어 기존 기록을
     * 돌려주면 200을 돌려준다.
     */
    @PostMapping("/items/{itemId}/override")
    public ResponseEntity<BrokerOrderImportItemOverrideResponse> overrideItem(
            @PathVariable Long memberId,
            @PathVariable Long portfolioId,
            @PathVariable Long runId,
            @PathVariable Long itemId,
            @Valid @RequestBody BrokerOrderImportItemOverrideRequest request
    ) {
        BrokerOrderImportItemOverrideWriter.OverrideResult result = brokerOrderImportItemOverrideService.createOverride(
                memberId, portfolioId, runId, itemId, request.getDecision(), request.getReason());

        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(BrokerOrderImportItemOverrideResponse.from(result.override()));
    }

    /** 실행 한 건의 재판정 감사 이력을 최신순 한 페이지만 돌려준다. */
    @GetMapping("/item-overrides")
    public BrokerHistoryPageResponse<BrokerOrderImportItemOverrideResponse> getOverrides(
            @PathVariable Long memberId,
            @PathVariable Long portfolioId,
            @PathVariable Long runId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return BrokerHistoryPageResponse.from(
                brokerOrderImportItemOverrideService.getOverrides(
                        memberId, portfolioId, runId, BrokerHistoryPageRequest.of(page, size)),
                BrokerOrderImportItemOverrideResponse::from
        );
    }
}
