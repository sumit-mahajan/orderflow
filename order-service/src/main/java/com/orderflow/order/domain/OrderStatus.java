package com.orderflow.order.domain;

/** Overall (customer-facing) order outcome, distinct from the internal saga state. */
public enum OrderStatus {
  PLACED,
  CONFIRMED,
  FAILED
}
