package com.orderflow.inventory.messaging;

import com.orderflow.common.messaging.CommandMessage;
import com.orderflow.common.messaging.EventMessage;
import com.orderflow.inventory.service.InsufficientStockException;
import com.orderflow.inventory.service.InventoryService;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Adapter: consumes inventory commands, delegates to the service, publishes the result event. No
 * business logic here. A failed reservation is a normal business outcome (a FAILED event), not an
 * exception that bubbles up — so the orchestrator can drive compensation.
 */
@Component
public class InventoryCommandListener {

  private static final Logger log = LoggerFactory.getLogger(InventoryCommandListener.class);

  private final InventoryService inventoryService;
  private final EventPublisher eventPublisher;
  private final Tracer tracer;

  public InventoryCommandListener(
      InventoryService inventoryService, EventPublisher eventPublisher, Tracer tracer) {
    this.inventoryService = inventoryService;
    this.eventPublisher = eventPublisher;
    this.tracer = tracer;
  }

  @KafkaListener(
      topics = com.orderflow.common.messaging.Topics.COMMANDS_INVENTORY,
      containerFactory = "commandListenerFactory")
  public void onCommand(CommandMessage cmd) {
    tagCurrentSpan(cmd);
    log.info("Received {} for order {}", cmd.type(), cmd.correlationId());

    EventMessage event =
        switch (cmd.type()) {
          case RESERVE_INVENTORY -> reserveSafely(cmd);
          case RELEASE_INVENTORY -> inventoryService.release(cmd);
          default -> {
            log.warn("Ignoring non-inventory command {}", cmd.type());
            yield null;
          }
        };

    if (event != null) {
      eventPublisher.publish(event);
    }
  }

  private void tagCurrentSpan(CommandMessage cmd) {
    Span span = tracer.currentSpan();
    if (span == null) {
      return;
    }
    span.tag("order.id", cmd.correlationId().toString());
    span.tag("saga.id", cmd.sagaId().toString());
  }

  private EventMessage reserveSafely(CommandMessage cmd) {
    try {
      return inventoryService.reserve(cmd);
    } catch (InsufficientStockException e) {
      log.warn("Reservation failed for order {}: {}", cmd.correlationId(), e.getMessage());
      return Events.reservationFailed(cmd, e.getMessage());
    }
  }
}
