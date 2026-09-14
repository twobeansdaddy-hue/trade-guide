package com.tradeguide.controller.broker;

import com.tradeguide.domain.broker.BrokerHistoryPageRequest;
import com.tradeguide.dto.broker.BrokerHistoryPageResponse;
import com.tradeguide.dto.broker.BrokerReconciliationRunDetailResponse;
import com.tradeguide.dto.broker.BrokerReconciliationRunResponse;
import com.tradeguide.service.auth.MemberAccessService;
import com.tradeguide.service.broker.BrokerReconciliationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 사용자 주도 원장 정합성 점검 API다.
 *
 * <p>이 컨트롤러의 모든 엔드포인트는 읽기 전용이다. 저장된 최신 보유 종목 스냅샷과 현재
 * 매매 원장만 비교하며, 증권사를 호출하지 않고 {@code TradeTransaction}이나 파생
 * {@code Holding}을 바꾸지 않는다.
 */
@RestController
@RequestMapping("/api/members/{memberId}/portfolios/{portfolioId}/broker-reconciliations")
public class BrokerReconciliationController {

    private final BrokerReconciliationService brokerReconciliationService;
    private final MemberAccessService memberAccessService;

    public BrokerReconciliationController(
            BrokerReconciliationService brokerReconciliationService,
            MemberAccessService memberAccessService
    ) {
        this.brokerReconciliationService = brokerReconciliationService;
        this.memberAccessService = memberAccessService;
    }

    @ModelAttribute
    void requireMemberAccess(@PathVariable Long memberId, Authentication authentication) {
        memberAccessService.requireMemberAccess(authentication, memberId);
    }

    /**
     * 저장된 최신 스냅샷과 현재 원장을 비교해 정합성 점검 한 건을 만든다. 항상 새 실행을
     * 남기며, 이 요청만으로는 증권사를 호출하지 않는다. 최신 상태를 보려면 스냅샷을 먼저
     * 갱신해야 한다.
     */
    @PostMapping
    public ResponseEntity<BrokerReconciliationRunDetailResponse> createReconciliation(
            @PathVariable Long memberId,
            @PathVariable Long portfolioId
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BrokerReconciliationRunDetailResponse.from(
                        brokerReconciliationService.createReconciliation(memberId, portfolioId)));
    }

    /**
     * 점검 실행 이력을 최신순 한 페이지만 돌려준다. 실행은 조회할 때마다 쌓이므로 과거 전체를
     * 한 번에 반환하지 않는다.
     */
    @GetMapping
    public BrokerHistoryPageResponse<BrokerReconciliationRunResponse> getReconciliations(
            @PathVariable Long memberId,
            @PathVariable Long portfolioId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return BrokerHistoryPageResponse.from(
                brokerReconciliationService.getRuns(
                        memberId, portfolioId, BrokerHistoryPageRequest.of(page, size)),
                BrokerReconciliationRunResponse::from
        );
    }

    @GetMapping("/{runId}")
    public BrokerReconciliationRunDetailResponse getReconciliation(
            @PathVariable Long memberId,
            @PathVariable Long portfolioId,
            @PathVariable Long runId
    ) {
        return BrokerReconciliationRunDetailResponse.from(
                brokerReconciliationService.getRun(memberId, portfolioId, runId));
    }
}
