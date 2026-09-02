package com.tradeguide.controller.trade;

import com.tradeguide.domain.trade.TradeTransaction;
import com.tradeguide.dto.trade.TradeTransactionCreateRequest;
import com.tradeguide.dto.trade.TradeTransactionResponse;
import com.tradeguide.service.trade.TradeTransactionService;
import com.tradeguide.service.auth.MemberAccessService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;

import java.util.List;

@RestController
@RequestMapping(
        "/api/members/{memberId}/portfolios/{portfolioId}/transactions"
)
public class TradeTransactionController {

    private final TradeTransactionService tradeTransactionService;
    private final MemberAccessService memberAccessService;

    public TradeTransactionController(
            TradeTransactionService tradeTransactionService,
            MemberAccessService memberAccessService
    ) {
        this.tradeTransactionService = tradeTransactionService;
        this.memberAccessService = memberAccessService;
    }

    @PostMapping
    public ResponseEntity<TradeTransactionResponse> createTradeTransaction(
            @PathVariable Long memberId,
            @PathVariable Long portfolioId,
            @Valid @RequestBody TradeTransactionCreateRequest request,
            Authentication authentication
    ) {
        memberAccessService.requireMemberAccess(authentication, memberId);
        TradeTransaction transaction =
                tradeTransactionService.createTradeTransaction(
                        memberId,
                        portfolioId,
                        request.getMarket(),
                        request.getTicker(),
                        request.getTradeType(),
                        request.getQuantity(),
                        request.getExecutedPrice(),
                        request.getFee(),
                        request.getTradedAt()
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(TradeTransactionResponse.from(transaction));
    }

    @GetMapping
    public List<TradeTransactionResponse> getTradeTransactions(
            @PathVariable Long memberId,
            @PathVariable Long portfolioId,
            Authentication authentication
    ) {
        memberAccessService.requireMemberAccess(authentication, memberId);

        return tradeTransactionService
                .getTradeTransactions(memberId, portfolioId)
                .stream()
                .map(TradeTransactionResponse::from)
                .toList();
    }

    @DeleteMapping("/{transactionId}")
    public ResponseEntity<Void> deleteTradeTransaction(
            @PathVariable Long memberId,
            @PathVariable Long portfolioId,
            @PathVariable Long transactionId,
            Authentication authentication
    ) {
        memberAccessService.requireMemberAccess(authentication, memberId);
        tradeTransactionService.deleteTradeTransaction(
                memberId,
                portfolioId,
                transactionId
        );

        return ResponseEntity.noContent().build();
    }
}
