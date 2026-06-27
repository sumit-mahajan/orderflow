package com.orderflow.order.service;

import java.util.UUID;

/**
 * Could not acquire the per-order saga lock. Thrown from the Kafka event listener so the message is
 * redelivered and retried later, rather than processed without the lock.
 */
public class SagaLockedException extends RuntimeException {
  public SagaLockedException(UUID orderId) {
    super("Could not acquire saga lock for order " + orderId);
  }
}
