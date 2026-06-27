package com.orderflow.shipment.messaging;

import com.orderflow.common.messaging.CommandMessage;
import com.orderflow.common.messaging.Topics;
import com.orderflow.shipment.service.ShipmentService;
import com.orderflow.common.messaging.EventMessage;
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

  public ShipmentCommandListener(ShipmentService shipmentService, EventPublisher eventPublisher) {
    this.shipmentService = shipmentService;
    this.eventPublisher = eventPublisher;
  }

  @KafkaListener(topics = Topics.COMMANDS_SHIPMENT, containerFactory = "commandListenerFactory")
  public void onCommand(CommandMessage cmd) {
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
}
