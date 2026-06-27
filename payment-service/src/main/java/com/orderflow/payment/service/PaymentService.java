package com.orderflow.payment.service;

import com.orderflow.common.messaging.CommandMessage;
import com.orderflow.common.messaging.EventMessage;
import com.orderflow.common.messaging.Simulate;
import com.orderflow.payment.domain.Payment;
import com.orderflow.payment.domain.PaymentStatus;
import com.orderflow.payment.messaging.Events;
import com.orderflow.payment.repository.PaymentRepository;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Capture / refund payment (F-08). Both operations are idempotent:
 *
 * <ul>
 *   <li><b>capture</b>: if a payment row already exists for the order, return its existing outcome
 *       without re-processing (redelivered command safety). Otherwise, check the simulate flag: if
 *       {@code paymentFail=true}, save FAILED and return a failure event; else save CAPTURED and
 *       return a captured event.
 *   <li><b>refund</b>: if already REFUNDED, return success (no-op). Otherwise flip status to
 *       REFUNDED and return a refunded event.
 * </ul>
 */
@Service
public class PaymentService {

  private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

  private final PaymentRepository payments;

  public PaymentService(PaymentRepository payments) {
    this.payments = payments;
  }

  @Transactional
  public EventMessage capture(CommandMessage cmd) {
    UUID orderId = cmd.correlationId();

    Optional<Payment> existing = payments.findByOrderId(orderId);
    if (existing.isPresent()) {
      log.info("Capture for order {} already processed — idempotent replay", orderId);
      return existingCaptureResult(cmd, existing.get());
    }

    boolean shouldFail = shouldSimulateFail(cmd.simulate());
    if (shouldFail) {
      Payment failed = new Payment(orderId, cmd.amount(), PaymentStatus.FAILED, "simulated-failure");
      payments.save(failed);
      log.info("Payment capture for order {} FAILED (injected)", orderId);
      return Events.paymentFailed(cmd, "simulated-failure");
    }

    Payment captured = new Payment(orderId, cmd.amount(), PaymentStatus.CAPTURED, null);
    payments.save(captured);
    log.info("Payment captured for order {} amount={}", orderId, cmd.amount());
    return Events.paymentCaptured(cmd);
  }

  @Transactional
  public EventMessage refund(CommandMessage cmd) {
    UUID orderId = cmd.correlationId();

    Optional<Payment> existing = payments.findByOrderId(orderId);
    if (existing.isPresent() && existing.get().getStatus() == PaymentStatus.REFUNDED) {
      log.info("Refund for order {} already processed — idempotent replay", orderId);
      return Events.paymentRefunded(cmd);
    }

    existing.ifPresent(
        p -> {
          p.markRefunded();
          payments.save(p);
        });

    log.info("Payment refunded for order {}", orderId);
    return Events.paymentRefunded(cmd);
  }

  private EventMessage existingCaptureResult(CommandMessage cmd, Payment payment) {
    return switch (payment.getStatus()) {
      case CAPTURED -> Events.paymentCaptured(cmd);
      case FAILED -> Events.paymentFailed(cmd, payment.getFailureReason());
      case REFUNDED -> Events.paymentCaptured(cmd); // already captured before refund
    };
  }

  private boolean shouldSimulateFail(Simulate simulate) {
    return simulate != null && Boolean.TRUE.equals(simulate.paymentFail());
  }
}
