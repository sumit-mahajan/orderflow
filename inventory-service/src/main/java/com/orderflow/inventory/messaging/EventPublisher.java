package com.orderflow.inventory.messaging;

import com.orderflow.common.messaging.EventMessage;
import com.orderflow.common.messaging.Topics;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/** Publishes inventory result events, keyed by orderId for per-order partition ordering. */
@Component
public class EventPublisher {

  private final KafkaTemplate<String, Object> kafkaTemplate;

  public EventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
    this.kafkaTemplate = kafkaTemplate;
  }

  public void publish(EventMessage event) {
    kafkaTemplate.send(Topics.EVENTS_INVENTORY, event.correlationId().toString(), event);
  }
}
