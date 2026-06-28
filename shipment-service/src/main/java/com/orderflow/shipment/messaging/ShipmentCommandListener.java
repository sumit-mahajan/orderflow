package com.orderflow.shipment.messaging;

import com.orderflow.common.messaging.CommandMessage;
import com.orderflow.common.messaging.Topics;
import com.orderflow.shipment.service.ShipmentService;
import com.orderflow.common.messaging.EventMessage;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Adapter: consumes shipment commands, delegates to the service, publishes the result event. No
 * business logic here. A failed create (CB open or carrier rejection) is a normal business outcome
 * so the orchestrator can drive compensation (refund payment → release inventory).
 */
@Component
public class ShipmentCommandListener {

  private static final Logger log = LoggerFactory.getLogger(ShipmentCommandListener.class);

  private final ShipmentService shipmentService;
  private final EventPublisher eventPublisher;
  private final Tracer tracer;

  public ShipmentCommandListener(
      ShipmentService shipmentService, EventPublisher eventPublisher, Tracer tracer) {
    this.shipmentService = shipmentService;
    this.eventPublisher = eventPublisher;
    this.tracer = tracer;
  }

  @KafkaListener(topics = Topics.COMMANDS_SHIPMENT, containerFactory = "commandListenerFactory")
  public void onCommand(CommandMessage cmd) {
    tagCurrentSpan(cmd);
    log.info("Received {} for order {}", cmd.type(), cmd.correlationId());

    EventMessage event =
        switch (cmd.type()) {
          case CREATE_SHIPMENT -> shipmentService.create(cmd);
          case CANCEL_SHIPMENT -> shipmentService.cancel(cmd);
          default -> {
            log.warn("Ignoring non-shipment command {}", cmd.type());
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
}
