package com.orderflow.common.messaging;

/**
 * Result events participant services publish back to the orchestrator. M1 uses only the inventory
 * events; the rest are declared for forward compatibility (M2/M3).
 */
public enum EventType {
  INVENTORY_RESERVED,
  INVENTORY_RESERVATION_FAILED,
  INVENTORY_RELEASED,
  PAYMENT_CAPTURED,
  PAYMENT_FAILED,
  PAYMENT_REFUNDED,
  SHIPMENT_INITIATED,
  SHIPMENT_FAILED,
  SHIPMENT_CANCELLED
}
