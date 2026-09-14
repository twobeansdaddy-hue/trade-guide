package com.tradeguide.controller.broker;

import com.tradeguide.domain.broker.BrokerHistoryPageRequest;
import com.tradeguide.domain.broker.BrokerOrderImportItemFilter;
import com.tradeguide.dto.broker.BrokerHistoryPageResponse;
import com.tradeguide.dto.broker.BrokerOrderApprovalResponse;
import com.tradeguide.dto.broker.BrokerOrderImportCreateRequest;
import com.tradeguide.dto.broker.BrokerOrderImportItemResponse;
import com.tradeguide.dto.broker.BrokerOrderImportRunDetailResponse;
import com.tradeguide.dto.broker.BrokerOrderImportRunResponse;
import com.tradeguide.service.auth.MemberAccessService;
import com.tradeguide.service.broker.BrokerOrderImportApprovalService;
import com.tradeguide.service.broker.BrokerOrderImportApprovalWriter;
import com.tradeguide.service.broker.BrokerOrderImportService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


/**
 * 증권사 주문 이력 가져오기·반영 API다.
 *
 * <p>미리보기·조회 엔드포인트는 매매 원장을 바꾸지 않는다. 원장에 쓰는 것은 승인 엔드포인트뿐이며,
 * 그마저도 활성 개시 잔고 기준점 이후 주문만, 재생 검증을 통과했을 때만, 사용자가 실행 단위로
 * 명시적으로 승인해야 반영한다. 자동 반영은 없다.
 */
@RestController
@RequestMapping("/api/members/{memberId}/portfolios/{portfolioId}/broker-order-imports")
public class BrokerOrderImportController {

    private final BrokerOrderImportService brokerOrderImportService;
    private final BrokerOrderImportApprovalService brokerOrderImportApprovalService;
    private final MemberAccessService memberAccessService;

    public BrokerOrderImportController(
            BrokerOrderImportService brokerOrderImportService,
            BrokerOrderImportApprovalService brokerOrderImportApprovalService,
            MemberAccessService memberAccessService
    ) {
        this.brokerOrderImportService = brokerOrderImportService;
        this.brokerOrderImportApprovalService = brokerOrderImportApprovalService;
        this.memberAccessService = memberAccessService;
    }

    @ModelAttribute
    void requireMemberAccess(@PathVariable Long memberId, Authentication authentication) {
        memberAccessService.requireMemberAccess(authentication, memberId);
    }

    /** 주문 이력을 조회해 실행 한 건으로 스테이징한다. 매매 원장은 바뀌지 않는다. */
    @PostMapping
    public ResponseEntity<BrokerOrderImportRunDetailResponse> createPreview(
            @PathVariable Long memberId,
            @PathVariable Long portfolioId,
            @Valid @RequestBody BrokerOrderImportCreateRequest request
    ) {
        Long runId = brokerOrderImportService.createPreview(
                memberId, portfolioId, request.getOrderedFrom(), request.getOrderedTo()).getId();

        // 상세를 별도 호출로 다시 읽는다. 스테이징 트랜잭션은 이미 커밋됐고, 항목·대조 결과는
        // 읽기 트랜잭션 안에서 초기화돼야 한다. 서비스 안에서 이어 부르면 프록시를 거치지 않아
        // 트랜잭션이 열리지 않는다.
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BrokerOrderImportRunDetailResponse.from(
                        brokerOrderImportService.getRun(memberId, portfolioId, runId)));
    }

    /**
     * 실행 이력을 최신순 한 페이지만 돌려준다. 실행은 조회할 때마다 쌓이므로 과거 전체를
     * 한 번에 반환하지 않는다.
     */
    @GetMapping
    public BrokerHistoryPageResponse<BrokerOrderImportRunResponse> getRuns(
            @PathVariable Long memberId,
            @PathVariable Long portfolioId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return BrokerHistoryPageResponse.from(
                brokerOrderImportService.getRuns(memberId, portfolioId, BrokerHistoryPageRequest.of(page, size)),
                BrokerOrderImportRunResponse::from
        );
    }

    @GetMapping("/{runId}")
    public BrokerOrderImportRunDetailResponse getRun(
            @PathVariable Long memberId,
            @PathVariable Long portfolioId,
            @PathVariable Long runId
    ) {
        return BrokerOrderImportRunDetailResponse.from(
                brokerOrderImportService.getRun(memberId, portfolioId, runId));
    }

    /**
     * 실행 한 건의 주문 항목을 최신순 한 페이지만 돌려준다.
     *
     * <p>실행 하나에 수천 건이 담길 수 있어 상세 응답 하나로 전부 내려보내면 응답이 수 MB에
     * 이른다. 상태·종목 거르기도 서버에서 적용한다. 전부 내려보내고 화면에서 거르면 페이지를
     * 나눈 이유가 사라진다.
     */
    @GetMapping("/{runId}/items")
    public BrokerHistoryPageResponse<BrokerOrderImportItemResponse> getRunItems(
            @PathVariable Long memberId,
            @PathVariable Long portfolioId,
            @PathVariable Long runId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String symbol
    ) {
        return BrokerHistoryPageResponse.from(
                brokerOrderImportService.getRunItems(
                        memberId,
                        portfolioId,
                        runId,
                        BrokerOrderImportItemFilter.of(status, symbol),
                        BrokerHistoryPageRequest.of(page, size)
                ),
                BrokerOrderImportItemResponse::from
        );
    }

    /**
     * 실행 한 건을 매매 원장에 반영한다. 이미 반영된 주문은 다시 쓰지 않으므로 재승인 요청은
     * 안전하며, 새로 반영된 주문이 없으면 200을, 하나라도 새로 반영됐으면 201을 돌려준다.
     */
    @PostMapping("/{runId}/approval")
    public ResponseEntity<BrokerOrderApprovalResponse> approveRun(
            @PathVariable Long memberId,
            @PathVariable Long portfolioId,
            @PathVariable Long runId,
            @RequestParam(required = false, defaultValue = "false") boolean acknowledgeIncompleteCoverage
    ) {
        BrokerOrderImportApprovalWriter.ApprovalResult result = brokerOrderImportApprovalService.approve(
                memberId, portfolioId, runId, acknowledgeIncompleteCoverage);

        HttpStatus status = result.writtenCount() > 0 ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(BrokerOrderApprovalResponse.from(result));
    }

    /** 실행 한 건의 반영 승인을 취소한다. 일반 매매 삭제 API는 이 출처의 기록을 지우지 못한다. */
    @DeleteMapping("/{runId}/approval")
    public ResponseEntity<Void> revokeRunApproval(
            @PathVariable Long memberId,
            @PathVariable Long portfolioId,
            @PathVariable Long runId
    ) {
        brokerOrderImportApprovalService.revoke(memberId, portfolioId, runId);
        return ResponseEntity.noContent().build();
    }
}
