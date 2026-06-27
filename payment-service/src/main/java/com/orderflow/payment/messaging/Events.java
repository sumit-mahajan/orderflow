package com.orderflow.payment.messaging;

import com.orderflow.common.messaging.CommandMessage;
import com.orderflow.common.messaging.EventMessage;
import com.orderflow.common.messaging.EventType;
import java.time.Instant;
import java.util.UUID;

/** Builds result events from the command they answer, preserving correlationId + sagaId. */
public final class Events {

  private Events() {}

  public static EventMessage paymentCaptured(CommandMessage cmd) {
    return of(cmd, EventType.PAYMENT_CAPTURED, null);
  }

  public static EventMessage paymentFailed(CommandMessage cmd, String reason) {
    return of(cmd, EventType.PAYMENT_FAILED, reason);
  }

  public static EventMessage paymentRefunded(CommandMessage cmd) {
    return of(cmd, EventType.PAYMENT_REFUNDED, null);
  }

  private static EventMessage of(CommandMessage cmd, EventType type, String reason) {
    return new EventMessage(
        UUID.randomUUID(), cmd.correlationId(), cmd.sagaId(), type, reason, null, Instant.now());
  }
}
