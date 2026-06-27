package com.orderflow.shipment.messaging;

import com.orderflow.common.messaging.EventMessage;
import com.orderflow.common.messaging.Topics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/** Publishes shipment result events to {@code orderflow.events.shipment}. */
@Component
public class EventPublisher {

  private static final Logger log = LoggerFactory.getLogger(EventPublisher.class);

  private final KafkaTemplate<String, Object> kafkaTemplate;

  public EventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
    this.kafkaTemplate = kafkaTemplate;
  }

  public void publish(EventMessage event) {
    String key = event.correlationId().toString();
    kafkaTemplate.send(Topics.EVENTS_SHIPMENT, key, event);
    log.info("Published {} for order {}", event.type(), event.correlationId());
  }
}
