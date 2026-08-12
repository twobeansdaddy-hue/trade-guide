package com.tradeguide.controller.trade;

import com.tradeguide.domain.trade.TradeTransaction;
import com.tradeguide.dto.trade.TradeTransactionCreateRequest;
import com.tradeguide.dto.trade.TradeTransactionResponse;
import com.tradeguide.service.trade.TradeTransactionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(
        "/api/members/{memberId}/portfolios/{portfolioId}/transactions"
)
public class TradeTransactionController {

    private final TradeTransactionService tradeTransactionService;

    public TradeTransactionController(
            TradeTransactionService tradeTransactionService
    ) {
        this.tradeTransactionService = tradeTransactionService;
    }

    @PostMapping
    public ResponseEntity<TradeTransactionResponse> createTradeTransaction(
            @PathVariable Long memberId,
            @PathVariable Long portfolioId,
            @Valid @RequestBody TradeTransactionCreateRequest request
    ) {
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
}