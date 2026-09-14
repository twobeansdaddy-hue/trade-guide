package com.tradeguide.controller.broker;

import com.tradeguide.domain.broker.BrokerConnection;
import com.tradeguide.dto.broker.BrokerConnectionCreateRequest;
import com.tradeguide.dto.broker.BrokerConnectionResponse;
import com.tradeguide.service.auth.MemberAccessService;
import com.tradeguide.service.broker.BrokerConnectionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/members/{memberId}/broker-connections")
public class BrokerConnectionController {

    private final BrokerConnectionService brokerConnectionService;
    private final MemberAccessService memberAccessService;

    public BrokerConnectionController(
            BrokerConnectionService brokerConnectionService,
            MemberAccessService memberAccessService
    ) {
        this.brokerConnectionService = brokerConnectionService;
        this.memberAccessService = memberAccessService;
    }

    @ModelAttribute
    void requireMemberAccess(@PathVariable Long memberId, Authentication authentication) {
        memberAccessService.requireMemberAccess(authentication, memberId);
    }

    @GetMapping
    public List<BrokerConnectionResponse> getBrokerConnections(
            @PathVariable Long memberId
    ) {
        return brokerConnectionService.getBrokerConnections(memberId).stream()
                .map(BrokerConnectionResponse::from)
                .toList();
    }

    @PostMapping
    public ResponseEntity<BrokerConnectionResponse> createBrokerConnection(
            @PathVariable Long memberId,
            @Valid @RequestBody BrokerConnectionCreateRequest request
    ) {
        BrokerConnection connection = brokerConnectionService.createBrokerConnection(
                memberId,
                request.getProvider(),
                request.getDisplayName(),
                request.resolveCredentialValues()
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BrokerConnectionResponse.from(connection));
    }

    @DeleteMapping("/{connectionId}")
    public ResponseEntity<Void> deleteBrokerConnection(
            @PathVariable Long memberId,
            @PathVariable Long connectionId
    ) {
        brokerConnectionService.deleteBrokerConnection(memberId, connectionId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{connectionId}/verify")
    public BrokerConnectionResponse verifyBrokerConnection(@PathVariable Long memberId, @PathVariable Long connectionId) {
        return BrokerConnectionResponse.from(brokerConnectionService.verifyBrokerConnection(memberId, connectionId));
    }
}
