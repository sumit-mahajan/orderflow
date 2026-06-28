package com.orderflow.payment.messaging;

import com.orderflow.common.messaging.CommandMessage;
import com.orderflow.common.messaging.Topics;
import com.orderflow.payment.service.PaymentService;
import com.orderflow.common.messaging.EventMessage;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Adapter: consumes payment commands, delegates to the service, publishes the result event. No
 * business logic here. A failed capture is a normal business outcome (not an exception) so the
 * orchestrator can drive compensation.
 */
@Component
public class PaymentCommandListener {

  private static final Logger log = LoggerFactory.getLogger(PaymentCommandListener.class);

  private final PaymentService paymentService;
  private final EventPublisher eventPublisher;
  private final Tracer tracer;

  public PaymentCommandListener(
      PaymentService paymentService, EventPublisher eventPublisher, Tracer tracer) {
    this.paymentService = paymentService;
    this.eventPublisher = eventPublisher;
    this.tracer = tracer;
  }

  @KafkaListener(topics = Topics.COMMANDS_PAYMENT, containerFactory = "commandListenerFactory")
  public void onCommand(CommandMessage cmd) {
    tagCurrentSpan(cmd);
    log.info("Received {} for order {}", cmd.type(), cmd.correlationId());

    EventMessage event =
        switch (cmd.type()) {
          case CAPTURE_PAYMENT -> paymentService.capture(cmd);
          case REFUND_PAYMENT -> paymentService.refund(cmd);
          default -> {
            log.warn("Ignoring non-payment command {}", cmd.type());
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
