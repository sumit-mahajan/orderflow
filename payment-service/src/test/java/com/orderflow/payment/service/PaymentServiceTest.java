package com.orderflow.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orderflow.common.messaging.CommandMessage;
import com.orderflow.common.messaging.CommandType;
import com.orderflow.common.messaging.EventMessage;
import com.orderflow.common.messaging.EventType;
import com.orderflow.common.messaging.Simulate;
import com.orderflow.payment.domain.Payment;
import com.orderflow.payment.domain.PaymentStatus;
import com.orderflow.payment.repository.PaymentRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for PaymentService (F-08). Uses Mockito so no DB/Kafka required.
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

  @Mock private PaymentRepository payments;
  @InjectMocks private PaymentService service;

  private static CommandMessage captureCmd(UUID orderId, Simulate simulate) {
    return new CommandMessage(
        UUID.randomUUID(),
        orderId,
        UUID.randomUUID(),
        CommandType.CAPTURE_PAYMENT,
        null,
        new BigDecimal("50.00"),
        simulate,
        Instant.now());
  }

  private static CommandMessage refundCmd(UUID orderId) {
    return new CommandMessage(
        UUID.randomUUID(),
        orderId,
        UUID.randomUUID(),
        CommandType.REFUND_PAYMENT,
        null,
        new BigDecimal("50.00"),
        null,
        Instant.now());
  }

  @Test
  void capture_noSimulate_returnsCaptured() {
    UUID orderId = UUID.randomUUID();
    when(payments.findByOrderId(orderId)).thenReturn(Optional.empty());

    EventMessage result = service.capture(captureCmd(orderId, null));

    assertThat(result.type()).isEqualTo(EventType.PAYMENT_CAPTURED);
    verify(payments).save(any(Payment.class));
  }

  @Test
  void capture_paymentFail_returnsFailedEvent() {
    UUID orderId = UUID.randomUUID();
    Simulate simulate = new Simulate(true, null, null);
    when(payments.findByOrderId(orderId)).thenReturn(Optional.empty());

    EventMessage result = service.capture(captureCmd(orderId, simulate));

    assertThat(result.type()).isEqualTo(EventType.PAYMENT_FAILED);
    assertThat(result.reason()).isEqualTo("simulated-failure");
    verify(payments).save(any(Payment.class));
  }

  @Test
  void capture_idempotent_existingCaptured_doesNotSaveAgain() {
    UUID orderId = UUID.randomUUID();
    Payment existing = new Payment(orderId, new BigDecimal("50.00"), PaymentStatus.CAPTURED, null);
    when(payments.findByOrderId(orderId)).thenReturn(Optional.of(existing));

    EventMessage result = service.capture(captureCmd(orderId, null));

    assertThat(result.type()).isEqualTo(EventType.PAYMENT_CAPTURED);
    verify(payments, never()).save(any(Payment.class));
  }

  @Test
  void capture_idempotent_existingFailed_doesNotSaveAgain() {
    UUID orderId = UUID.randomUUID();
    Payment existing =
        new Payment(orderId, new BigDecimal("50.00"), PaymentStatus.FAILED, "simulated-failure");
    when(payments.findByOrderId(orderId)).thenReturn(Optional.of(existing));

    EventMessage result = service.capture(captureCmd(orderId, null));

    assertThat(result.type()).isEqualTo(EventType.PAYMENT_FAILED);
    verify(payments, never()).save(any(Payment.class));
  }

  @Test
  void refund_capturedPayment_returnsRefunded() {
    UUID orderId = UUID.randomUUID();
    Payment captured = new Payment(orderId, new BigDecimal("50.00"), PaymentStatus.CAPTURED, null);
    when(payments.findByOrderId(orderId)).thenReturn(Optional.of(captured));

    EventMessage result = service.refund(refundCmd(orderId));

    assertThat(result.type()).isEqualTo(EventType.PAYMENT_REFUNDED);
    assertThat(captured.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
    verify(payments).save(captured);
  }

  @Test
  void refund_idempotent_alreadyRefunded_doesNotSaveAgain() {
    UUID orderId = UUID.randomUUID();
    Payment refunded =
        new Payment(orderId, new BigDecimal("50.00"), PaymentStatus.REFUNDED, null);
    when(payments.findByOrderId(orderId)).thenReturn(Optional.of(refunded));

    EventMessage result = service.refund(refundCmd(orderId));

    assertThat(result.type()).isEqualTo(EventType.PAYMENT_REFUNDED);
    verify(payments, never()).save(any(Payment.class));
  }
}
