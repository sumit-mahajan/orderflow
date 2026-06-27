package com.orderflow.inventory.domain;

/** Lifecycle of a stock reservation. Stored as text (see schema.mdc). */
public enum ReservationStatus {
  RESERVED,
  RELEASED
}
