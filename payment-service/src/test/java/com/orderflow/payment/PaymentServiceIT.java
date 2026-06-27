package com.orderflow.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.orderflow.common.messaging.CommandMessage;
import com.orderflow.common.messaging.CommandType;
import com.orderflow.common.messaging.EventMessage;
import com.orderflow.common.messaging.EventType;
import com.orderflow.common.messaging.Simulate;
import com.orderflow.payment.domain.PaymentStatus;
import com.orderflow.payment.repository.PaymentRepository;
import com.orderflow.payment.service.PaymentService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Real Postgres + Kafka via Testcontainers. Proves capture/refund correctness AND idempotency
 * (F-08) against a real database.
 */
@SpringBootTest
@Testcontainers
class PaymentServiceIT {

  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  @Container
  static KafkaContainer kafka =
      new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
  }

  @Autowired private PaymentService service;
  @Autowired private PaymentRepository payments;

  private static CommandMessage captureCmd(UUID orderId, Simulate simulate) {
    return new CommandMessage(
        UUID.randomUUID(),
        orderId,
        UUID.randomUUID(),
        CommandType.CAPTURE_PAYMENT,
        null,
        new BigDecimal("75.00"),
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
        new BigDecimal("75.00"),
        null,
        Instant.now());
  }

  @Test
  void captureAndRefund_areIdempotent() {
    UUID orderId = UUID.randomUUID();

    EventMessage captured = service.capture(captureCmd(orderId, null));
    assertThat(captured.type()).isEqualTo(EventType.PAYMENT_CAPTURED);
    assertThat(payments.findByOrderId(orderId)).isPresent()
        .hasValueSatisfying(p -> assertThat(p.getStatus()).isEqualTo(PaymentStatus.CAPTURED));

    // redelivered capture must not create a duplicate — check order-scoped count
    long beforeReplay = payments.findAll().stream()
        .filter(p -> p.getOrderId().equals(orderId)).count();
    EventMessage capturedAgain = service.capture(captureCmd(orderId, null));
    assertThat(capturedAgain.type()).isEqualTo(EventType.PAYMENT_CAPTURED);
    assertThat(payments.findAll().stream().filter(p -> p.getOrderId().equals(orderId)).count())
        .isEqualTo(beforeReplay);

    EventMessage refunded = service.refund(refundCmd(orderId));
    assertThat(refunded.type()).isEqualTo(EventType.PAYMENT_REFUNDED);
    assertThat(payments.findByOrderId(orderId)).isPresent()
        .hasValueSatisfying(p -> assertThat(p.getStatus()).isEqualTo(PaymentStatus.REFUNDED));

    // redelivered refund must not double-process
    long beforeRefundReplay = payments.findAll().stream()
        .filter(p -> p.getOrderId().equals(orderId)).count();
    EventMessage refundedAgain = service.refund(refundCmd(orderId));
    assertThat(refundedAgain.type()).isEqualTo(EventType.PAYMENT_REFUNDED);
    assertThat(payments.findAll().stream().filter(p -> p.getOrderId().equals(orderId)).count())
        .isEqualTo(beforeRefundReplay);
  }

  @Test
  void capture_withPaymentFailFlag_savesFailedRecord() {
    UUID orderId = UUID.randomUUID();
    Simulate simulate = new Simulate(true, null, null);

    EventMessage result = service.capture(captureCmd(orderId, simulate));

    assertThat(result.type()).isEqualTo(EventType.PAYMENT_FAILED);
    assertThat(payments.findByOrderId(orderId)).isPresent()
        .hasValueSatisfying(p -> assertThat(p.getStatus()).isEqualTo(PaymentStatus.FAILED));
  }
}
