package com.orderflow.payment.messaging;

import com.orderflow.common.messaging.CommandMessage;
import com.orderflow.common.messaging.Topics;
import com.orderflow.payment.service.PaymentService;
import com.orderflow.common.messaging.EventMessage;
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

  public PaymentCommandListener(PaymentService paymentService, EventPublisher eventPublisher) {
    this.paymentService = paymentService;
    this.eventPublisher = eventPublisher;
  }

  @KafkaListener(topics = Topics.COMMANDS_PAYMENT, containerFactory = "commandListenerFactory")
  public void onCommand(CommandMessage cmd) {
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
}
