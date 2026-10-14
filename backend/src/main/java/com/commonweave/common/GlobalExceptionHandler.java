package com.commonweave.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> api(ApiException e, HttpServletRequest req) {
        if (e.retryAfterSeconds() != null) {
            return ResponseEntity.status(e.code().status()).header("Retry-After", String.valueOf(e.retryAfterSeconds()))
                    .body(ApiErrorWriter.body(e.code(), e.getMessage(), e.fieldErrors(), req.getRequestURI()));
        }
        return respond(e.code(), e.getMessage(), e.fieldErrors(), req);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> invalidBody(MethodArgumentNotValidException e, HttpServletRequest req) {
        List<FieldErrorDto> fields = e.getBindingResult().getFieldErrors().stream()
                .map(f -> new FieldErrorDto(f.getField(), String.valueOf(f.getDefaultMessage()))).toList();
        return respond(ErrorCode.VALIDATION_FAILED, "Validation failed", fields, req);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiError> constraint(ConstraintViolationException e, HttpServletRequest req) {
        List<FieldErrorDto> fields = e.getConstraintViolations().stream()
                .map(v -> new FieldErrorDto(v.getPropertyPath().toString(), v.getMessage())).toList();
        return respond(ErrorCode.VALIDATION_FAILED, "Validation failed", fields, req);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class})
    ResponseEntity<ApiError> malformed(Exception e, HttpServletRequest req) {
        return respond(ErrorCode.VALIDATION_FAILED, "Malformed request", List.of(), req);
    }

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    ResponseEntity<ApiError> noRoute(Exception e, HttpServletRequest req) {
        return respond(ErrorCode.NOT_FOUND, "Not found", List.of(), req);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiError> method(Exception e, HttpServletRequest req) {
        return respond(ErrorCode.METHOD_NOT_ALLOWED, "Method not allowed", List.of(), req);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> integrity(DataIntegrityViolationException e, HttpServletRequest req) {
        String detail = String.valueOf(e.getMostSpecificCause().getMessage());
        if (detail.contains("uq_handoffs_active_listing")) {
            return respond(ErrorCode.LISTING_UNAVAILABLE, "This listing is already reserved", List.of(), req);
        }
        return respond(ErrorCode.CONFLICT, "The request conflicts with existing data", List.of(), req);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<ApiError> optimistic(Exception e, HttpServletRequest req) {
        return respond(ErrorCode.CONFLICT, "The resource was modified concurrently, please retry", List.of(), req);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception e, HttpServletRequest req) {
        log.error("Unhandled error on {} {}", req.getMethod(), req.getRequestURI(), e);
        return respond(ErrorCode.INTERNAL, "Unexpected server error", List.of(), req);
    }

    private ResponseEntity<ApiError> respond(ErrorCode code, String message, List<FieldErrorDto> fields,
                                             HttpServletRequest req) {
        return ResponseEntity.status(code.status())
                .body(ApiErrorWriter.body(code, message, fields, req.getRequestURI()));
    }
}
