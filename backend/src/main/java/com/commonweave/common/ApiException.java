package com.commonweave.common;

import java.util.List;

public class ApiException extends RuntimeException {
    private final ErrorCode code;
    private final List<FieldErrorDto> fieldErrors;
    private final Long retryAfterSeconds;

    public ApiException(ErrorCode code, String message, List<FieldErrorDto> fieldErrors) {
        this(code, message, fieldErrors, null);
    }

    public ApiException(ErrorCode code, String message, List<FieldErrorDto> fieldErrors, Long retryAfterSeconds) {
        super(message);
        this.code = code;
        this.fieldErrors = fieldErrors;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public static ApiException tooManyRequests(String message, long retryAfterSeconds) {
        return new ApiException(ErrorCode.TOO_MANY_REQUESTS, message, List.of(), retryAfterSeconds);
    }

    public Long retryAfterSeconds() {
        return retryAfterSeconds;
    }

    public ApiException(ErrorCode code, String message) {
        this(code, message, List.of());
    }

    public ErrorCode code() {
        return code;
    }

    public List<FieldErrorDto> fieldErrors() {
        return fieldErrors;
    }

    public static ApiException notFound(String what) {
        return new ApiException(ErrorCode.NOT_FOUND, what + " not found");
    }

    public static ApiException forbidden(String message) {
        return new ApiException(ErrorCode.FORBIDDEN, message);
    }

    public static ApiException conflict(String message) {
        return new ApiException(ErrorCode.CONFLICT, message);
    }

    public static ApiException invalid(String field, String message) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, "Validation failed",
                List.of(new FieldErrorDto(field, message)));
    }
}
