package com.orderflow.inventory.messaging;

import com.orderflow.common.messaging.CommandMessage;
import com.orderflow.common.messaging.EventMessage;
import com.orderflow.common.messaging.EventType;
import java.time.Instant;
import java.util.UUID;

/** Builds result events from the command they answer, preserving correlationId + sagaId. */
public final class Events {

  private Events() {}

  public static EventMessage reserved(CommandMessage cmd) {
    return of(cmd, EventType.INVENTORY_RESERVED, null);
  }

  public static EventMessage reservationFailed(CommandMessage cmd, String reason) {
    return of(cmd, EventType.INVENTORY_RESERVATION_FAILED, reason);
  }

  public static EventMessage released(CommandMessage cmd) {
    return of(cmd, EventType.INVENTORY_RELEASED, null);
  }

  private static EventMessage of(CommandMessage cmd, EventType type, String reason) {
    return new EventMessage(
        UUID.randomUUID(), cmd.correlationId(), cmd.sagaId(), type, reason, null, Instant.now());
  }
}
