package com.orderflow.order.messaging;

import com.orderflow.common.messaging.EventMessage;
import com.orderflow.common.messaging.Topics;
import com.orderflow.order.service.SagaLock;
import com.orderflow.order.service.SagaLockedException;
import com.orderflow.order.service.SagaOrchestrator;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Adapter: consumes payment result events (M2), acquires the per-order saga lock, delegates to the
 * orchestrator, and always releases the lock. No business logic here.
 */
@Component
public class PaymentEventListener {

  private static final Logger log = LoggerFactory.getLogger(PaymentEventListener.class);

  private final SagaOrchestrator orchestrator;
  private final SagaLock sagaLock;
  private final Tracer tracer;

  public PaymentEventListener(SagaOrchestrator orchestrator, SagaLock sagaLock, Tracer tracer) {
    this.orchestrator = orchestrator;
    this.sagaLock = sagaLock;
    this.tracer = tracer;
  }

  @KafkaListener(topics = Topics.EVENTS_PAYMENT, containerFactory = "eventListenerFactory")
  public void onPaymentEvent(EventMessage event) {
    UUID orderId = event.correlationId();
    tagCurrentSpan(orderId, event.sagaId());
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

  private void tagCurrentSpan(UUID orderId, UUID sagaId) {
    Span span = tracer.currentSpan();
    if (span == null) {
      return;
    }
    span.tag("order.id", orderId.toString());
    span.tag("saga.id", sagaId.toString());
  }
}
