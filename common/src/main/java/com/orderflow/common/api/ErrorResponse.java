package com.orderflow.common.api;

import java.time.Instant;

/**
 * Consistent error envelope across all services (locked in schema.mdc §8). Produced by each
 * service's global exception handler.
 *
 * @param timestamp when the error occurred
 * @param status HTTP status code
 * @param error short machine code, e.g. "VALIDATION_ERROR", "IDEMPOTENCY_CONFLICT", "NOT_FOUND"
 * @param message human-readable detail
 * @param path request path
 * @param correlationId orderId / request id for log + trace correlation
 */
public record ErrorResponse(
    Instant timestamp,
    int status,
    String error,
    String message,
    String path,
    String correlationId) {}
