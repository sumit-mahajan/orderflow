package com.orderflow.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.orderflow.common.messaging.EventMessage;
import com.orderflow.common.messaging.EventType;
import com.orderflow.common.messaging.Topics;
import com.orderflow.order.api.dto.PlaceOrderRequest;
import com.orderflow.order.api.dto.SimulateDto;
import com.orderflow.order.domain.OrderStatus;
import com.orderflow.order.domain.SagaInstance;
import com.orderflow.order.repository.OrderRepository;
import com.orderflow.order.repository.OutboxRepository;
import com.orderflow.order.repository.SagaInstanceRepository;
import com.orderflow.order.service.PlaceOrderResult;
import com.orderflow.order.service.PlaceOrderService;
import com.orderflow.order.service.SagaOrchestrator;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * End-to-end saga inside order-service against real Postgres + Kafka + Redis. We drive the
 * participant's result events directly (no inventory or payment service running) to prove
 * orchestration: happy path (inventory + payment → OrderConfirmed), failAfterReserve (M1
 * compensation before payment), and payment failure (M2 compensation → release inventory).
 */
@SpringBootTest
@Testcontainers
class OrderSagaIT {

  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  @Container
  static KafkaContainer kafka =
      new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

  @Container
  static GenericContainer<?> redis =
      new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    registry.add("spring.data.redis.host", redis::getHost);
    registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
  }

  @Autowired private PlaceOrderService placeOrderService;
  @Autowired private SagaOrchestrator orchestrator;
  @Autowired private SagaInstanceRepository sagas;
  @Autowired private OrderRepository orders;
  @Autowired private OutboxRepository outbox;

  private static PlaceOrderRequest request(SimulateDto simulate) {
    return new PlaceOrderRequest(
        UUID.randomUUID(),
        List.of(new PlaceOrderRequest.Item("SKU-LAPTOP", 1)),
        new BigDecimal("10.00"),
        simulate);
  }

  private static EventMessage event(UUID orderId, UUID sagaId, EventType type) {
    return new EventMessage(UUID.randomUUID(), orderId, sagaId, type, null, null, Instant.now());
  }

  @Test
  void happyPath_fullThreeStepSaga_thenConfirmed() {
    PlaceOrderResult result = placeOrderService.place("key-" + UUID.randomUUID(), request(null));
    UUID orderId = result.orderId();

    SagaInstance saga = sagas.findByOrderId(orderId).orElseThrow();
    assertThat(saga.getCurrentState()).isEqualTo("OrderPlaced");
    assertThat(outbox.findAll()).anyMatch(o -> o.getTopic().equals(Topics.COMMANDS_INVENTORY));

    orchestrator.handle(event(orderId, saga.getSagaId(), EventType.INVENTORY_RESERVED));
    assertThat(sagas.findByOrderId(orderId).orElseThrow().getCurrentState())
        .isEqualTo("InventoryReserved");
    assertThat(outbox.findAll()).anyMatch(o -> o.getPayload().contains("CAPTURE_PAYMENT"));

    orchestrator.handle(event(orderId, saga.getSagaId(), EventType.PAYMENT_CAPTURED));
    assertThat(sagas.findByOrderId(orderId).orElseThrow().getCurrentState())
        .isEqualTo("PaymentCaptured");
    assertThat(outbox.findAll()).anyMatch(o -> o.getPayload().contains("CREATE_SHIPMENT"));

    orchestrator.handle(eventWithCarrier(orderId, saga.getSagaId(), EventType.SHIPMENT_INITIATED, "LABEL-TEST01"));
    assertThat(sagas.findByOrderId(orderId).orElseThrow().getCurrentState())
        .isEqualTo("OrderConfirmed");
    assertThat(orders.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.CONFIRMED);
  }

  @Test
  void compensation_failAfterReserve_releasesThenFails() {
    PlaceOrderResult result =
        placeOrderService.place(
            "key-" + UUID.randomUUID(), request(new SimulateDto(null, null, true)));
    UUID orderId = result.orderId();
    SagaInstance saga = sagas.findByOrderId(orderId).orElseThrow();

    orchestrator.handle(event(orderId, saga.getSagaId(), EventType.INVENTORY_RESERVED));

    assertThat(sagas.findByOrderId(orderId).orElseThrow().getCurrentState())
        .isEqualTo("Compensating");
    assertThat(outbox.findAll()).anyMatch(o -> o.getPayload().contains("RELEASE_INVENTORY"));

    orchestrator.handle(event(orderId, saga.getSagaId(), EventType.INVENTORY_RELEASED));

    assertThat(sagas.findByOrderId(orderId).orElseThrow().getCurrentState())
        .isEqualTo("OrderFailed");
    assertThat(orders.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.FAILED);
  }

  @Test
  void m2_paymentFail_compensates_inventoryReleased_thenFails() {
    PlaceOrderResult result =
        placeOrderService.place(
            "key-" + UUID.randomUUID(), request(new SimulateDto(true, null, null)));
    UUID orderId = result.orderId();
    SagaInstance saga = sagas.findByOrderId(orderId).orElseThrow();

    orchestrator.handle(event(orderId, saga.getSagaId(), EventType.INVENTORY_RESERVED));
    assertThat(sagas.findByOrderId(orderId).orElseThrow().getCurrentState())
        .isEqualTo("InventoryReserved");

    orchestrator.handle(event(orderId, saga.getSagaId(), EventType.PAYMENT_FAILED));
    assertThat(sagas.findByOrderId(orderId).orElseThrow().getCurrentState())
        .isEqualTo("Compensating");
    assertThat(outbox.findAll()).anyMatch(o -> o.getPayload().contains("RELEASE_INVENTORY"));

    orchestrator.handle(event(orderId, saga.getSagaId(), EventType.INVENTORY_RELEASED));
    assertThat(sagas.findByOrderId(orderId).orElseThrow().getCurrentState())
        .isEqualTo("OrderFailed");
    assertThat(orders.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.FAILED);
  }

  @Test
  void m3_shipmentFail_compensates_refundPayment_releaseInventory_thenFails() {
    // M3: inventory reserved → payment captured → shipment fails →
    //     refund payment → release inventory → OrderFailed (full 3-step reverse cascade, F-12)
    PlaceOrderResult result =
        placeOrderService.place(
            "key-" + UUID.randomUUID(), request(new SimulateDto(null, "fail", null)));
    UUID orderId = result.orderId();
    SagaInstance saga = sagas.findByOrderId(orderId).orElseThrow();

    orchestrator.handle(event(orderId, saga.getSagaId(), EventType.INVENTORY_RESERVED));
    assertThat(sagas.findByOrderId(orderId).orElseThrow().getCurrentState())
        .isEqualTo("InventoryReserved");

    orchestrator.handle(event(orderId, saga.getSagaId(), EventType.PAYMENT_CAPTURED));
    assertThat(sagas.findByOrderId(orderId).orElseThrow().getCurrentState())
        .isEqualTo("PaymentCaptured");
    assertThat(outbox.findAll()).anyMatch(o -> o.getPayload().contains("CREATE_SHIPMENT"));

    orchestrator.handle(event(orderId, saga.getSagaId(), EventType.SHIPMENT_FAILED));
    assertThat(sagas.findByOrderId(orderId).orElseThrow().getCurrentState())
        .isEqualTo("Compensating");
    assertThat(outbox.findAll()).anyMatch(o -> o.getPayload().contains("REFUND_PAYMENT"));

    orchestrator.handle(event(orderId, saga.getSagaId(), EventType.PAYMENT_REFUNDED));
    assertThat(sagas.findByOrderId(orderId).orElseThrow().getCurrentState())
        .isEqualTo("Compensating");
    assertThat(outbox.findAll()).anyMatch(o -> o.getPayload().contains("RELEASE_INVENTORY"));

    orchestrator.handle(event(orderId, saga.getSagaId(), EventType.INVENTORY_RELEASED));
    assertThat(sagas.findByOrderId(orderId).orElseThrow().getCurrentState())
        .isEqualTo("OrderFailed");
    assertThat(orders.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.FAILED);
  }

  private static EventMessage eventWithCarrier(UUID orderId, UUID sagaId, EventType type, String carrierRef) {
    return new EventMessage(UUID.randomUUID(), orderId, sagaId, type, null, carrierRef, Instant.now());
  }
}
