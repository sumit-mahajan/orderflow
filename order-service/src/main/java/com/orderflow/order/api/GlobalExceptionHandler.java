package com.orderflow.order.api;

import com.orderflow.common.api.ErrorResponse;
import com.orderflow.order.service.IdempotencyConflictException;
import com.orderflow.order.service.OrderNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps exceptions to the consistent error envelope (schema.mdc §8). ≈ ASP.NET Core exception
 * middleware / IExceptionHandler. A failed saga step is NOT handled here — it's a business outcome
 * surfaced via saga state + SSE, not an HTTP error.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> onInvalidBody(
      MethodArgumentNotValidException ex, HttpServletRequest request) {
    String detail =
        ex.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(f -> f.getField() + " " + f.getDefaultMessage())
            .orElse("Invalid request");
    return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", detail, request);
  }

  @ExceptionHandler({MissingRequestHeaderException.class, ConstraintViolationException.class})
  public ResponseEntity<ErrorResponse> onMissingOrInvalidParam(
      Exception ex, HttpServletRequest request) {
    return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", ex.getMessage(), request);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ErrorResponse> onMalformed(
      HttpMessageNotReadableException ex, HttpServletRequest request) {
    return build(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "Malformed JSON request", request);
  }

  @ExceptionHandler(IdempotencyConflictException.class)
  public ResponseEntity<ErrorResponse> onIdempotencyConflict(
      IdempotencyConflictException ex, HttpServletRequest request) {
    return build(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT", ex.getMessage(), request);
  }

  @ExceptionHandler(OrderNotFoundException.class)
  public ResponseEntity<ErrorResponse> onNotFound(
      OrderNotFoundException ex, HttpServletRequest request) {
    return build(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage(), request);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> onUnexpected(Exception ex, HttpServletRequest request) {
    log.error("Unexpected error handling {}", request.getRequestURI(), ex);
    return build(
        HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred", request);
  }

  private ResponseEntity<ErrorResponse> build(
      HttpStatus status, String code, String message, HttpServletRequest request) {
    ErrorResponse body =
        new ErrorResponse(
            Instant.now(), status.value(), code, message, request.getRequestURI(), "-");
    return ResponseEntity.status(status).body(body);
  }
}
