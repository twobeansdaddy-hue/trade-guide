package com.tradeguide.exception;

import com.tradeguide.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

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

    @ExceptionHandler(MarketDataUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleMarketDataUnavailableException(
            MarketDataUnavailableException exception
    ) {
        ErrorResponse response = new ErrorResponse(exception.getMessage());

        return ResponseEntity
                .status(HttpStatus.BAD_GATEWAY)
                .body(response);
    }

    @ExceptionHandler(MarketDataRateLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleMarketDataRateLimitExceededException(
            MarketDataRateLimitExceededException exception
    ) {
        ErrorResponse response = new ErrorResponse(exception.getMessage());

        return ResponseEntity
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .body(response);
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

}
