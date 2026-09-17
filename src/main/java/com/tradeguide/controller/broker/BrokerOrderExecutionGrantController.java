package com.tradeguide.controller.broker;

import com.tradeguide.dto.broker.BrokerOrderExecutionGrantCreateRequest;
import com.tradeguide.dto.broker.BrokerOrderExecutionGrantResponse;
import com.tradeguide.service.auth.MemberAccessService;
import com.tradeguide.service.broker.BrokerOrderExecutionGrantService;
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
import org.springframework.web.bind.annotation.RestController;

/**
 * 오토매매 opt-in 동의를 만들고 켜고 끄는 API다.
 *
 * <p>이 컨트롤러는 실제 주문을 제출하지 않는다 - 동의 상태만 관리한다
 * (`docs/agent-tasks/claude-broker-order-execution-grant-implementation-20260917.md`
 * 범위).
 */
@RestController
@RequestMapping("/api/members/{memberId}/broker-connections/{connectionId}/order-execution-grant")
public class BrokerOrderExecutionGrantController {

    private final BrokerOrderExecutionGrantService brokerOrderExecutionGrantService;
    private final MemberAccessService memberAccessService;

    public BrokerOrderExecutionGrantController(
            BrokerOrderExecutionGrantService brokerOrderExecutionGrantService,
            MemberAccessService memberAccessService
    ) {
        this.brokerOrderExecutionGrantService = brokerOrderExecutionGrantService;
        this.memberAccessService = memberAccessService;
    }

    @ModelAttribute
    void requireMemberAccess(@PathVariable Long memberId, Authentication authentication) {
        memberAccessService.requireMemberAccess(authentication, memberId);
    }

    @GetMapping
    public BrokerOrderExecutionGrantResponse getGrant(
            @PathVariable Long memberId,
            @PathVariable Long connectionId
    ) {
        return BrokerOrderExecutionGrantResponse.from(
                brokerOrderExecutionGrantService.getGrant(memberId, connectionId));
    }

    @PostMapping
    public ResponseEntity<BrokerOrderExecutionGrantResponse> createGrant(
            @PathVariable Long memberId,
            @PathVariable Long connectionId,
            @Valid @RequestBody BrokerOrderExecutionGrantCreateRequest request
    ) {
        var grant = brokerOrderExecutionGrantService.createGrant(
                memberId,
                connectionId,
                request.getStrategyId(),
                request.getMaxPositionSizePerOrderPercent(),
                request.getMaxDailyOrderCount(),
                request.getConsentVersion()
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BrokerOrderExecutionGrantResponse.from(grant));
    }

    @PostMapping("/pause")
    public BrokerOrderExecutionGrantResponse pauseGrant(
            @PathVariable Long memberId,
            @PathVariable Long connectionId
    ) {
        Long grantId = brokerOrderExecutionGrantService.getGrant(memberId, connectionId).getId();
        return BrokerOrderExecutionGrantResponse.from(
                brokerOrderExecutionGrantService.pauseGrant(memberId, grantId));
    }

    @PostMapping("/revoke")
    public BrokerOrderExecutionGrantResponse revokeGrant(
            @PathVariable Long memberId,
            @PathVariable Long connectionId
    ) {
        Long grantId = brokerOrderExecutionGrantService.getGrant(memberId, connectionId).getId();
        return BrokerOrderExecutionGrantResponse.from(
                brokerOrderExecutionGrantService.revokeGrant(memberId, grantId));
    }

    @PostMapping("/reactivate")
    public BrokerOrderExecutionGrantResponse reactivateGrant(
            @PathVariable Long memberId,
            @PathVariable Long connectionId
    ) {
        Long grantId = brokerOrderExecutionGrantService.getGrant(memberId, connectionId).getId();
        return BrokerOrderExecutionGrantResponse.from(
                brokerOrderExecutionGrantService.reactivateGrant(memberId, grantId));
    }
}
