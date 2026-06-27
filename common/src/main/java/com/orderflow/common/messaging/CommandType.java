package com.orderflow.common.messaging;

/**
 * Commands the orchestrator (order-service) sends to participant services. M1 uses only the
 * inventory commands; the rest are declared for forward compatibility (M2/M3).
 */
public enum CommandType {
  RESERVE_INVENTORY,
  RELEASE_INVENTORY,
  CAPTURE_PAYMENT,
  REFUND_PAYMENT,
  CREATE_SHIPMENT,
  CANCEL_SHIPMENT
}
