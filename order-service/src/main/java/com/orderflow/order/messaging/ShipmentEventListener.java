package com.orderflow.order.messaging;

import com.orderflow.common.messaging.EventMessage;
import com.orderflow.common.messaging.Topics;
import com.orderflow.order.service.SagaLock;
import com.orderflow.order.service.SagaLockedException;
import com.orderflow.order.service.SagaOrchestrator;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Adapter: consumes shipment result events (M3), acquires the per-order saga lock, delegates to
 * the orchestrator, and always releases the lock. No business logic here.
 */
@Component
public class ShipmentEventListener {

  private static final Logger log = LoggerFactory.getLogger(ShipmentEventListener.class);

  private final SagaOrchestrator orchestrator;
  private final SagaLock sagaLock;

  public ShipmentEventListener(SagaOrchestrator orchestrator, SagaLock sagaLock) {
    this.orchestrator = orchestrator;
    this.sagaLock = sagaLock;
  }

  @KafkaListener(topics = Topics.EVENTS_SHIPMENT, containerFactory = "eventListenerFactory")
  public void onShipmentEvent(EventMessage event) {
    UUID orderId = event.correlationId();
    String token = UUID.randomUUID().toString();

    if (!sagaLock.acquireWithRetry(orderId, token)) {
      throw new SagaLockedException(orderId);
    }
    try {
      log.info("Handling {} for order {}", event.type(), orderId);
      orchestrator.handle(event);
    } finally {
      sagaLock.unlock(orderId, token);
    }
  }
}
