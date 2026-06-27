package com.orderflow.order.service;

import java.util.UUID;

/** Requested order does not exist → 404 (schema.mdc). */
public class OrderNotFoundException extends RuntimeException {
  private final UUID orderId;

  public OrderNotFoundException(UUID orderId) {
    super("Order not found: " + orderId);
    this.orderId = orderId;
  }

  public UUID getOrderId() {
    return orderId;
  }
}
