package com.fincore.infrastructure.web;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fincore.application.usecases.BusinessRuleViolationException;
import com.fincore.application.usecases.IdempotencyConflictException;
import com.fincore.application.usecases.NotFoundException;
import com.fincore.infrastructure.observability.CorrelationContext;
import com.fincore.infrastructure.web.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.UUID;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult()
            .getFieldErrors()
            .stream()
            .map(FieldError::getDefaultMessage)
            .collect(Collectors.joining("; "));
        return errorResponse(HttpStatus.BAD_REQUEST, message, request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        if (UUID.class.equals(ex.getRequiredType())) {
            return errorResponse(HttpStatus.BAD_REQUEST, "invalid UUID value for parameter " + ex.getName(), request);
        }
        return errorResponse(HttpStatus.BAD_REQUEST, "invalid value for parameter " + ex.getName(), request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableMessage(HttpMessageNotReadableException ex, HttpServletRequest request) {
        Throwable mostSpecificCause = ex.getMostSpecificCause();
        if (mostSpecificCause instanceof InvalidFormatException invalidFormatException) {
            Class<?> targetType = invalidFormatException.getTargetType();
            if (UUID.class.equals(targetType)) {
                return errorResponse(
                    HttpStatus.BAD_REQUEST,
                    "invalid UUID value for field " + extractFieldName(invalidFormatException),
                    request
                );
            }
        }
        return errorResponse(HttpStatus.BAD_REQUEST, "request body is invalid", request);
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException ex, HttpServletRequest request) {
        return errorResponse(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(BusinessRuleViolationException.class)
    public ResponseEntity<ErrorResponse> handleBusinessRule(BusinessRuleViolationException ex, HttpServletRequest request) {
        return errorResponse(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), request);
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<ErrorResponse> handleIdempotencyConflict(IdempotencyConflictException ex, HttpServletRequest request) {
        return errorResponse(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        return errorResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.atError()
            .setMessage("http.request.failed")
            .setCause(ex)
            .addKeyValue("correlation_id", CorrelationContext.currentCorrelationId())
            .addKeyValue("path", request.getRequestURI())
            .log();
        return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "internal server error", request);
    }

    private ResponseEntity<ErrorResponse> errorResponse(HttpStatus status, String message, HttpServletRequest request) {
        ErrorResponse body = ApiErrorResponses.build(status, message, request);
        return ResponseEntity.status(status).body(body);
    }

    private String extractFieldName(InvalidFormatException invalidFormatException) {
        return invalidFormatException.getPath()
            .stream()
            .map(JsonMappingException.Reference::getFieldName)
            .filter(fieldName -> fieldName != null && !fieldName.isBlank())
            .findFirst()
            .orElse("unknown");
    }
}
