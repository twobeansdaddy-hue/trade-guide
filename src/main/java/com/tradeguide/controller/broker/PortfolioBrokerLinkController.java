package com.tradeguide.controller.broker;

import com.tradeguide.domain.broker.BrokerHistoryPageRequest;
import com.tradeguide.domain.broker.BrokerOpeningBalanceBatchResult;
import com.tradeguide.dto.broker.BrokerHistoryPageResponse;
import com.tradeguide.dto.broker.BrokerHoldingAdjustmentCreateRequest;
import com.tradeguide.dto.broker.BrokerHoldingImportBatchCreateRequest;
import com.tradeguide.dto.broker.BrokerHoldingImportBatchResponse;
import com.tradeguide.dto.broker.BrokerHoldingImportCreateRequest;
import com.tradeguide.dto.broker.BrokerHoldingPreviewResponse;
import com.tradeguide.dto.broker.BrokerHoldingSnapshotResponse;
import com.tradeguide.dto.broker.BrokerLinkCandidateResponse;
import com.tradeguide.dto.broker.PortfolioBrokerHoldingAdjustmentResponse;
import com.tradeguide.dto.broker.PortfolioBrokerHoldingImportResponse;
import com.tradeguide.dto.broker.PortfolioBrokerLinkResponse;
import com.tradeguide.dto.broker.PortfolioBrokerLinkUpdateRequest;
import com.tradeguide.service.auth.MemberAccessService;
import com.tradeguide.service.broker.BrokerHoldingPreviewService;
import com.tradeguide.service.broker.PortfolioBrokerHoldingAdjustmentService;
import com.tradeguide.service.broker.PortfolioBrokerHoldingImportService;
import com.tradeguide.service.broker.PortfolioBrokerHoldingSnapshotService;
import com.tradeguide.service.broker.PortfolioBrokerLinkService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 포트폴리오와 증권사 계좌 연결, 읽기 전용 보유 종목 미리보기와 저장된 스냅샷 API다.
 * 주문 등록이나 매매 기록 생성 엔드포인트는 제공하지 않는다.
 */
@RestController
@RequestMapping("/api/members/{memberId}/portfolios/{portfolioId}")
public class PortfolioBrokerLinkController {

    private final PortfolioBrokerLinkService portfolioBrokerLinkService;
    private final BrokerHoldingPreviewService brokerHoldingPreviewService;
    private final PortfolioBrokerHoldingSnapshotService portfolioBrokerHoldingSnapshotService;
    private final PortfolioBrokerHoldingImportService portfolioBrokerHoldingImportService;
    private final PortfolioBrokerHoldingAdjustmentService portfolioBrokerHoldingAdjustmentService;
    private final MemberAccessService memberAccessService;

    public PortfolioBrokerLinkController(
            PortfolioBrokerLinkService portfolioBrokerLinkService,
            BrokerHoldingPreviewService brokerHoldingPreviewService,
            PortfolioBrokerHoldingSnapshotService portfolioBrokerHoldingSnapshotService,
            PortfolioBrokerHoldingImportService portfolioBrokerHoldingImportService,
            PortfolioBrokerHoldingAdjustmentService portfolioBrokerHoldingAdjustmentService,
            MemberAccessService memberAccessService
    ) {
        this.portfolioBrokerLinkService = portfolioBrokerLinkService;
        this.brokerHoldingPreviewService = brokerHoldingPreviewService;
        this.portfolioBrokerHoldingSnapshotService = portfolioBrokerHoldingSnapshotService;
        this.portfolioBrokerHoldingImportService = portfolioBrokerHoldingImportService;
        this.portfolioBrokerHoldingAdjustmentService = portfolioBrokerHoldingAdjustmentService;
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

    @PostMapping("/broker-holding-snapshots")
    public BrokerHoldingSnapshotResponse refreshBrokerHoldingSnapshot(
            @PathVariable Long memberId,
            @PathVariable Long portfolioId
    ) {
        return BrokerHoldingSnapshotResponse.from(
                portfolioBrokerHoldingSnapshotService.refreshSnapshot(memberId, portfolioId)
        );
    }

    @GetMapping("/broker-holding-snapshots/latest")
    public BrokerHoldingSnapshotResponse getLatestBrokerHoldingSnapshot(
            @PathVariable Long memberId,
            @PathVariable Long portfolioId
    ) {
        return BrokerHoldingSnapshotResponse.from(
                portfolioBrokerHoldingSnapshotService.getLatestSnapshot(memberId, portfolioId)
        );
    }

    @GetMapping("/broker-holding-snapshots/latest/comparison")
    public BrokerHoldingPreviewResponse getLatestBrokerHoldingSnapshotComparison(
            @PathVariable Long memberId,
            @PathVariable Long portfolioId
    ) {
        return BrokerHoldingPreviewResponse.from(
                portfolioBrokerHoldingSnapshotService.getLatestSnapshotComparison(memberId, portfolioId)
        );
    }

    @PostMapping("/broker-holding-imports")
    public ResponseEntity<PortfolioBrokerHoldingImportResponse> approveBrokerHoldingOpeningBalance(
            @PathVariable Long memberId,
            @PathVariable Long portfolioId,
            @Valid @RequestBody BrokerHoldingImportCreateRequest request
    ) {
        PortfolioBrokerHoldingImportService.ApprovalResult result = portfolioBrokerHoldingImportService
                .approveOpeningBalance(memberId, portfolioId, request.getSnapshotItemId());

        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status)
                .body(PortfolioBrokerHoldingImportResponse.from(result.importRecord()));
    }

