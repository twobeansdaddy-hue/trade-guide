package com.tradeguide.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

public class ErrorResponse {
    private final String message;
    private final ApiErrorCode code;

    public ErrorResponse(String message) {
        this(message, null);
    }

    public ErrorResponse(String message, ApiErrorCode code) {
        this.message = message;
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    /**
     * 오류 구분 코드다. 코드를 부여하지 않은 기존 오류 응답에서는 직렬화에서 제외된다.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public ApiErrorCode getCode() {
        return code;
    }
}
