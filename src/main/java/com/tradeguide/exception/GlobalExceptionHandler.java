package com.tradeguide.exception;

import com.tradeguide.dto.ApiErrorCode;
import com.tradeguide.dto.ErrorResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(
            AccessDeniedException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponse(exception.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(
            IllegalArgumentException exception
    ) {
        ErrorResponse response = new ErrorResponse(exception.getMessage());

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException exception
    ) {
        String message = exception.getBindingResult()
                .getFieldErrors()
                .get(0)
                .getDefaultMessage();

        ErrorResponse response = new ErrorResponse(message);

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(response);
    }

    @ExceptionHandler(MarketDataProviderNotConfiguredException.class)
    public ResponseEntity<ErrorResponse> handleMarketDataProviderNotConfiguredException(
            MarketDataProviderNotConfiguredException exception
    ) {
        ErrorResponse response = new ErrorResponse(
                exception.getMessage(),
                ApiErrorCode.MARKET_DATA_PROVIDER_NOT_CONFIGURED
        );

        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(response);
    }

    @ExceptionHandler(StaleMarketDataException.class)
    public ResponseEntity<ErrorResponse> handleStaleMarketDataException(
            StaleMarketDataException exception
    ) {
        ErrorResponse response = new ErrorResponse(
                exception.getMessage(),
                ApiErrorCode.MARKET_DATA_STALE
        );

        return ResponseEntity
                .status(HttpStatus.BAD_GATEWAY)
                .body(response);
    }

    @ExceptionHandler(MarketDataUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleMarketDataUnavailableException(
            MarketDataUnavailableException exception
    ) {
        ErrorResponse response = new ErrorResponse(
                exception.getMessage(),
                ApiErrorCode.MARKET_DATA_UNAVAILABLE
        );

        return ResponseEntity
                .status(HttpStatus.BAD_GATEWAY)
                .body(response);
    }

    @ExceptionHandler(MarketDataRateLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleMarketDataRateLimitExceededException(
            MarketDataRateLimitExceededException exception
    ) {
        ErrorResponse response = new ErrorResponse(
                exception.getMessage(),
                ApiErrorCode.MARKET_DATA_RATE_LIMIT_EXCEEDED
        );

        ResponseEntity.BodyBuilder responseBuilder = ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS);
        if (exception.getRetryAfterSeconds() != null) {
            responseBuilder.header(HttpHeaders.RETRY_AFTER, String.valueOf(exception.getRetryAfterSeconds()));
        }

        return responseBuilder.body(response);
    }

    @ExceptionHandler(MarketDataProviderAccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleMarketDataProviderAccessDeniedException(
            MarketDataProviderAccessDeniedException exception
    ) {
        ErrorResponse response = new ErrorResponse(
                exception.getMessage(),
                ApiErrorCode.MARKET_DATA_PROVIDER_ACCESS_DENIED
        );

        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(response);
    }

    @ExceptionHandler(BrokerConnectionUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleBrokerConnectionUnavailableException(
            BrokerConnectionUnavailableException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ErrorResponse(exception.getMessage(), exception.getCode()));
    }

    @ExceptionHandler(AssetProfileNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleAssetProfileNotFoundException(
            AssetProfileNotFoundException exception) {
        ErrorResponse response = new ErrorResponse(exception.getMessage());

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(response);
    }

    @ExceptionHandler(AssetProfileAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleAssetProfileAlreadyExistsException(
            AssetProfileAlreadyExistsException exception) {
        ErrorResponse response = new ErrorResponse(exception.getMessage());

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(response);
    }

    @ExceptionHandler(PortfolioRiskPolicyNotFoundException.class)
    public ResponseEntity<ErrorResponse> handlePortfolioRiskPolicyNotFoundException(
            PortfolioRiskPolicyNotFoundException exception
    ) {
        ErrorResponse response = new ErrorResponse(exception.getMessage());

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(response);
    }

    @ExceptionHandler(BrokerHoldingSnapshotNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleBrokerHoldingSnapshotNotFoundException(
            BrokerHoldingSnapshotNotFoundException exception
    ) {
        ErrorResponse response = new ErrorResponse(exception.getMessage());

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(response);
    }

    @ExceptionHandler(BrokerHoldingSnapshotItemNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleBrokerHoldingSnapshotItemNotFoundException(
            BrokerHoldingSnapshotItemNotFoundException exception
    ) {
        ErrorResponse response = new ErrorResponse(exception.getMessage());

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(response);
    }

    @ExceptionHandler(PortfolioBrokerHoldingImportNotFoundException.class)
    public ResponseEntity<ErrorResponse> handlePortfolioBrokerHoldingImportNotFoundException(
            PortfolioBrokerHoldingImportNotFoundException exception
    ) {
        ErrorResponse response = new ErrorResponse(exception.getMessage());

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(response);
    }

    @ExceptionHandler(BrokerHoldingImportConflictException.class)
    public ResponseEntity<ErrorResponse> handleBrokerHoldingImportConflictException(
            BrokerHoldingImportConflictException exception
    ) {
        ErrorResponse response = new ErrorResponse(exception.getMessage());

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(response);
    }

    @ExceptionHandler(BrokerHoldingImportUnprocessableException.class)
    public ResponseEntity<ErrorResponse> handleBrokerHoldingImportUnprocessableException(
            BrokerHoldingImportUnprocessableException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ErrorResponse(exception.getMessage(), exception.getCode()));
    }

    @ExceptionHandler(BrokerConnectionDeletionBlockedException.class)
    public ResponseEntity<ErrorResponse> handleBrokerConnectionDeletionBlockedException(
            BrokerConnectionDeletionBlockedException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(exception.getMessage()));
    }

    @ExceptionHandler(BrokerOrderImportNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleBrokerOrderImportNotFoundException(
            BrokerOrderImportNotFoundException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(exception.getMessage()));
    }

    @ExceptionHandler(BrokerOrderImportUnprocessableException.class)
    public ResponseEntity<ErrorResponse> handleBrokerOrderImportUnprocessableException(
            BrokerOrderImportUnprocessableException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ErrorResponse(exception.getMessage()));
    }

    @ExceptionHandler(TradeTransactionProtectedException.class)
    public ResponseEntity<ErrorResponse> handleTradeTransactionProtectedException(
            TradeTransactionProtectedException exception
    ) {
        ErrorResponse response = new ErrorResponse(exception.getMessage());

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(response);
    }

    @ExceptionHandler(BrokerOrderApprovalConflictException.class)
    public ResponseEntity<ErrorResponse> handleBrokerOrderApprovalConflictException(
            BrokerOrderApprovalConflictException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(exception.getMessage(), exception.getCode()));
    }

    @ExceptionHandler(BrokerOrderOverrideConflictException.class)
    public ResponseEntity<ErrorResponse> handleBrokerOrderOverrideConflictException(
            BrokerOrderOverrideConflictException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(exception.getMessage(), exception.getCode()));
    }

    @ExceptionHandler(PortfolioNotFoundException.class)
    public ResponseEntity<ErrorResponse> handlePortfolioNotFoundException(
            PortfolioNotFoundException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(exception.getMessage(), exception.getCode()));
    }

    @ExceptionHandler(PortfolioBrokerLinkNotFoundException.class)
    public ResponseEntity<ErrorResponse> handlePortfolioBrokerLinkNotFoundException(
            PortfolioBrokerLinkNotFoundException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(exception.getMessage(), exception.getCode()));
    }

    @ExceptionHandler(BrokerConnectionReverificationRequiredException.class)
    public ResponseEntity<ErrorResponse> handleBrokerConnectionReverificationRequiredException(
            BrokerConnectionReverificationRequiredException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(exception.getMessage(), exception.getCode()));
    }

    @ExceptionHandler(BrokerReconciliationRunNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleBrokerReconciliationRunNotFoundException(
            BrokerReconciliationRunNotFoundException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(exception.getMessage()));
    }

    @ExceptionHandler(BrokerCallInProgressException.class)
    public ResponseEntity<ErrorResponse> handleBrokerCallInProgressException(
            BrokerCallInProgressException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(exception.getMessage(), exception.getCode()));
    }

    @ExceptionHandler(BrokerCallCooldownException.class)
    public ResponseEntity<ErrorResponse> handleBrokerCallCooldownException(
            BrokerCallCooldownException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .body(new ErrorResponse(exception.getMessage(), exception.getCode()));
    }

    @ExceptionHandler(BrokerHoldingAdjustmentConflictException.class)
    public ResponseEntity<ErrorResponse> handleBrokerHoldingAdjustmentConflictException(
            BrokerHoldingAdjustmentConflictException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(exception.getMessage()));
    }

    @ExceptionHandler(BrokerHoldingAdjustmentUnprocessableException.class)
    public ResponseEntity<ErrorResponse> handleBrokerHoldingAdjustmentUnprocessableException(
            BrokerHoldingAdjustmentUnprocessableException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ErrorResponse(exception.getMessage()));
    }

    @ExceptionHandler(PortfolioBrokerHoldingAdjustmentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handlePortfolioBrokerHoldingAdjustmentNotFoundException(
            PortfolioBrokerHoldingAdjustmentNotFoundException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(exception.getMessage()));
    }

    @ExceptionHandler(PortfolioAssetNotHeldException.class)
    public ResponseEntity<ErrorResponse> handlePortfolioAssetNotHeldException(
            PortfolioAssetNotHeldException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ErrorResponse(exception.getMessage(), exception.getCode()));
    }

    @ExceptionHandler(PortfolioAssetStrategyProfileNotFoundException.class)
    public ResponseEntity<ErrorResponse> handlePortfolioAssetStrategyProfileNotFoundException(
            PortfolioAssetStrategyProfileNotFoundException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(exception.getMessage(), exception.getCode()));
    }

    @ExceptionHandler(UnsupportedInvestmentTrackException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedInvestmentTrackException(
            UnsupportedInvestmentTrackException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ErrorResponse(exception.getMessage(), exception.getCode()));
    }

    @ExceptionHandler(PortfolioCandidateAssetAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handlePortfolioCandidateAssetAlreadyExistsException(
            PortfolioCandidateAssetAlreadyExistsException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(exception.getMessage(), exception.getCode()));
    }

    @ExceptionHandler(PortfolioCandidateAssetNotFoundException.class)
    public ResponseEntity<ErrorResponse> handlePortfolioCandidateAssetNotFoundException(
            PortfolioCandidateAssetNotFoundException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(exception.getMessage(), exception.getCode()));
    }

}