    /**
     * 최신 저장 스냅샷의 {@code ONLY_IN_BROKER} 종목을 한 번에 개시 잔고로 반영한다.
     *
     * <p>반영할 수 없다고 판정된 종목은 실패가 아니라 사유가 붙은 제외로 함께 돌려준다.
     * 하나라도 새로 반영됐으면 201, 전부 제외됐으면 200이다. 이 API는 어떤 경우에도
     * 실제 증권사 주문을 내지 않는다.
     */
    @PostMapping("/broker-holding-imports/batch")
    public ResponseEntity<BrokerHoldingImportBatchResponse> approveBrokerHoldingOpeningBalanceBatch(
            @PathVariable Long memberId,
            @PathVariable Long portfolioId,
            @Valid @RequestBody BrokerHoldingImportBatchCreateRequest request
    ) {
        BrokerOpeningBalanceBatchResult result = portfolioBrokerHoldingImportService
                .approveOpeningBalanceBatch(memberId, portfolioId, request.getSnapshotId());

        HttpStatus status = result.hasApproved() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(BrokerHoldingImportBatchResponse.from(result));
    }

    /**
     * 개시 잔고 승인 감사 이력을 최신순 한 페이지만 돌려준다. 이력은 계속 쌓이므로
     * 과거 전체를 한 번에 반환하지 않는다.
     */
    @GetMapping("/broker-holding-imports")
    public BrokerHistoryPageResponse<PortfolioBrokerHoldingImportResponse> getBrokerHoldingOpeningBalanceImports(
            @PathVariable Long memberId,
            @PathVariable Long portfolioId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return BrokerHistoryPageResponse.from(
                portfolioBrokerHoldingImportService.getImportHistory(
                        memberId, portfolioId, BrokerHistoryPageRequest.of(page, size)),
                PortfolioBrokerHoldingImportResponse::from
        );
    }

    @DeleteMapping("/broker-holding-imports/{importId}")
    public ResponseEntity<Void> revokeBrokerHoldingOpeningBalanceImport(
            @PathVariable Long memberId,
            @PathVariable Long portfolioId,
            @PathVariable Long importId
    ) {
        portfolioBrokerHoldingImportService.revokeOpeningBalance(memberId, portfolioId, importId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 이미 원장에 있는 종목의 증권사 잔고 조정(수량 불일치 해소)을 승인한다. 대상은
     * 최신 저장 스냅샷에서 비교 결과가 {@code QUANTITY_MISMATCH}이고 증권사 수량이
     * 원장 수량보다 많은 종목뿐이며, 이 API는 어떤 경우에도 실제 증권사 주문을 내지
     * 않는다. 같은 스냅샷 항목을 재승인하면 새 거래 없이 기존 결과를 그대로 돌려준다.
     */
    @PostMapping("/broker-holding-adjustments")
    public ResponseEntity<PortfolioBrokerHoldingAdjustmentResponse> approveBrokerHoldingAdjustment(
            @PathVariable Long memberId,
            @PathVariable Long portfolioId,
            @Valid @RequestBody BrokerHoldingAdjustmentCreateRequest request
    ) {
        PortfolioBrokerHoldingAdjustmentService.ApprovalResult result = portfolioBrokerHoldingAdjustmentService
                .approveAdjustment(memberId, portfolioId, request.getSnapshotItemId());

        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status)
                .body(PortfolioBrokerHoldingAdjustmentResponse.from(result.adjustment()));
    }

    /**
     * 잔고 조정 승인 감사 이력을 최신순 한 페이지만 돌려준다. 이력은 계속 쌓이므로
     * 과거 전체를 한 번에 반환하지 않는다.
     */
    @GetMapping("/broker-holding-adjustments")
    public BrokerHistoryPageResponse<PortfolioBrokerHoldingAdjustmentResponse> getBrokerHoldingAdjustments(
            @PathVariable Long memberId,
            @PathVariable Long portfolioId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return BrokerHistoryPageResponse.from(
                portfolioBrokerHoldingAdjustmentService.getAdjustmentHistory(
                        memberId, portfolioId, BrokerHistoryPageRequest.of(page, size)),
                PortfolioBrokerHoldingAdjustmentResponse::from
        );
    }

    @DeleteMapping("/broker-holding-adjustments/{adjustmentId}")
    public ResponseEntity<Void> revokeBrokerHoldingAdjustment(
            @PathVariable Long memberId,
            @PathVariable Long portfolioId,
            @PathVariable Long adjustmentId
    ) {
        portfolioBrokerHoldingAdjustmentService.revokeAdjustment(memberId, portfolioId, adjustmentId);
        return ResponseEntity.noContent().build();
    }
}
