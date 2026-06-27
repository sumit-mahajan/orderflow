package com.orderflow.shipment.messaging;

import com.orderflow.common.messaging.CommandMessage;
import com.orderflow.common.messaging.EventMessage;
import com.orderflow.common.messaging.EventType;
import java.time.Instant;
import java.util.UUID;

/** Builds result events from the command they answer, preserving correlationId + sagaId. */
public final class Events {

  private Events() {}

  public static EventMessage shipmentInitiated(CommandMessage cmd, String carrierRef) {
    return of(cmd, EventType.SHIPMENT_INITIATED, null, carrierRef);
  }

  public static EventMessage shipmentFailed(CommandMessage cmd, String reason) {
    return of(cmd, EventType.SHIPMENT_FAILED, reason, null);
  }

  public static EventMessage shipmentCancelled(CommandMessage cmd) {
    return of(cmd, EventType.SHIPMENT_CANCELLED, null, null);
  }

  private static EventMessage of(
      CommandMessage cmd, EventType type, String reason, String carrierRef) {
    return new EventMessage(
        UUID.randomUUID(), cmd.correlationId(), cmd.sagaId(), type, reason, carrierRef, Instant.now());
  }
}
