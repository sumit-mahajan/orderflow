package com.orderflow.shipment;

import static org.assertj.core.api.Assertions.assertThat;

import com.orderflow.common.messaging.CommandMessage;
import com.orderflow.common.messaging.CommandType;
import com.orderflow.common.messaging.EventMessage;
import com.orderflow.common.messaging.EventType;
import com.orderflow.common.messaging.Simulate;
import com.orderflow.shipment.domain.ShipmentStatus;
import com.orderflow.shipment.repository.ShipmentRepository;
import com.orderflow.shipment.service.ShipmentService;
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
 * Real Postgres + Kafka via Testcontainers. Proves create/cancel correctness and idempotency
 * (F-10), and verifies circuit breaker fallback for fail-mode injection (F-11).
 *
 * <p>The circuit breaker annotation is active in this full Spring context, so
 * {@code shipmentMode="fail"} exercises both the carrier exception path and the fallback.
 */
@SpringBootTest
@Testcontainers
class ShipmentServiceIT {

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

  @Autowired private ShipmentService service;
  @Autowired private ShipmentRepository shipments;

  private static CommandMessage createCmd(UUID orderId, Simulate simulate) {
    return new CommandMessage(
        UUID.randomUUID(),
        orderId,
        UUID.randomUUID(),
        CommandType.CREATE_SHIPMENT,
        null,
        null,
        simulate,
        Instant.now());
  }

  private static CommandMessage cancelCmd(UUID orderId) {
    return new CommandMessage(
        UUID.randomUUID(),
        orderId,
        UUID.randomUUID(),
        CommandType.CANCEL_SHIPMENT,
        null,
        null,
        null,
        Instant.now());
  }

  @Test
  void create_happyPath_returnsInitiatedAndPersists() {
    UUID orderId = UUID.randomUUID();
    EventMessage result = service.create(createCmd(orderId, null));

    assertThat(result.type()).isEqualTo(EventType.SHIPMENT_INITIATED);
    assertThat(result.carrierRef()).isNotNull().startsWith("LABEL-");
    assertThat(shipments.findByOrderId(orderId)).isPresent()
        .hasValueSatisfying(s -> assertThat(s.getStatus()).isEqualTo(ShipmentStatus.INITIATED));
  }

  @Test
  void create_idempotentReplay_doesNotDoubleCreate() {
    UUID orderId = UUID.randomUUID();

    EventMessage first = service.create(createCmd(orderId, null));
    assertThat(first.type()).isEqualTo(EventType.SHIPMENT_INITIATED);

    long countBefore = shipments.findAll().stream()
        .filter(s -> s.getOrderId().equals(orderId)).count();

    EventMessage second = service.create(createCmd(orderId, null));
    assertThat(second.type()).isEqualTo(EventType.SHIPMENT_INITIATED);

    assertThat(shipments.findAll().stream()
        .filter(s -> s.getOrderId().equals(orderId)).count()).isEqualTo(countBefore);
  }

  @Test
  void create_failMode_circuitBreakerFallback_returnsFailedEvent() {
    UUID orderId = UUID.randomUUID();
    Simulate simulate = new Simulate(null, "fail", null);

    // "fail" mode throws; the @CircuitBreaker annotation routes to the fallback
    EventMessage result = service.create(createCmd(orderId, simulate));

    assertThat(result.type()).isEqualTo(EventType.SHIPMENT_FAILED);
    assertThat(shipments.findByOrderId(orderId)).isPresent()
        .hasValueSatisfying(s -> assertThat(s.getStatus()).isEqualTo(ShipmentStatus.FAILED));
  }

  @Test
  void cancel_initiatedShipment_cancelsAndReturnsEvent() {
    UUID orderId = UUID.randomUUID();
    service.create(createCmd(orderId, null));

    EventMessage result = service.cancel(cancelCmd(orderId));

    assertThat(result.type()).isEqualTo(EventType.SHIPMENT_CANCELLED);
    assertThat(shipments.findByOrderId(orderId)).isPresent()
        .hasValueSatisfying(s -> assertThat(s.getStatus()).isEqualTo(ShipmentStatus.CANCELLED));
  }

  @Test
  void cancel_idempotentReplay_doesNotDoubleCancel() {
    UUID orderId = UUID.randomUUID();
    service.create(createCmd(orderId, null));
    service.cancel(cancelCmd(orderId));

    long countBefore = shipments.findAll().stream()
        .filter(s -> s.getOrderId().equals(orderId)).count();

    EventMessage second = service.cancel(cancelCmd(orderId));

    assertThat(second.type()).isEqualTo(EventType.SHIPMENT_CANCELLED);
    assertThat(shipments.findAll().stream()
        .filter(s -> s.getOrderId().equals(orderId)).count()).isEqualTo(countBefore);
  }
}
