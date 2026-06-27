package com.orderflow.order.service;

/** Same Idempotency-Key reused with a different request body → 409 (schema.mdc). */
public class IdempotencyConflictException extends RuntimeException {
  public IdempotencyConflictException(String key) {
    super("Idempotency-Key '" + key + "' was already used with a different request body");
  }
}
